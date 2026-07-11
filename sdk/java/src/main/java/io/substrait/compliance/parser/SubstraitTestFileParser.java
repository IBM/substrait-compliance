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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Substrait .test files containing arithmetic function test cases.
 */
public class SubstraitTestFileParser {
    
    // Pattern to match test case lines like: add(120::i8, 5::i8) = 125::i8
    // or: add(120::i8, 10::i8) [overflow:ERROR] = <!ERROR>
    // Note: We don't use regex to extract arguments due to nested parentheses in aggregate functions
    // Instead, we manually parse to handle cases like: approx_count_distinct((1, 2, 3)::i8)
    private static final Pattern TEST_CASE_PATTERN = Pattern.compile(
        "^\\s*([a-z_]+)\\((.+)$"
    );
    
    // Pattern to match argument with type: value::type
    // Note: value can contain parentheses for tuples/arrays like (1, 2, 3)::i8
    private static final Pattern ARG_PATTERN = Pattern.compile(
        "(.+?)::([a-z0-9_]+)$"
    );
    
    // Pattern to match options like: overflow:ERROR, rounding:TIE_TO_EVEN
    private static final Pattern OPTION_PATTERN = Pattern.compile(
        "([a-z_]+):([A-Z_]+)"
    );

    /**
     * Parse a Substrait test file from a Path.
     */
    public List<ArithmeticTestCase> parseFile(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            return parseStream(reader, filePath.getFileName().toString());
        }
    }

    /**
     * Parse a Substrait test file from an InputStream (e.g., from resources).
     */
    public List<ArithmeticTestCase> parseFile(InputStream inputStream, String fileName) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return parseStream(reader, fileName);
        }
    }

    /**
     * Parse a Substrait test file from a BufferedReader.
     */
    private List<ArithmeticTestCase> parseStream(BufferedReader reader, String fileName) throws IOException {
        List<ArithmeticTestCase> testCases = new ArrayList<>();
        String line;
        int lineNumber = 0;
        String currentCategory = "uncategorized";

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();

            // Skip empty lines and header lines
            if (line.isEmpty() || line.startsWith("###")) {
                continue;
            }

            // Extract category from comments
            if (line.startsWith("#")) {
                String comment = line.substring(1).trim();
                if (comment.contains(":")) {
                    currentCategory = comment.substring(0, comment.indexOf(":")).trim();
                }
                continue;
            }

            // Try to parse as a test case
            ArithmeticTestCase testCase = parseTestCase(line, currentCategory, lineNumber, fileName);
            if (testCase != null) {
                testCases.add(testCase);
            }
        }

        return testCases;
    }

    /**
     * Parse a single test case line.
     */
    private ArithmeticTestCase parseTestCase(String line, String category, int lineNumber, String fileName) {
        Matcher matcher = TEST_CASE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null; // Not a valid test case line
        }

        String functionName = matcher.group(1);
        String remainder = matcher.group(2); // Everything after function name and opening paren
        
        // Manually find the matching closing parenthesis for the function arguments
        int parenDepth = 1; // We already consumed the opening paren
        int argsEndIndex = -1;
        
        for (int i = 0; i < remainder.length(); i++) {
            char c = remainder.charAt(i);
            if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                parenDepth--;
                if (parenDepth == 0) {
                    argsEndIndex = i;
                    break;
                }
            }
        }
        
        if (argsEndIndex == -1) {
            return null; // Unmatched parentheses
        }
        
        String argsString = remainder.substring(0, argsEndIndex);
        String afterArgs = remainder.substring(argsEndIndex + 1).trim();
        
        // Parse options if present: [option:VALUE]
        String optionsString = null;
        if (afterArgs.startsWith("[")) {
            int optionsEnd = afterArgs.indexOf("]");
            if (optionsEnd != -1) {
                optionsString = afterArgs.substring(1, optionsEnd);
                afterArgs = afterArgs.substring(optionsEnd + 1).trim();
            }
        }
        
        // Parse expected result: = value::type or = value
        if (!afterArgs.startsWith("=")) {
            return null; // Invalid format
        }
        
        afterArgs = afterArgs.substring(1).trim();
        
        // Find the expected result and type
        String expectedResult;
        String expectedResultType = null;
        
        // Check if there's a type annotation (::type)
        int typeIndex = afterArgs.lastIndexOf("::");
        if (typeIndex != -1) {
            expectedResult = afterArgs.substring(0, typeIndex).trim();
            expectedResultType = afterArgs.substring(typeIndex + 2).trim();
        } else {
            expectedResult = afterArgs.trim();
        }

        // Parse arguments
        List<String> arguments = new ArrayList<>();
        List<String> argumentTypes = new ArrayList<>();
        parseArguments(argsString, arguments, argumentTypes);

        // Parse options
        Map<String, String> options = new HashMap<>();
        if (optionsString != null) {
            parseOptions(optionsString, options);
        }

        // Strip quotes from string expected results
        if (expectedResultType != null && (expectedResultType.equals("str") || expectedResultType.equals("string"))) {
            if (expectedResult.startsWith("'") && expectedResult.endsWith("'") && expectedResult.length() >= 2) {
                expectedResult = expectedResult.substring(1, expectedResult.length() - 1);
                // Unescape doubled single quotes
                expectedResult = expectedResult.replace("''", "'");
            }
        }

        return new ArithmeticTestCase(
            functionName,
            arguments.toArray(new String[0]),
            argumentTypes.toArray(new String[0]),
            expectedResult,
            expectedResultType,
            options,
            category,
            lineNumber,
            fileName
        );
    }

    /**
     * Parse function arguments from the arguments string.
     * Handles commas inside quoted strings and parentheses properly.
     */
    private void parseArguments(String argsString, List<String> arguments, List<String> argumentTypes) {
        // Split by comma, but respect quotes and parentheses
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int parenDepth = 0;
        
        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);
            
            if (c == '\'' && (i == 0 || argsString.charAt(i-1) != '\\')) {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == '(' && !inQuotes) {
                parenDepth++;
                current.append(c);
            } else if (c == ')' && !inQuotes) {
                parenDepth--;
                current.append(c);
            } else if (c == ',' && !inQuotes && parenDepth == 0) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        
        for (String part : parts) {
            Matcher argMatcher = ARG_PATTERN.matcher(part);
            if (argMatcher.find()) {
                String value = argMatcher.group(1).trim();
                String type = argMatcher.group(2).trim();
                
                // Strip quotes from string values - they will be added back in SQL generation
                if ((type.equals("str") || type.equals("string")) && value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                    // Unescape doubled single quotes
                    value = value.replace("''", "'");
                }
                
                arguments.add(value);
                argumentTypes.add(type);
            }
        }
    }

    /**
     * Parse options from the options string.
     */
    private void parseOptions(String optionsString, Map<String, String> options) {
        Matcher optionMatcher = OPTION_PATTERN.matcher(optionsString);
        while (optionMatcher.find()) {
            String key = optionMatcher.group(1);
            String value = optionMatcher.group(2);
            options.put(key, value);
        }
    }

    /**
     * Parse all test files from a directory.
     */
    public Map<String, List<ArithmeticTestCase>> parseDirectory(Path directory) throws IOException {
        Map<String, List<ArithmeticTestCase>> allTestCases = new HashMap<>();
        
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".test"))
            .forEach(path -> {
                try {
                    List<ArithmeticTestCase> testCases = parseFile(path);
                    allTestCases.put(path.getFileName().toString(), testCases);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to parse file: " + path, e);
                }
            });
        
        return allTestCases;
    }
}

