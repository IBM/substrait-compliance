using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace Substrait.Compliance
{
    /// <summary>
    /// Engine metadata information
    /// </summary>
    public record EngineInfo(
        string Name,
        string Version,
        string Vendor,
        string? Description = null
    );

    /// <summary>
    /// Engine capability information
    /// </summary>
    public record EngineCapabilities(
        IReadOnlyList<string> SupportedRelations,
        IReadOnlyList<string> SupportedFunctions,
        IReadOnlyList<string> SupportedTypes,
        IReadOnlyDictionary<string, string>? Extensions = null
    );

    /// <summary>
    /// Main interface that query engines must implement
    /// </summary>
    public interface IComplianceEngine
    {
        /// <summary>
        /// Get engine metadata
        /// </summary>
        EngineInfo GetInfo();

        /// <summary>
        /// Get engine capabilities
        /// </summary>
        EngineCapabilities GetCapabilities();

        /// <summary>
        /// Execute a Substrait plan
        /// </summary>
        /// <param name="planBytes">Serialized Substrait plan (JSON or binary)</param>
        /// <param name="inputData">Map of table names to input data</param>
        /// <returns>Compliance result with output data</returns>
        Task<ComplianceResult> ExecutePlanAsync(
            byte[] planBytes,
            IReadOnlyDictionary<string, TableData> inputData);

        /// <summary>
        /// Validate a Substrait plan without executing it
        /// </summary>
        /// <param name="planBytes">Serialized Substrait plan</param>
        /// <returns>Compliance result indicating validity</returns>
        Task<ComplianceResult> ValidatePlanAsync(byte[] planBytes);

        /// <summary>
        /// Optional: Initialize engine resources
        /// </summary>
        Task InitializeAsync() => Task.CompletedTask;

        /// <summary>
        /// Optional: Cleanup engine resources
        /// </summary>
        Task ShutdownAsync() => Task.CompletedTask;

        /// <summary>
        /// Optional: Check if engine can handle a specific test
        /// </summary>
        /// <param name="testId">Test identifier</param>
        /// <returns>True if engine can run the test</returns>
        bool CanRunTest(string testId) => true;
    }
}

// Made with Bob
