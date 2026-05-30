/**
 * Engine metadata information
 */
export interface EngineInfo {
  name: string;
  version: string;
  vendor: string;
  description?: string;
}

/**
 * Engine capability information
 */
export interface EngineCapabilities {
  supportedRelations: string[];
  supportedFunctions: string[];
  supportedTypes: string[];
  extensions?: Record<string, string>;
}

/**
 * Helper class for engine capabilities
 */
export class Capabilities implements EngineCapabilities {
  supportedRelations: string[] = [];
  supportedFunctions: string[] = [];
  supportedTypes: string[] = [];
  extensions: Record<string, string> = {};

  supportsRelation(relation: string): boolean {
    return this.supportedRelations.includes(relation);
  }

  supportsFunction(func: string): boolean {
    return this.supportedFunctions.includes(func);
  }

  supportsType(type: string): boolean {
    return this.supportedTypes.includes(type);
  }

  addRelation(relation: string): this {
    this.supportedRelations.push(relation);
    return this;
  }

  addFunction(func: string): this {
    this.supportedFunctions.push(func);
    return this;
  }

  addType(type: string): this {
    this.supportedTypes.push(type);
    return this;
  }
}

import { ComplianceResult } from './result';
import { TableData } from './table-data';

/**
 * Main interface that query engines must implement
 */
export interface ComplianceEngine {
  /**
   * Get engine metadata
   */
  getInfo(): EngineInfo;

  /**
   * Get engine capabilities
   */
  getCapabilities(): EngineCapabilities;

  /**
   * Execute a Substrait plan
   * @param planBytes Serialized Substrait plan
   * @param inputData Map of table names to input data
   */
  executePlan(
    planBytes: Uint8Array,
    inputData: Map<string, TableData>
  ): Promise<ComplianceResult>;

  /**
   * Validate a Substrait plan without executing it
   * @param planBytes Serialized Substrait plan
   */
  validatePlan(planBytes: Uint8Array): Promise<ComplianceResult>;

  /**
   * Optional: Initialize engine resources
   */
  initialize?(): Promise<void>;

  /**
   * Optional: Cleanup engine resources
   */
  shutdown?(): Promise<void>;

  /**
   * Optional: Check if engine can handle a specific test
   * @param testId Test identifier
   */
  canRunTest?(testId: string): boolean;
}

// Made with Bob
