# Extended function test cases

Cases from the legacy suite (`test-suites/functions`) for Substrait standard functions,
converted to the current test-case format by `scripts/convert_legacy_function_tests.py`.
Do not edit by hand; fix the legacy case or the converter and re-run it.

Function definitions: `substrait-extensions` 0.107.0.

## Result

| | Cases |
|---|---:|
| Legacy cases read | 5041 |
| **Kept** (valid and verified) | **2278** |
| &nbsp;&nbsp;verified by DuckDB | 2151 |
| &nbsp;&nbsp;verified by the function definitions (exact computation) | 127 |
| Quarantined (valid, not verified — see below) | 40 |
| Dropped: not a Substrait standard function | 1032 |
| Dropped: already covered by an identical upstream case | 789 |
| Dropped: legacy window syntax (OVER ... WITH) has no automatic conversion | 233 |
| Dropped: no matching signature in the 0.107.0 YAMLs | 225 |
| Dropped: string uses \n/\t-style escapes, which the format does not define | 202 |
| Dropped: does not parse in the current format after conversion | 89 |
| Dropped: expected value contradicted by the function definitions | 89 |
| Dropped: extract component with no exact Substrait equivalent (EPOCH, WEEK, DOW, ...) | 24 |
| Dropped: type with no Substrait standard equivalent | 15 |
| Dropped: duplicate of another legacy case | 8 |
| Dropped: std_dev/variance case in an unexpected form | 6 |
| Dropped: contradicts the upstream case for the same call | 5 |
| Dropped: rpad truncation: functions_string.yaml removes characters from the left | 3 |
| Dropped: extract without a literal component | 2 |
| Dropped: temporal literal in an unsupported form | 1 |

A kept case's expected value was reproduced by DuckDB (through the compliance runner's
translator) or, where DuckDB differs or cannot run the case, by the result the function
definitions give (`scripts/function_reference.py`: exact arithmetic, IEEE 754 special values,
the option modes, string semantics). DuckDB's agreement is one of the two checks, so these
cases lean towards behaviour DuckDB shares; the leaderboard therefore does not score them.

## Examples of dropped cases

**not a Substrait standard function** (1032)

- `aggregate/count_distinct.test:5`: `count_distinct((1, 2, 3, 4, 5)::i32) = 5::i64`
- `aggregate/count_distinct.test:6`: `count_distinct((1, 1, 2, 2, 3)::i32) = 3::i64`
- `aggregate/count_distinct.test:7`: `count_distinct((100, 200, 100, 300, 200)::i64) = 3::i64`

**already covered by an identical upstream case** (789)

- `arithmetic/abs.test:5`: `abs(25::i8) = 25::i8`
- `arithmetic/abs.test:6`: `abs(-200::i16) = 200::i16`
- `arithmetic/abs.test:7`: `abs(30000::i32) = 30000::i32`

**legacy window syntax (OVER ... WITH) has no automatic conversion** (233)

- `window/dense_rank.test:5`: `dense_rank() OVER (ORDER BY value::i32) WITH ((1), (2), (3), (4), (5)) = (1, 2, 3, 4, 5)::i64`
- `window/dense_rank.test:6`: `dense_rank() OVER (ORDER BY value::i32 DESC) WITH ((5), (4), (3), (2), (1)) = (1, 2, 3, 4, 5)::i64`
- `window/dense_rank.test:9`: `dense_rank() OVER (ORDER BY value::i32) WITH ((1), (1), (2), (3), (3)) = (1, 1, 2, 3, 3)::i64`

**no matching signature in the 0.107.0 YAMLs** (225)

- `aggregate/avg.test:5`: `avg((1, 2, 3, 4, 5)::i32) = 3.0::fp64  — no matching signature for avg: argument 1: i32 does not match DECIMAL`
- `aggregate/avg.test:6`: `avg((10, 20, 30, 40)::i64) = 25.0::fp64  — no matching signature for avg: argument 1: i64 does not match DECIM`
- `aggregate/avg.test:7`: `avg((2.5, 5.0, 7.5, 10.0)::fp32) = 6.25::fp64  — no matching signature for avg: argument 1: fp32 does not matc`

**string uses \n/\t-style escapes, which the format does not define** (202)

- `aggregate/count.test:58`: `count(('tab\there'::str, 'new\nline'::str, 'quote\"test'::str)) = 3::i64`
- `aggregate/string_agg.test:52`: `string_agg(('hello', 'world')::str, '\n'::str) = 'hello\nworld'::str`
- `aggregate/string_agg.test:53`: `string_agg(('x', 'y', 'z')::str, '\t'::str) = 'x\ty\z'::str`

**does not parse in the current format after conversion** (89)

- `aggregate/avg.test:33`: `avg((1::i8, 2::i8, 3::i8))::i8 = 2.0::fp64`
- `aggregate/avg.test:34`: `avg((100::i16, 200::i16, 300::i16))::i16 = 200.0::fp64`
- `aggregate/avg.test:35`: `avg((1.5::fp32, 2.5::fp32, 3.5::fp32))::fp32 = 2.5::fp64`

**expected value contradicted by the function definitions** (89)

- `aggregate/stddev.test:51`: `((1.1), (2.2), (3.3), (4.4), (5.5), (6.6), (7.7), (8.8), (9.9), (10.1), (11.2), (12.3)) std_dev(POPULATION::en`
- `arithmetic/acosh.test:26`: `acosh(1.0000001::fp64) = 0.0014142135094480515::fp64`
- `arithmetic/acosh.test:27`: `acosh(1.0000001::fp32) = 0.0014142135094480515::fp32`

