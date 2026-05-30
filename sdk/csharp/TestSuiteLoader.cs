using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using YamlDotNet.Serialization;
using YamlDotNet.Serialization.NamingConventions;

namespace Substrait.Compliance
{
    /// <summary>
    /// Loader for test suites from YAML files
    /// </summary>
    public class TestSuiteLoader
    {
        private readonly IDeserializer _deserializer;

        public TestSuiteLoader()
        {
            _deserializer = new DeserializerBuilder()
                .WithNamingConvention(UnderscoredNamingConvention.Instance)
                .IgnoreUnmatchedProperties()
                .Build();
        }

        /// <summary>
        /// Load a test suite from a YAML file
        /// </summary>
        public async Task<TestSuite> LoadFromFileAsync(string filePath)
        {
            var content = await File.ReadAllTextAsync(filePath);
            var basePath = Path.GetDirectoryName(filePath) ?? ".";
            return LoadFromYaml(content, basePath);
        }

        /// <summary>
        /// Load a test suite from YAML string
        /// </summary>
        public TestSuite LoadFromYaml(string yamlContent, string basePath = ".")
        {
            var data = _deserializer.Deserialize<Dictionary<string, object>>(yamlContent);

            var metadata = ParseMetadata(data);
            var testCases = ParseTestCases(data, basePath);

            return new TestSuite(metadata, testCases);
        }

        /// <summary>
        /// Load multiple test suites from a directory
        /// </summary>
        public async Task<List<TestSuite>> LoadFromDirectoryAsync(string dirPath)
        {
            var suites = new List<TestSuite>();
            var yamlFiles = Directory.GetFiles(dirPath, "*.yaml")
                .Concat(Directory.GetFiles(dirPath, "*.yml"));

            foreach (var filePath in yamlFiles)
            {
                try
                {
                    var suite = await LoadFromFileAsync(filePath);
                    suites.Add(suite);
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"Failed to load test suite from {filePath}: {ex.Message}");
                }
            }

            return suites;
        }

        private TestSuiteMetadata ParseMetadata(Dictionary<string, object> data)
        {
            var name = GetValue<string>(data, "name") ?? "Unnamed Test Suite";
            var version = GetValue<string>(data, "version") ?? "1.0.0";
            var description = GetValue<string>(data, "description");
            var category = GetValue<string>(data, "category");
            var tags = GetValue<List<object>>(data, "tags")?.Select(t => t.ToString()!).ToList();

            return new TestSuiteMetadata(name, version, description, category, tags);
        }

        private List<TestCase> ParseTestCases(Dictionary<string, object> data, string basePath)
        {
            var testCases = new List<TestCase>();
            var tests = GetValue<List<object>>(data, "tests");

            if (tests == null)
                return testCases;

            foreach (var test in tests)
            {
                if (test is Dictionary<object, object> testDict)
                {
                    var testData = testDict.ToDictionary(
                        kvp => kvp.Key.ToString()!,
                        kvp => kvp.Value);
                    testCases.Add(ParseTestCase(testData, basePath));
                }
            }

            return testCases;
        }

        private TestCase ParseTestCase(Dictionary<string, object> test, string basePath)
        {
            var id = GetValue<string>(test, "id") ?? GetValue<string>(test, "name") ?? "unknown";
            var description = GetValue<string>(test, "description");
            var planPath = GetValue<string>(test, "plan") ?? throw new InvalidOperationException("Test case missing 'plan' field");
            var plan = Path.Combine(basePath, planPath);

            var inputData = ParseInputData(test);
            var expectedOutput = ParseExpectedOutput(test);
            var tags = GetValue<List<object>>(test, "tags")?.Select(t => t.ToString()!).ToList();
            var skip = GetValue<bool>(test, "skip");
            var skipReason = GetValue<string>(test, "skip_reason");

            return new TestCase(id, plan, description, inputData, expectedOutput, tags, skip, skipReason);
        }

