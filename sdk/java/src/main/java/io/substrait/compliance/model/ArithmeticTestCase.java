/*
 * Copyright 2026 Substrait Validation Framework Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.substrait.test.arithmetic;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a single arithmetic test case from Substrait test files.
 */
public class ArithmeticTestCase {
    private final String functionName;
    private final String[] arguments;
    private final String[] argumentTypes;
    private final String expectedResult;
    private final String expectedResultType;
    private final Map<String, String> options; // e.g., overflow:ERROR, rounding:TIE_TO_EVEN
    private final String category; // e.g., basic, overflow, floating_exception
    private final int lineNumber;
    private final String sourceFile;

    public ArithmeticTestCase(
            String functionName,
            String[] arguments,
            String[] argumentTypes,
            String expectedResult,
            String expectedResultType,
            Map<String, String> options,
            String category,
            int lineNumber,
            String sourceFile) {
        this.functionName = functionName;
        this.arguments = arguments;
        this.argumentTypes = argumentTypes;
        this.expectedResult = expectedResult;
        this.expectedResultType = expectedResultType;
        this.options = options;
        this.category = category;
        this.lineNumber = lineNumber;
        this.sourceFile = sourceFile;
    }

    public String getFunctionName() {
        return functionName;
    }

    public String[] getArguments() {
        return arguments;
    }

    public String[] getArgumentTypes() {
        return argumentTypes;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public String getExpectedResultType() {
        return expectedResultType;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public String getCategory() {
        return category;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public boolean isErrorExpected() {
        return expectedResult.equals("<!ERROR>");
    }

    public boolean isUndefinedExpected() {
        return expectedResult.equals("<!UNDEFINED>");
    }

    public boolean isNanExpected() {
        return expectedResult.equals("<!NAN>") || expectedResult.equals("nan");
    }

    public boolean isInfinityExpected() {
        return expectedResult.equals("inf");
    }

    public boolean isNegativeInfinityExpected() {
        return expectedResult.equals("-inf");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(functionName).append("(");
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arguments[i]).append("::").append(argumentTypes[i]);
        }
        sb.append(")");
        
        if (!options.isEmpty()) {
            sb.append(" [");
            options.forEach((k, v) -> sb.append(k).append(":").append(v).append(" "));
            sb.append("]");
        }
        
        sb.append(" = ").append(expectedResult);
        if (expectedResultType != null) {
            sb.append("::").append(expectedResultType);
        }
        
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArithmeticTestCase that = (ArithmeticTestCase) o;
        return lineNumber == that.lineNumber &&
                Objects.equals(functionName, that.functionName) &&
                Objects.equals(sourceFile, that.sourceFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionName, lineNumber, sourceFile);
    }
}