**extract component with no exact Substrait equivalent (EPOCH, WEEK, DOW, ...)** (24)

- `datetime/current_date.test:39`: `extract('DAY_OF_WEEK'::str, current_date()) >= 1::i64`
- `datetime/current_date.test:40`: `extract('DAY_OF_WEEK'::str, current_date()) <= 7::i64`
- `datetime/extract.test:9`: `extract('WEEK'::str, '2016-12-31T13:30:15'::ts) = 52::i64`

**type with no Substrait standard equivalent** (15)

- `comparison/is_not_null.test:13`: `is_not_null(255::u8) = true::bool`
- `comparison/is_not_null.test:14`: `is_not_null(65535::u16) = true::bool`
- `comparison/is_not_null.test:15`: `is_not_null(4294967295::u32) = true::bool`

**duplicate of another legacy case** (8)

- `arithmetic/degrees.test:43`: `degrees(0.7853981633974483::fp64) = 45::fp64`
- `arithmetic/degrees.test:45`: `degrees(0.017453292519943295::fp64) = 1::fp64`
- `comparison/gte.test:80`: `gte(0.0::fp64, 0.0::fp64) = true::bool`

**std_dev/variance case in an unexpected form** (6)

- `aggregate/stddev.test:28`: `stddev((1::i8, 2::i8, 3::i8, 4::i8))::i8 = 1.118033988749895::fp64`
- `aggregate/stddev.test:29`: `stddev((100::i16, 200::i16, 300::i16))::i16 = 81.64965809277261::fp64`
- `aggregate/stddev.test:30`: `stddev((1.5::fp32, 2.5::fp32, 3.5::fp32))::fp32 = 0.8164965809277261::fp64`

**contradicts the upstream case for the same call** (5)