        private Dictionary<string, TableData>? ParseInputData(Dictionary<string, object> test)
        {
            var inputDataObj = GetValue<Dictionary<object, object>>(test, "input_data");
            if (inputDataObj == null)
                return null;

            var result = new Dictionary<string, TableData>();
            foreach (var kvp in inputDataObj)
            {
                var name = kvp.Key.ToString()!;
                if (kvp.Value is Dictionary<object, object> tableDict)
                {
                    var tableData = tableDict.ToDictionary(
                        k => k.Key.ToString()!,
                        v => v.Value);
                    result[name] = ParseTableData(tableData);
                }
            }

            return result;
        }

        private TableData? ParseExpectedOutput(Dictionary<string, object> test)
        {
            var expectedOutputObj = GetValue<Dictionary<object, object>>(test, "expected_output");
            if (expectedOutputObj == null)
                return null;

            var expectedOutput = expectedOutputObj.ToDictionary(
                kvp => kvp.Key.ToString()!,
                kvp => kvp.Value);
            return ParseTableData(expectedOutput);
        }

        private TableData ParseTableData(Dictionary<string, object> data)
        {
            var columns = ParseColumns(data);
            var rows = ParseRows(data);

            return new TableData(columns, rows);
        }

        private List<Column> ParseColumns(Dictionary<string, object> data)
        {
            var columnsObj = GetValue<List<object>>(data, "columns");
            if (columnsObj == null)
                return new List<Column>();

            var columns = new List<Column>();
            foreach (var col in columnsObj)
            {
                if (col is Dictionary<object, object> colDict)
                {
                    var colData = colDict.ToDictionary(
                        kvp => kvp.Key.ToString()!,
                        kvp => kvp.Value);

                    var name = GetValue<string>(colData, "name") ?? "unknown";
                    var typeStr = GetValue<string>(colData, "type") ?? "string";
                    var nullable = GetValue<bool?>(colData, "nullable") ?? true;

                    var type = ParseColumnType(typeStr);
                    columns.Add(new Column(name, type, nullable));
                }
            }

            return columns;
        }

        private List<object?[]> ParseRows(Dictionary<string, object> data)
        {
            var rowsObj = GetValue<List<object>>(data, "rows");
            if (rowsObj == null)
                return new List<object?[]>();

            var rows = new List<object?[]>();
            foreach (var row in rowsObj)
            {
                if (row is List<object> rowList)
                {
                    rows.Add(rowList.ToArray());
                }
            }

            return rows;
        }

        private ColumnType ParseColumnType(string typeStr)
        {
            var normalized = typeStr.ToLowerInvariant();

            if (normalized.Contains("int")) return ColumnType.Integer;
            if (normalized.Contains("float") || normalized.Contains("double")) return ColumnType.Float;
            if (normalized.Contains("decimal") || normalized.Contains("numeric")) return ColumnType.Decimal;
            if (normalized.Contains("bool")) return ColumnType.Boolean;
            if (normalized.Contains("string") || normalized.Contains("varchar") || normalized.Contains("text"))
                return ColumnType.String;
            if (normalized.Contains("date") && !normalized.Contains("time")) return ColumnType.Date;
            if (normalized.Contains("timestamp") || normalized.Contains("datetime")) return ColumnType.Timestamp;
            if (normalized.Contains("time") && !normalized.Contains("stamp")) return ColumnType.Time;
            if (normalized.Contains("binary") || normalized.Contains("blob")) return ColumnType.Binary;

            return ColumnType.String; // Default fallback
        }

        private T? GetValue<T>(Dictionary<string, object> dict, string key)
        {
            if (dict.TryGetValue(key, out var value))
            {
                if (value is T typedValue)
                    return typedValue;
                
                // Try to convert
                try
                {
                    return (T)Convert.ChangeType(value, typeof(T));
                }
                catch
                {
                    return default;
                }
            }
            return default;
        }
    }
}

// Made with Bob
