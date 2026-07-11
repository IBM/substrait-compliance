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

import java.util.Objects;

/**
 * Represents the result of executing an arithmetic test case.
 */
public class ArithmeticTestResult {
    private final ArithmeticTestCase testCase;
    private final String engine;
    private final boolean success;
    private final String actualResult;
    private final String actualResultType;
    private final String errorMessage;
    private final Throwable exception;
    private final long executionTimeMs;

    public ArithmeticTestResult(
            ArithmeticTestCase testCase,
            String engine,
            boolean success,
            String actualResult,
            String actualResultType,
            String errorMessage,
            Throwable exception,
            long executionTimeMs) {
        this.testCase = testCase;
        this.engine = engine;
        this.success = success;
        this.actualResult = actualResult;
        this.actualResultType = actualResultType;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.executionTimeMs = executionTimeMs;
    }

    public static ArithmeticTestResult success(
            ArithmeticTestCase testCase,
            String engine,
            String actualResult,
            String actualResultType,
            long executionTimeMs) {
        return new ArithmeticTestResult(
                testCase, engine, true, actualResult, actualResultType, null, null, executionTimeMs);
    }

    public static ArithmeticTestResult failure(
            ArithmeticTestCase testCase,
            String engine,
            String actualResult,
            String actualResultType,
            String errorMessage,
            long executionTimeMs) {
        return new ArithmeticTestResult(
                testCase, engine, false, actualResult, actualResultType, errorMessage, null, executionTimeMs);
    }

    public static ArithmeticTestResult error(
            ArithmeticTestCase testCase,
            String engine,
            Throwable exception,
            long executionTimeMs) {
        return new ArithmeticTestResult(
                testCase, engine, false, null, null, exception.getMessage(), exception, executionTimeMs);
    }

    public ArithmeticTestCase getTestCase() {
        return testCase;
    }

    public String getEngine() {
        return engine;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getActualResult() {
        return actualResult;
    }

    public String getActualResultType() {
        return actualResultType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getException() {
        return exception;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(engine).append("] ");
        sb.append(testCase.toString());
        sb.append(" -> ");
        
        if (success) {
            sb.append("✓ PASS");
            if (actualResult != null) {
                sb.append(" (actual: ").append(actualResult);
                if (actualResultType != null) {
                    sb.append("::").append(actualResultType);
                }
                sb.append(")");
            }
        } else {
            sb.append("✗ FAIL");
            if (errorMessage != null) {
                sb.append(" - ").append(errorMessage);
            }
            if (actualResult != null) {
                sb.append(" (actual: ").append(actualResult);
                if (actualResultType != null) {
                    sb.append("::").append(actualResultType);
                }
                sb.append(")");
            }
        }
        
        sb.append(" [").append(executionTimeMs).append("ms]");
        
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArithmeticTestResult that = (ArithmeticTestResult) o;
        return Objects.equals(testCase, that.testCase) &&
                Objects.equals(engine, that.engine);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testCase, engine);
    }
}