- `arithmetic/shift_left.test:8`: `shift_left(301989888::i64, 8::i32) = 19791209299968::i64  vs upstream `shift_left(301989888::i64, 8::i32) = 77`
- `arithmetic/sqrt.test:7`: `sqrt(-1::i64) [on_domain_error:NAN] = null::fp64?  vs upstream `sqrt(-1::i64) [on_domain_error:NAN] = nan::fp6`
- `arithmetic/sqrt.test:8`: `sqrt(-9223372036854775800::i64) [on_domain_error:NAN] = null::fp64?  vs upstream `sqrt(-9223372036854775800::i`

**rpad truncation: functions_string.yaml removes characters from the left** (3)

- `string/rpad.test:10`: `rpad('abcdef'::str, 4::i32, ' '::str) = 'abcd'::str`
- `string/rpad.test:27`: `rpad('abc'::str, 1::i32, 'x'::str) = 'a'::str`
- `string/rpad.test:28`: `rpad('abc'::str, 2::i32, 'x'::str) = 'ab'::str`

**extract without a literal component** (2)

- `datetime/extract.test:35`: `extract(null::str, '2020-01-01'::date) = null::i64`
- `datetime/extract.test:37`: `extract(null::str, '13:45:30'::time) = null::i64`

**temporal literal in an unsupported form** (1)

- `datetime/subtract_datetime.test:23`: `subtract('2020-02-29'::ts, 'P1Y'::iyear) = '2019-02-28T00:00:00'::ts`

## Legacy values contradicted by the function definitions

Valid cases whose expected value is wrong: the definitions give a different result, shown
here. Dropped; to keep one, correct the legacy case.

| Legacy case | Converted | Correct result | DuckDB |
|---|---|---|---|
| `aggregate/stddev.test:51` | `((1.1), (2.2), (3.3), (4.4), (5.5), (6.6), (7.7), (8.8), (9.9), (10.1), (11.2), (12.3)) std_dev(POPULATION::enum, col0::fp64) = 3.6332495807108003::fp64` | `3.5133376818442414` | got 3.513337681844242, expected 3.6332495807108005 [SELECT stddev_pop(col0) FROM (VALUES (CAST('1.1' AS DOUBLE)), (CAST( |
| `arithmetic/acosh.test:26` | `acosh(1.0000001::fp64) = 0.0014142135094480515::fp64` | `0.0004472135919037347` | got 0.0004472135919037347, expected 0.0014142135094480516 [SELECT acosh(CAST('1.0000001' AS DOUBLE))] |
| `arithmetic/acosh.test:27` | `acosh(1.0000001::fp32) = 0.0014142135094480515::fp32` | `0.00048828125` | got 0.0004882812451493617, expected 0.0014142135623842478 [SELECT acosh(CAST('1.0000001' AS FLOAT))] |
| `arithmetic/acosh.test:35` | `acosh(1.7976931348623157e308::fp64) = 709.782712893384::fp64` | `710.475860073944` | got 710.4758600739439, expected 709.782712893384 [SELECT acosh(CAST('1.7976931348623157e308' AS DOUBLE))] |
| `arithmetic/asinh.test:37` | `asinh(1e20::fp32) = 46.75172974982901::fp32` | `46.744850158691406` | got 46.74484906048174, expected 46.75172805786133 [SELECT asinh(CAST('1e20' AS FLOAT))] |
| `arithmetic/atan.test:14` | `atan(inf::fp64) = 1.5707963633974483::fp64` | `1.5707963267948966` | got 1.5707963267948966, expected 1.5707963633974482 [SELECT atan(CAST('inf' AS DOUBLE))] |
| `arithmetic/atan.test:15` | `atan(-inf::fp64) = -1.5707963633974483::fp64` | `-1.5707963267948966` | got -1.5707963267948966, expected -1.5707963633974482 [SELECT atan(CAST('-inf' AS DOUBLE))] |
| `arithmetic/atan.test:34` | `atan(-1.0e308::fp64) = -1.5707963633974483::fp64` | `-1.5707963267948966` | got -1.5707963267948966, expected -1.5707963633974482 [SELECT atan(CAST('-1.0e308' AS DOUBLE))] |
| `arithmetic/atan.test:40` | `atan(1.0e308::fp64) = 1.5707963633974483::fp64` | `1.5707963267948966` | got 1.5707963267948966, expected 1.5707963633974482 [SELECT atan(CAST('1.0e308' AS DOUBLE))] |
| `arithmetic/atan2.test:43` | `atan2(5.0::fp64, -3.0::fp64) = 2.111215699912376::fp64` | `2.1112158270654806` | got 2.1112158270654806, expected 2.111215699912376 [SELECT atan2(CAST('5.0' AS DOUBLE), CAST('-3.0' AS DOUBLE))] |
| `arithmetic/atan2.test:44` | `atan2(-5.0::fp64, -3.0::fp64) = -2.111215699912376::fp64` | `-2.1112158270654806` | got -2.1112158270654806, expected -2.111215699912376 [SELECT atan2(CAST('-5.0' AS DOUBLE), CAST('-3.0' AS DOUBLE))] |
| `arithmetic/atanh.test:30` | `atanh(0.9999::fp64) = 4.951760157141521::fp64` | `4.951718775643099` | got 4.951718775643098, expected 4.951760157141521 [SELECT atanh(CAST('0.9999' AS DOUBLE))] |
| `arithmetic/atanh.test:31` | `atanh(-0.9999::fp64) = -4.951760157141521::fp64` | `-4.951718775643099` | got -4.951718775643098, expected -4.951760157141521 [SELECT atanh(CAST('-0.9999' AS DOUBLE))] |
| `arithmetic/atanh.test:32` | `atanh(0.999999::fp64) = 7.600902709541989::fp64` | `7.254328619247669` | got 7.254328619247669, expected 7.600902709541989 [SELECT atanh(CAST('0.999999' AS DOUBLE))] |
| `arithmetic/atanh.test:33` | `atanh(-0.999999::fp64) = -7.600902709541989::fp64` | `-7.254328619247669` | got -7.254328619247669, expected -7.600902709541989 [SELECT atanh(CAST('-0.999999' AS DOUBLE))] |
| `arithmetic/bitwise_xor.test:57` | `bitwise_xor(-50::i8, -25::i8) = 43::i8` | `41` | got 41, expected 43 [SELECT xor(CAST(-50 AS TINYINT), CAST(-25 AS TINYINT))] |
| `arithmetic/bitwise_xor.test:58` | `bitwise_xor(-1000::i16, -2000::i16) = 3096::i16` | `1064` | got 1064, expected 3096 [SELECT xor(CAST(-1000 AS SMALLINT), CAST(-2000 AS SMALLINT))] |
| `arithmetic/bitwise_xor.test:61` | `bitwise_xor(-1000000::i32, -500000::i32) = 1499496::i32` | `582432` | got 582432, expected 1499496 [SELECT xor(CAST(-1000000 AS INTEGER), CAST(-500000 AS INTEGER))] |
| `arithmetic/bitwise_xor.test:62` | `bitwise_xor(-1000000000000::i64, -500000000000::i64) = 1499995213824::i64` | `673218791424` | got 673218791424, expected 1499995213824 [SELECT xor(CAST(-1000000000000 AS BIGINT), CAST(-500000000000 AS BIGINT))] |
| `arithmetic/cos.test:18` | `cos(3.4028235e38::fp32) = nan::fp32` | `0.8530210256576538` | got 0.8530210398303042, expected nan [SELECT cos(CAST('3.4028235e38' AS FLOAT))] |
| `arithmetic/cos.test:19` | `cos(-3.4028235e38::fp32) = nan::fp32` | `0.8530210256576538` | got 0.8530210398303042, expected nan [SELECT cos(CAST('-3.4028235e38' AS FLOAT))] |
| `arithmetic/cos.test:20` | `cos(1.7976931348623157e308::fp64) = nan::fp64` | `-0.9999876894265599` | got -0.9999876894265599, expected nan [SELECT cos(CAST('1.7976931348623157e308' AS DOUBLE))] |
| `arithmetic/cos.test:21` | `cos(-1.7976931348623157e308::fp64) = nan::fp64` | `-0.9999876894265599` | got -0.9999876894265599, expected nan [SELECT cos(CAST('-1.7976931348623157e308' AS DOUBLE))] |
| `arithmetic/cos.test:25` | `cos(1.5707963267948966::fp64) = 0.0::fp64` | `6.123233995736766e-17` | got 6.123233995736766e-17, expected 0.0 [SELECT cos(CAST('1.5707963267948966' AS DOUBLE))] |
| `arithmetic/cos.test:28` | `cos(4.71238898038469::fp64) = 0.0::fp64` | `-1.8369701987210297e-16` | got -1.8369701987210297e-16, expected 0.0 [SELECT cos(CAST('4.71238898038469' AS DOUBLE))] |
| `arithmetic/cos.test:30` | `cos(1.57079633::fp32) = 0.0::fp32` | `-4.371138828673793e-08` | got -4.371139000186241e-08, expected 0.0 [SELECT cos(CAST('1.57079633' AS FLOAT))] |
| `arithmetic/cos.test:35` | `cos(1000000.0::fp64) = 0.8623188722876839::fp64` | `0.9367521275331447` | got 0.9367521275331447, expected 0.8623188722876839 [SELECT cos(CAST('1000000.0' AS DOUBLE))] |
| `arithmetic/cos.test:36` | `cos(-1000000.0::fp64) = 0.8623188722876839::fp64` | `0.9367521275331447` | got 0.9367521275331447, expected 0.8623188722876839 [SELECT cos(CAST('-1000000.0' AS DOUBLE))] |
| `arithmetic/cos.test:37` | `cos(1000000.0::fp32) = -0.34999273::fp32` | `0.9367521405220032` | got 0.9367521275331447, expected -0.3499927222728729 [SELECT cos(CAST('1000000.0' AS FLOAT))] |
| `arithmetic/cos.test:38` | `cos(123456.789::fp64) = -0.7844488963293296::fp64` | `0.05167253271870138` | got 0.05167253271870138, expected -0.7844488963293296 [SELECT cos(CAST('123456.789' AS DOUBLE))] |
| `arithmetic/cosh.test:21` | `cosh(89.0::fp32) = inf::fp32` | `2.2448063889081578e+38` | got 2.2448064095871728e+38, expected inf [SELECT cosh(CAST('89.0' AS FLOAT))] |
| `arithmetic/cosh.test:22` | `cosh(-89.0::fp32) = inf::fp32` | `2.2448063889081578e+38` | got 2.2448064095871728e+38, expected inf [SELECT cosh(CAST('-89.0' AS FLOAT))] |
| `arithmetic/cosh.test:23` | `cosh(710.0::fp64) = inf::fp64` | `1.1169973830808555e+308` | got 1.1169973830808557e+308, expected inf [SELECT cosh(CAST('710.0' AS DOUBLE))] |
| `arithmetic/cosh.test:24` | `cosh(-710.0::fp64) = inf::fp64` | `1.1169973830808555e+308` | got 1.1169973830808557e+308, expected inf [SELECT cosh(CAST('-710.0' AS DOUBLE))] |
| `arithmetic/degrees.test:52` | `degrees(2.2250738585072014e-308::fp64) = 1.2746826375965833e-306::fp64` | `1.2748734119735194e-306` | got 1.2748734119735194e-306, expected 1.2746826375965832e-306 [SELECT degrees(CAST('2.2250738585072014e-308' AS DOUBLE)) |
| `arithmetic/degrees.test:53` | `degrees(1.1754944e-38::fp32) = 6.7359477e-37::fp32` | `6.735086592976112e-37` | got 6.7350865143587625e-37, expected 6.735947550752593e-37 [SELECT degrees(CAST('1.1754944e-38' AS FLOAT))] |
| `arithmetic/degrees.test:54` | `degrees(5e-324::fp64) = 2.864788975654116e-322::fp64` | `2.8e-322` | got 2.8e-322, expected 2.87e-322 [SELECT degrees(CAST('5e-324' AS DOUBLE))] |
| `arithmetic/degrees.test:68` | `degrees(3.4028235e+38::fp32) = 1.9498558e+40::fp32` | `inf` | got 1.94967423051954e+40, expected inf [SELECT degrees(CAST('3.4028235e+38' AS FLOAT))] |
| `arithmetic/exp.test:23` | `exp(-745.0::fp64) = 0.0::fp64` | `5e-324` | got 5e-324, expected 0.0 [SELECT exp(CAST('-745.0' AS DOUBLE))] |
| `arithmetic/exp.test:24` | `exp(-103.97::fp32) = 0.0::fp32` | `1.401298464324817e-45` | got 7.021051948485981e-46, expected 0.0 [SELECT exp(CAST('-103.97' AS FLOAT))] |
| `arithmetic/exp.test:34` | `exp(709.78::fp64) = 1.7976931348622732e+308::fp64` | `1.7928227943945155e+308` | got 1.7928227943945155e+308, expected 1.7976931348622732e+308 [SELECT exp(CAST('709.78' AS DOUBLE))] |
| `arithmetic/exp.test:35` | `exp(88.72::fp32) = 3.4028235e+38::fp32` | `3.3931806003874245e+38` | got 3.393180516226706e+38, expected 3.4028234663852886e+38 [SELECT exp(CAST('88.72' AS FLOAT))] |
| `arithmetic/ln.test:44` | `ln(1e-308::fp64) = -709.0895657128241::fp64` | `-709.1962086421661` | got -709.1962086421661, expected -709.0895657128241 [SELECT ln(CAST('1e-308' AS DOUBLE))] |
| `arithmetic/ln.test:51` | `ln(1e308::fp64) = 709.0895657128241::fp64` | `709.1962086421661` | got 709.1962086421661, expected 709.0895657128241 [SELECT ln(CAST('1e308' AS DOUBLE))] |
| `arithmetic/log10.test:52` | `log10(2.2250738585072014e-308::fp64) = -307.65249970533984::fp64` | `-307.6526555685888` | got -307.6526555685888, expected -307.65249970533984 [SELECT log10(CAST('2.2250738585072014e-308' AS DOUBLE))] |
| `arithmetic/mod.test:78` | `modulus(1000000::i32, 7::i32) = 6::i32` | `1` | got 1, expected 6 [SELECT (CAST(1000000 AS INTEGER) % CAST(7 AS INTEGER))] |
| `arithmetic/mod.test:79` | `modulus(1000000000::i64, 13::i64) = 4::i64` | `12` | got 12, expected 4 [SELECT (CAST(1000000000 AS BIGINT) % CAST(13 AS BIGINT))] |
| `arithmetic/power.test:50` | `power(2.0::fp64, -1074.0::fp64) = 0.0::fp64` | `5e-324` | got 5e-324, expected 0.0 [SELECT pow(CAST('2.0' AS DOUBLE), CAST('-1074.0' AS DOUBLE))] |
| `arithmetic/power.test:51` | `power(10.0::fp32, -45.0::fp32) = 0.0::fp32` | `1.401298464324817e-45` | got 1e-45, expected 0.0 [SELECT pow(CAST('10.0' AS FLOAT), CAST('-45.0' AS FLOAT))] |
| `arithmetic/power.test:52` | `power(0.1::fp64, 308.0::fp64) = 0.0::fp64` | `1.000000000000017e-308` | got 1.000000000000017e-308, expected 0.0 [SELECT pow(CAST('0.1' AS DOUBLE), CAST('308.0' AS DOUBLE))] |
| `arithmetic/radians.test:35` | `radians(90.987654321::fp64) = 1.5880193625544395::fp64` | `1.5880341465678955` | got 1.5880341465678955, expected 1.5880193625544394 [SELECT radians(CAST('90.987654321' AS DOUBLE))] |
| `arithmetic/radians.test:38` | `radians(1e38::fp32) = inf::fp32` | `1.7453292347074313e+36` | got 1.745329196193656e+36, expected inf [SELECT radians(CAST('1e38' AS FLOAT))] |
| `arithmetic/radians.test:39` | `radians(-1e38::fp32) = -inf::fp32` | `-1.7453292347074313e+36` | got -1.745329196193656e+36, expected -inf [SELECT radians(CAST('-1e38' AS FLOAT))] |
| `arithmetic/radians.test:51` | `radians(45.123456789::fp64) = 0.7875226218541935::fp64` | `0.7875528908494382` | got 0.7875528908494382, expected 0.7875226218541935 [SELECT radians(CAST('45.123456789' AS DOUBLE))] |
| `arithmetic/radians.test:52` | `radians(1.23456789::fp64) = 0.021546959764818256::fp64` | `0.021547274519899174` | got 0.021547274519899174, expected 0.021546959764818255 [SELECT radians(CAST('1.23456789' AS DOUBLE))] |
| `arithmetic/radians.test:69` | `radians(359.9::fp64) = 6.281451468902134::fp64` | `6.281439977927592` | got 6.281439977927592, expected 6.281451468902134 [SELECT radians(CAST('359.9' AS DOUBLE))] |
| `arithmetic/sin.test:27` | `sin(3.141592653589793::fp64) = 0.0::fp64` | `1.2246467991473532e-16` | got 1.2246467991473532e-16, expected 0.0 [SELECT sin(CAST('3.141592653589793' AS DOUBLE))] |
| `arithmetic/sin.test:29` | `sin(6.283185307179586::fp64) = 0.0::fp64` | `-2.4492935982947064e-16` | got -2.4492935982947064e-16, expected 0.0 [SELECT sin(CAST('6.283185307179586' AS DOUBLE))] |
| `arithmetic/sin.test:31` | `sin(3.1415927::fp32) = 0.0::fp32` | `-8.742277657347586e-08` | got -8.742278000372475e-08, expected 0.0 [SELECT sin(CAST('3.1415927' AS FLOAT))] |
| `arithmetic/sin.test:34` | `sin(1000.0::fp64) = -0.8268795405320025::fp64` | `0.8268795405320025` | got 0.8268795405320025, expected -0.8268795405320025 [SELECT sin(CAST('1000.0' AS DOUBLE))] |
| `arithmetic/sin.test:35` | `sin(-1000.0::fp64) = 0.8268795405320025::fp64` | `-0.8268795405320025` | got -0.8268795405320025, expected 0.8268795405320025 [SELECT sin(CAST('-1000.0' AS DOUBLE))] |
| `arithmetic/sin.test:36` | `sin(1e10::fp64) = -0.3048106211022167::fp64` | `-0.4875060250875107` | got -0.4875060250875107, expected -0.3048106211022167 [SELECT sin(CAST('1e10' AS DOUBLE))] |
| `arithmetic/sin.test:37` | `sin(1e15::fp64) = -0.9301436974926853::fp64` | `0.8582727931702359` | got 0.8582727931702359, expected -0.9301436974926853 [SELECT sin(CAST('1e15' AS DOUBLE))] |
| `arithmetic/sinh.test:12` | `sinh(89.0::fp32) = 2.3012729e+38::fp32` | `2.2448063889081578e+38` | got 2.2448064095871728e+38, expected 2.30127282006882e+38 [SELECT sinh(CAST('89.0' AS FLOAT))] |
| `arithmetic/sinh.test:13` | `sinh(-89.0::fp32) = -2.3012729e+38::fp32` | `-2.2448063889081578e+38` | got -2.2448064095871728e+38, expected -2.30127282006882e+38 [SELECT sinh(CAST('-89.0' AS FLOAT))] |
| `arithmetic/sum.test:58` | `sum((3.14159)::fp32) = 3.14159::fp64` | `3.141590118408203` | got 3.141590118408203, expected 3.14159 [SELECT sum(x) FROM (SELECT UNNEST(CAST(CAST([CAST('3.14159' AS FLOAT)] AS FLOAT |
| `comparison/between.test:40` | `between(-inf::fp32, -100.0::fp32, 100.0::fp32) = true::bool` | `False` | got False, expected True [SELECT (CAST('-inf' AS FLOAT) BETWEEN CAST('-100.0' AS FLOAT) AND CAST('100.0' AS FLOAT))] |
| `comparison/between.test:45` | `between(-inf::fp64, -100.0::fp64, 100.0::fp64) = true::bool` | `False` | got False, expected True [SELECT (CAST('-inf' AS DOUBLE) BETWEEN CAST('-100.0' AS DOUBLE) AND CAST('100.0' AS DOUBLE))] |
| `string/bit_length.test:24` | `bit_length('abc😄def'::str) = 88::i64` | `80` | got 80, expected 88 [SELECT bit_length('abc😄def')] |
| `string/bit_length.test:33` | `bit_length('é'::str) = 24::i64` | `16` | got 16, expected 24 [SELECT bit_length('é')] |
| `string/like.test:30` | `like('abc%def'::str, 'abc%def'::str) = false::bool` | `True` | got True, expected False [SELECT ('abc%def' LIKE 'abc%def')] |
| `string/like.test:31` | `like('abc_def'::str, 'abc_def'::str) = false::bool` | `True` | got True, expected False [SELECT ('abc_def' LIKE 'abc_def')] |
| `string/lpad.test:36` | `lpad('test'::str, 5000::i32, 'pad'::str) = '<5000 character string ending with test>'::str` | `'padpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpad...' (5000 characters)` | got 'padpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadpadp |
| `string/octet_length.test:45` | `octet_length('test🎉string'::str) = 15::i64` | `14` | got 14, expected 15 [SELECT strlen('test🎉string')] |
| `string/octet_length.test:47` | `octet_length('ASCII+日本語+한글'::str) = 23::i64` | `22` | got 22, expected 23 [SELECT strlen('ASCII+日本語+한글')] |
| `string/regexp_replace.test:54` | `regexp_replace('abcabcabc'::str, '[bc]'::str, 'dd'::str, 1::i64, 0::i64) = 'addcabcabc'::str` | `'addddaddddadddd'` | got 'addddaddddadddd', expected 'addcabcabc' [SELECT regexp_replace('abcabcabc', '[bc]', 'dd', 'g')] |
| `string/regexp_replace.test:84` | `regexp_replace('abc123def456ghi789'::str, '[0-9]+'::str, 'X'::str, 2::i64, 0::i64) = 'abc123defX'::str` | `'abcXdefXghiX'` | untranslatable: regexp_replace with position != 1 |
| `string/regexp_replace.test:85` | `regexp_replace('abc123def456ghi789'::str, '[0-9]+'::str, 'X'::str, 3::i64, 0::i64) = 'abc123def456ghiX'::str` | `'abcXdefXghiX'` | untranslatable: regexp_replace with position != 1 |
| `string/regexp_replace.test:90` | `regexp_replace('test'::str, 't'::str, ''::str, 1::i64, 0::i64) = 'est'::str` | `'es'` | got 'es', expected 'est' [SELECT regexp_replace('test', 't', '', 'g')] |
| `string/regexp_replace.test:104` | `regexp_replace('hello'::str, 'l'::str, ''::str) = 'helo'::str` | `'heo'` | got 'heo', expected 'helo' [SELECT regexp_replace('hello', 'l', '', 'g')] |
| `string/regexp_replace.test:107` | `regexp_replace('abcabc'::str, 'abc'::str, 'X'::str, 1::i64, 4::i64) = 'abcX'::str` | `'abcabc'` | untranslatable: regexp_replace with occurrence > 1 |
| `string/regexp_replace.test:108` | `regexp_replace('testtest'::str, 'test'::str, 'X'::str, 1::i64, 5::i64) = 'testX'::str` | `'testtest'` | untranslatable: regexp_replace with occurrence > 1 |
| `string/regexp_string_split.test:69` | `regexp_string_split('AbCdEf'::str, 'b'::str) = ['AbCdEf']::list<str>` | `['A', 'CdEf']` | got ['A', 'CdEf'], expected ['AbCdEf'] [SELECT regexp_split_to_array('AbCdEf', 'b')] |
| `string/regexp_string_split.test:70` | `regexp_string_split('AbCdEf'::str, 'B'::str) = ['A', 'CdEf']::list<str>` | `['AbCdEf']` | got ['AbCdEf'], expected ['A', 'CdEf'] [SELECT regexp_split_to_array('AbCdEf', 'B')] |
| `string/repeat.test:16` | `repeat('a'::str, 2147483647::i64) = 'aaa...'::str` | `'a' repeated 2147483647 times` | untranslatable: repeat result of 2147483647 characters is too large to materialise |
| `string/repeat.test:35` | `repeat('a'::str, 10000::i64) = 'aaaaaaaaaa...'::str` | `'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa...' (10000 characters)` | got 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa |
| `string/repeat.test:36` | `repeat('xy'::str, 5000::i64) = 'xyxyxyxy...'::str` | `'xyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyx...' (10000 characters)` | got 'xyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyxyx |
| `string/replace.test:42` | `replace('test'::str, 't'::str, 'TEST'::str) = 'TESTesTestEST'::str` | `'TESTesTEST'` | got 'TESTesTEST', expected 'TESTesTestEST' [SELECT replace('test', 't', 'TEST')] |
| `string/right.test:38` | `right('mixed😄text'::str, 4::i32) = '😄text'::str` | `'text'` | got 'text', expected '😄text' [SELECT right('mixed😄text', CAST(4 AS INTEGER))] |

## Quarantined cases

Valid against the function definitions, but DuckDB does not reproduce the expected value and
the definitions do not decide it (for example shift_left overflow, modulus(INT_MIN, -1),
min/max with NaN, interval normalisation, a start or position below 1 without an option).
To keep one, extend `scripts/function_reference.py` where a definition settles it.

| Legacy case | Converted | Why not verified |
|---|---|---|
| `arithmetic/min.test:36` | `min((NaN, 1.0, 2.0)::fp32) = nan::fp32` | got 1.0, expected nan [SELECT min(x) FROM (SELECT UNNEST(CAST(CAST([CAST('NaN' AS FLOAT), CAST('1.0' AS FLOAT), CAST('2.0' AS FLOAT)] AS FLOAT[]) AS FLOAT[])) AS x)] |
| `arithmetic/min.test:37` | `min((1.0, NaN, 2.0)::fp64) = nan::fp64` | got 1.0, expected nan [SELECT min(x) FROM (SELECT UNNEST(CAST(CAST([CAST('1.0' AS DOUBLE), CAST('NaN' AS DOUBLE), CAST('2.0' AS DOUBLE)] AS DOUBLE[]) AS DOUBLE[])) AS x)] |
| `arithmetic/min.test:39` | `min((NaN, -inf, 5.0)::fp64) = nan::fp64` | got -inf, expected nan [SELECT min(x) FROM (SELECT UNNEST(CAST(CAST([CAST('NaN' AS DOUBLE), CAST('-inf' AS DOUBLE), CAST('5.0' AS DOUBLE)] AS DOUBLE[]) AS DOUBLE[])) AS x)] |
| `arithmetic/min.test:40` | `min((inf, NaN, -inf)::fp32) = nan::fp32` | got -inf, expected nan [SELECT min(x) FROM (SELECT UNNEST(CAST(CAST([CAST('inf' AS FLOAT), CAST('NaN' AS FLOAT), CAST('-inf' AS FLOAT)] AS FLOAT[]) AS FLOAT[])) AS x)] |
| `arithmetic/mod.test:40` | `modulus(-2147483648::i32, -1::i32) = 0::i32` | OutOfRangeException: Out of Range Error: Overflow in division of -2147483648 / -1 [SELECT (CAST(-2147483648 AS INTEGER) % CAST(-1 AS INTEGER))] |
| `arithmetic/mod.test:41` | `modulus(-9223372036854775808::i64, -1::i64) = 0::i64` | OutOfRangeException: Out of Range Error: Overflow in division of -9223372036854775808 / -1 [SELECT (CAST(-9223372036854775808 AS BIGINT) % CAST(-1 AS BIGINT))] |
| `arithmetic/mod.test:44` | `modulus(-128::i8, -1::i8) = 0::i8` | OutOfRangeException: Out of Range Error: Overflow in division of -128 / -1 [SELECT (CAST(-128 AS TINYINT) % CAST(-1 AS TINYINT))] |
| `arithmetic/mod.test:45` | `modulus(-32768::i16, -1::i16) = 0::i16` | OutOfRangeException: Out of Range Error: Overflow in division of -32768 / -1 [SELECT (CAST(-32768 AS SMALLINT) % CAST(-1 AS SMALLINT))] |
| `arithmetic/shift_left.test:17` | `shift_left(1::i32, 31::i32) = -2147483648::i32` | OutOfRangeException: Out of Range Error: Overflow in left shift (1 << 31) [SELECT (CAST(1 AS INTEGER) << CAST(31 AS INTEGER))] |
| `arithmetic/shift_left.test:18` | `shift_left(1::i64, 63::i32) = -9223372036854775808::i64` | OutOfRangeException: Out of Range Error: Overflow in left shift (1 << 63) [SELECT (CAST(1 AS BIGINT) << CAST(63 AS INTEGER))] |
| `arithmetic/shift_left.test:19` | `shift_left(2147483647::i32, 1::i32) = -2::i32` | OutOfRangeException: Out of Range Error: Overflow in left shift (2147483647 << 1) [SELECT (CAST(2147483647 AS INTEGER) << CAST(1 AS INTEGER))] |
| `arithmetic/shift_left.test:20` | `shift_left(-2147483648::i32, 1::i32) = 0::i32` | OutOfRangeException: Out of Range Error: Cannot left-shift negative number -2147483648 [SELECT (CAST(-2147483648 AS INTEGER) << CAST(1 AS INTEGER))] |
| `arithmetic/shift_left.test:21` | `shift_left(9223372036854775807::i64, 1::i32) = -2::i64` | OutOfRangeException: Out of Range Error: Overflow in left shift (9223372036854775807 << 1) [SELECT (CAST(9223372036854775807 AS BIGINT) << CAST(1 AS INTEGER))] |
| `arithmetic/shift_left.test:22` | `shift_left(-9223372036854775808::i64, 1::i32) = 0::i64` | OutOfRangeException: Out of Range Error: Cannot left-shift negative number -9223372036854775808 [SELECT (CAST(-9223372036854775808 AS BIGINT) << CAST(1 AS INTEGER))] |
| `arithmetic/shift_left.test:23` | `shift_left(5::i32, 30::i32) = -2147483648::i32` | OutOfRangeException: Out of Range Error: Overflow in left shift (5 << 30) [SELECT (CAST(5 AS INTEGER) << CAST(30 AS INTEGER))] |
| `arithmetic/shift_left.test:24` | `shift_left(3::i32, 31::i32) = -2147483648::i32` | OutOfRangeException: Out of Range Error: Overflow in left shift (3 << 31) [SELECT (CAST(3 AS INTEGER) << CAST(31 AS INTEGER))] |
| `arithmetic/shift_left.test:25` | `shift_left(3::i64, 63::i32) = -9223372036854775808::i64` | OutOfRangeException: Out of Range Error: Overflow in left shift (3 << 63) [SELECT (CAST(3 AS BIGINT) << CAST(63 AS INTEGER))] |
| `arithmetic/shift_left.test:36` | `shift_left(1073741824::i32, 2::i32) = 0::i32` | OutOfRangeException: Out of Range Error: Overflow in left shift (1073741824 << 2) [SELECT (CAST(1073741824 AS INTEGER) << CAST(2 AS INTEGER))] |
| `arithmetic/shift_left.test:37` | `shift_left(536870912::i32, 3::i32) = 0::i32` | OutOfRangeException: Out of Range Error: Overflow in left shift (536870912 << 3) [SELECT (CAST(536870912 AS INTEGER) << CAST(3 AS INTEGER))] |
| `arithmetic/shift_left.test:38` | `shift_left(4611686018427387904::i64, 2::i32) = 0::i64` | OutOfRangeException: Out of Range Error: Overflow in left shift (4611686018427387904 << 2) [SELECT (CAST(4611686018427387904 AS BIGINT) << CAST(2 AS INTEGER))] |
| `arithmetic/shift_left.test:39` | `shift_left(2305843009213693952::i64, 3::i32) = 0::i64` | OutOfRangeException: Out of Range Error: Overflow in left shift (2305843009213693952 << 3) [SELECT (CAST(2305843009213693952 AS BIGINT) << CAST(3 AS INTEGER))] |
| `arithmetic/shift_left.test:42` | `shift_left(5::i64, 62::i32) = -4611686018427387904::i64` | OutOfRangeException: Out of Range Error: Overflow in left shift (5 << 62) [SELECT (CAST(5 AS BIGINT) << CAST(62 AS INTEGER))] |
| `datetime/add_intervals.test:39` | `add_intervals(PT23H59M59S::iday, PT1S::iday) = P1D::iday` | got '24:00:00', expected '1 day' [SELECT CAST((SELECT (CAST('23 hours 59 minutes 59 seconds' AS INTERVAL) + CAST('1 seconds' AS INTERVAL))) AS VARCHAR)] |
| `datetime/add_intervals.test:40` | `add_intervals(PT12H30M::iday, PT11H45M::iday) = P1DT15M::iday` | got '24:15:00', expected '1 day 00:15:00' [SELECT CAST((SELECT (CAST('12 hours 30 minutes' AS INTERVAL) + CAST('11 hours 45 minutes' AS INTERVAL))) AS VARCHAR)] |
| `string/left.test:25` | `left('abcdef'::str, -1::i32) = ''::str` | got 'abcde', expected '' [SELECT left('abcdef', CAST(-1 AS INTEGER))] |
| `string/left.test:38` | `left('🏴󠁧󠁢󠁥󠁮󠁧󠁿test'::str, 2::i32) = '🏴󠁧󠁢󠁥󠁮󠁧󠁿t'::str` | got '🏴\U000e0067', expected '🏴\U000e0067\U000e0062\U000e0065\U000e006e\U000e0067\U000e007ft' [SELECT left('🏴󠁧󠁢󠁥󠁮󠁧󠁿test', CAST(2 AS INTEGER))] |
| `string/regexp_count_substring.test:55` | `regexp_count_substring('abcabc'::str, 'a'::str, 0::i64) = 0::i64` | got 2, expected 0 [SELECT len(regexp_extract_all(substring('abcabc', 0), 'a'))] |
| `string/regexp_replace.test:80` | `regexp_replace('hello'::str, 'l'::str, null::str?, 0::i64, 0::i64) = null::str?` | untranslatable: regexp_replace with position != 1 |
| `string/regexp_replace.test:86` | `regexp_replace('testtest'::str, 'test'::str, 'X'::str, 0::i64, 0::i64) = 'XX'::str` | untranslatable: regexp_replace with position != 1 |
| `string/regexp_replace.test:87` | `regexp_replace('dogdogdog'::str, 'dog'::str, 'cat'::str, 0::i64, 0::i64) = 'catcatcat'::str` | untranslatable: regexp_replace with position != 1 |
| `string/regexp_replace.test:88` | `regexp_replace('abcdefabc'::str, 'abc'::str, 'X'::str, 0::i64, 4::i64) = 'abcdefX'::str` | untranslatable: regexp_replace with position != 1 |
| `string/regexp_replace.test:91` | `regexp_replace('test'::str, 't'::str, ''::str, 0::i64, 0::i64) = 'es'::str` | untranslatable: regexp_replace with position != 1 |
| `string/regexp_string_split.test:48` | `regexp_string_split('abc'::str, ''::str) = ['', 'a', 'b', 'c', '']::list<str>` | got ['a', 'b', 'c'], expected ['', 'a', 'b', 'c', ''] [SELECT regexp_split_to_array('abc', '')] |
| `string/right.test:29` | `right('abcdef'::str, -1::i32) = ''::str` | got 'bcdef', expected '' [SELECT right('abcdef', CAST(-1 AS INTEGER))] |
| `string/right.test:42` | `right('a̐é̲'::str, 1::i32) = 'é̲'::str` | got '̲', expected 'é̲' [SELECT right('a̐é̲', CAST(1 AS INTEGER))] |
| `string/string_split.test:20` | `string_split(''::str, ' '::str) = []::list<str>` | got [''], expected [] [SELECT string_split('', ' ')] |
| `string/string_split.test:22` | `string_split(''::str, ''::str) = []::list<str>` | got [''], expected [] [SELECT string_split('', '')] |
| `string/substring.test:30` | `substring('abcdefg'::str, 0::i32, 3::i32) = 'abc'::str` | got 'ab', expected 'abc' [SELECT substring('abcdefg', CAST(0 AS INTEGER), CAST(3 AS INTEGER))] |
| `string/substring.test:31` | `substring('abcdefg'::str, 0::i32, 1::i32) = 'a'::str` | got '', expected 'a' [SELECT substring('abcdefg', CAST(0 AS INTEGER), CAST(1 AS INTEGER))] |
| `string/substring.test:38` | `substring('abcdefg'::str, 3::i32, -2::i32) = ''::str` | got 'ab', expected '' [SELECT substring('abcdefg', CAST(3 AS INTEGER), CAST(-2 AS INTEGER))] |
