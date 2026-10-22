"""
Substrait Expression, Operator, and Type Mapper.

This module translates Substrait Function Test AST expressions (e.g. from .test files)
into standard SQL dialect expressions suitable for direct execution and verification
in database engines such as DuckDB, PostgreSQL, and SQLite.
"""

import re
from typing import List, Dict, Any, Optional


class SubstraitExpressionMapper:
    """Translates Substrait function calls and type annotations into engine-executable SQL."""

    # Substrait type annotation mappings to SQL types
    TYPE_MAP: Dict[str, str] = {
        '::str': '::VARCHAR',
        '::string': '::VARCHAR',
        '::vchar': '::VARCHAR',
        '::i8': '::TINYINT',
        '::i16': '::SMALLINT',
        '::i32': '::INTEGER',
        '::i64': '::BIGINT',
        '::fp32': '::FLOAT',
        '::fp64': '::DOUBLE',
        '::bool': '::BOOLEAN',
        '::boolean': '::BOOLEAN',
        '::date': '::DATE',
        '::time': '::TIME',
        '::timestamp': '::TIMESTAMP',
        '::ts': '::TIMESTAMP',
        '::timestamp_tz': '::TIMESTAMPTZ',
        '::tstz': '::TIMESTAMPTZ',
    }

    # Keyword type arguments for cast / try_cast
    BARE_TYPE_MAP: Dict[str, str] = {
        'str': 'VARCHAR',
        'string': 'VARCHAR',
        'vchar': 'VARCHAR',
        'i8': 'TINYINT',
        'i16': 'SMALLINT',
        'i32': 'INTEGER',
        'i64': 'BIGINT',
        'fp32': 'FLOAT',
        'fp64': 'DOUBLE',
        'bool': 'BOOLEAN',
        'boolean': 'BOOLEAN',
        'date': 'DATE',
        'time': 'TIME',
        'timestamp': 'TIMESTAMP',
        'ts': 'TIMESTAMP',
    }

    # Binary operator translations
    BINARY_OPS: Dict[str, str] = {
        'equal': '=',
        'not_equal': '<>',
        'lt': '<',
        'lte': '<=',
        'gt': '>',
        'gte': '>=',
        'add': '+',
        'subtract': '-',
        'multiply': '*',
        'divide': '/',
        'modulus': '%',
        'mod': '%',
        'and': 'AND',
        'or': 'OR',
        'xor': 'XOR',
        'like': 'LIKE',
        'is_not_distinct_from': 'IS NOT DISTINCT FROM',
        'is_distinct_from': 'IS DISTINCT FROM',
        'bitwise_and': '&',
        'bitwise_or': '|',
        'shift_left': '<<',
        'shift_right': '>>',
    }

    # Unary predicates written as postfix SQL tests
    POSTFIX_TESTS: Dict[str, str] = {
        'is_true': 'IS TRUE',
        'is_false': 'IS FALSE',
        'is_not_true': 'IS NOT TRUE',
        'is_not_false': 'IS NOT FALSE',
    }

    # Builtin function renames
    FN_RENAMES: Dict[str, str] = {
        'char_length': 'length',
        'character_length': 'length',
        'is_nan': 'isnan',
        'is_infinite': 'isinf',
        'is_finite': 'isfinite',
        'array_concat': 'list_concat',
        'array_contains': 'list_contains',
        'array_length': 'len',
        'list_length': 'len',
        'octet_length': 'strlen',
        'bitwise_xor': 'xor',
    }

    ISO_DURATION = re.compile(
        r"^(-)?P(?:(-?\d+)Y)?(?:(-?\d+)M)?(?:(-?\d+)W)?(?:(-?\d+)D)?"
        r"(?:T(?:(-?\d+)H)?(?:(-?\d+)M)?(?:(-?\d+(?:\.\d+)?)S)?)?$")

    @classmethod
    def iso_duration_to_interval(cls, iso: str) -> Optional[str]:
        """'P1Y2M3DT4H5M6S' -> "INTERVAL '1 years 2 months 3 days 4 hours 5 minutes 6 seconds'"."""
        m = cls.ISO_DURATION.match(iso)
        if not m or iso in ('P', 'PT', '-P'):
            return None
        sign = -1 if m.group(1) else 1
        units = ['years', 'months', 'weeks', 'days', 'hours', 'minutes', 'seconds']
        parts = [f"{sign * float(v) if '.' in v else sign * int(v)} {u}"
                 for v, u in zip(m.groups()[1:], units) if v is not None]
        return f"INTERVAL '{' '.join(parts) or '0 seconds'}'"

    @classmethod
    def clean_types(cls, expr: str) -> str:
        """Convert Substrait type annotations into valid SQL types and literal syntax."""
        s = expr.strip()
        # dec<precision, scale> -> DECIMAL(precision, scale)
        s = re.sub(r'::dec(?:imal)?<(\d+),\s*(\d+)>', r'::DECIMAL(\1,\2)', s)
        # list<T> -> T[]
        s = re.sub(r'::list<(\w+)>', r'[]', s)
        # struct<...> -> struct
        s = re.sub(r'::struct<[^>]+>', '', s)
        # Floating point special literals (-inf before inf)
        s = re.sub(r'(?<!\w)-inf::', r"'-inf'::", s)
        s = re.sub(r'(?<!\w)\+inf::', r"'inf'::", s)
        s = re.sub(r'(?<!\w)inf::', r"'inf'::", s)
        s = re.sub(r'(?<!\w)nan::', r"'nan'::", s)
        # ISO-8601 duration literals: 'P5D'::iday -> INTERVAL '5 days'
        s = re.sub(r"'(-?P[0-9YMWDTHS.\-]*)'::(?:iday|iyear|interval\w*)(?:<[^>]*>)?",
                   lambda m: cls.iso_duration_to_interval(m.group(1)) or m.group(0), s)
        # Negative numeric literals: in SQL the cast binds tighter than unary minus, so
        # -128::TINYINT means -(128::TINYINT) and overflows. Parenthesize the literal.
        s = re.sub(r'(?<![\w.)\]])-(\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)::', r'(-\1)::', s)
        # Type annotations
        for sub_t, duck_t in cls.TYPE_MAP.items():
            s = re.sub(re.escape(sub_t) + r'(?!\w)', duck_t, s)
        return s

    @classmethod
    def parse_args(cls, args_str: str) -> List[str]:
        """Split a comma-separated argument list respecting nested parentheses, brackets, and quotes."""
        args: List[str] = []
        depth = 0
        in_quote = False
        quote_char = ''
        curr: List[str] = []

        for char in args_str:
            if in_quote:
                curr.append(char)
                if char == quote_char:
                    in_quote = False
            elif char in ("'", '"'):
                in_quote = True
                quote_char = char
                curr.append(char)
            elif char in ('(', '[', '{'):
                depth += 1
                curr.append(char)
            elif char in (')', ']', '}'):
                depth -= 1
                curr.append(char)
            elif char == ',' and depth == 0:
                args.append(''.join(curr).strip())
                curr = []
            else:
                curr.append(char)

        if curr:
            args.append(''.join(curr).strip())
        return [a for a in args if a]

    @classmethod
    def map_expression(cls, expr: str) -> str:
        """Recursively transpile a Substrait function expression into SQL."""
        cleaned = cls.clean_types(expr)

        # Match function call pattern: name(args...)
        fn_match = re.match(r'^([a-zA-Z_]\w*)\s*\((.*)\)$', cleaned, re.DOTALL)
        if not fn_match:
            return cleaned

        fn_name = fn_match.group(1).lower()
        args_str = fn_match.group(2)
        raw_args = cls.parse_args(args_str)
        mapped_args = [cls.map_expression(a) for a in raw_args]

        # 1. Binary Operators
        if fn_name in cls.BINARY_OPS and len(mapped_args) == 2:
            op = cls.BINARY_OPS[fn_name]
            return f"({mapped_args[0]} {op} {mapped_args[1]})"

        # 2. Ternary / Range Operators
        if fn_name == 'between' and len(mapped_args) == 3:
            return f"({mapped_args[0]} BETWEEN {mapped_args[1]} AND {mapped_args[2]})"

        if fn_name in ('case_when', 'if_then_else') and len(mapped_args) == 3:
            return f"(CASE WHEN {mapped_args[0]} THEN {mapped_args[1]} ELSE {mapped_args[2]} END)"

        # 3. Unary Operators
        if fn_name == 'negate' and len(mapped_args) == 1:
            return f"(-({mapped_args[0]}))"

        if fn_name == 'not' and len(mapped_args) == 1:
            return f"(NOT ({mapped_args[0]}))"

        if fn_name == 'is_null' and len(mapped_args) == 1:
            return f"({mapped_args[0]} IS NULL)"

        if fn_name == 'is_not_null' and len(mapped_args) == 1:
            return f"({mapped_args[0]} IS NOT NULL)"

        if fn_name in cls.POSTFIX_TESTS and len(mapped_args) == 1:
            return f"({mapped_args[0]} {cls.POSTFIX_TESTS[fn_name]})"

        if fn_name == 'bitwise_not' and len(mapped_args) == 1:
            return f"(~({mapped_args[0]}))"

        # 4. Cast Operations (try_cast('42', i32) -> TRY_CAST('42' AS INTEGER))
        if fn_name in ('try_cast', 'cast') and len(mapped_args) == 2:
            sql_fn = 'TRY_CAST' if fn_name == 'try_cast' else 'CAST'
            target_type = mapped_args[1].strip()
            target_type = cls.BARE_TYPE_MAP.get(target_type.lower(), target_type)
            return f"{sql_fn}({mapped_args[0]} AS {target_type})"

        # 5. Struct Construction (struct_construct('x', 1, 'y', 2) -> struct_pack("x" := 1, "y" := 2))
        if fn_name == 'struct_construct' and len(mapped_args) >= 2 and len(mapped_args) % 2 == 0:
            pack_pairs = []
            for i in range(0, len(mapped_args), 2):
                key = mapped_args[i].strip().strip("'\"")
                val = mapped_args[i + 1]
                pack_pairs.append(f'"{key}" := {val}')
            return f"struct_pack({', '.join(pack_pairs)})"

        # 6. Struct Extraction
        if fn_name == 'struct_extract' and len(mapped_args) == 2:
            struct_obj = mapped_args[0]
            field_name = mapped_args[1].strip().strip("'\"")
            return f"({struct_obj}).\"{field_name}\""

        # 7. Array / List Element Indexing
        if fn_name == 'array_element' and len(mapped_args) == 2:
            arr = mapped_args[0]
            idx = mapped_args[1]
            return f"({arr})[{idx}]"

        # 8. Array Construction
        if fn_name == 'array_construct':
            return f"[{', '.join(mapped_args)}]"

        # 9. General Function Renames
        target_fn = cls.FN_RENAMES.get(fn_name, fn_name)
        joined_args = ', '.join(mapped_args)
        return f"{target_fn}({joined_args})"

    @classmethod
    def clean_expected(cls, expr: str) -> str:
        """Prepare expected result expression for SQL comparison."""
        cleaned = cls.clean_types(expr)
        # Convert struct literals {a: 1, b: 2} -> {'a': 1, 'b': 2}
        cleaned = re.sub(r'\{([a-zA-Z_]\w*):', r"{'\1':", cleaned)
        cleaned = re.sub(r',\s*([a-zA-Z_]\w*):', r", '\1':", cleaned)
        return cleaned
