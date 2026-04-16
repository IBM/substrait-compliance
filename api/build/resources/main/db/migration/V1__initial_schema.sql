-- Substrait Compliance API - Initial Database Schema
-- Version: 1.0
-- Description: Creates core tables for engines, reports, test results, and API keys

-- Engines table
CREATE TABLE engines (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(100) NOT NULL,
    vendor VARCHAR(255),
    substrait_version VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_engine_name_version UNIQUE(name, version)
);

CREATE INDEX idx_engines_name ON engines(name);
CREATE INDEX idx_engines_created_at ON engines(created_at DESC);

COMMENT ON TABLE engines IS 'Database engines that have submitted compliance reports';
COMMENT ON COLUMN engines.name IS 'Engine name (e.g., DuckDB, DataFusion)';
COMMENT ON COLUMN engines.version IS 'Engine version (e.g., 0.10.0)';
COMMENT ON COLUMN engines.substrait_version IS 'Supported Substrait version';

-- Compliance reports table
CREATE TABLE compliance_reports (
    id BIGSERIAL PRIMARY KEY,
    engine_id BIGINT NOT NULL,
    test_suite_name VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    total_tests INTEGER NOT NULL CHECK (total_tests >= 0),
    passed_count INTEGER NOT NULL CHECK (passed_count >= 0),
    failed_count INTEGER NOT NULL CHECK (failed_count >= 0),
    skipped_count INTEGER NOT NULL CHECK (skipped_count >= 0),
    compliance_score DECIMAL(5,2) NOT NULL CHECK (compliance_score >= 0 AND compliance_score <= 100),
    execution_time_ms BIGINT CHECK (execution_time_ms >= 0),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_engine FOREIGN KEY (engine_id) REFERENCES engines(id) ON DELETE CASCADE,
    CONSTRAINT chk_test_counts CHECK (total_tests = passed_count + failed_count + skipped_count)
);

CREATE INDEX idx_reports_engine ON compliance_reports(engine_id);
CREATE INDEX idx_reports_suite ON compliance_reports(test_suite_name);
CREATE INDEX idx_reports_timestamp ON compliance_reports(timestamp DESC);
CREATE INDEX idx_reports_score ON compliance_reports(compliance_score DESC);
CREATE INDEX idx_reports_created_at ON compliance_reports(created_at DESC);
CREATE INDEX idx_reports_metadata ON compliance_reports USING GIN (metadata);

COMMENT ON TABLE compliance_reports IS 'Compliance test execution reports';
COMMENT ON COLUMN compliance_reports.timestamp IS 'Report timestamp in milliseconds since epoch';
COMMENT ON COLUMN compliance_reports.compliance_score IS 'Percentage of tests passed (0-100)';
COMMENT ON COLUMN compliance_reports.metadata IS 'Additional metadata (CI info, commit SHA, etc.)';

-- Test results table
CREATE TABLE test_results (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    test_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PASSED', 'FAILED', 'SKIPPED')),
    message TEXT,
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_result_report FOREIGN KEY (report_id) REFERENCES compliance_reports(id) ON DELETE CASCADE
);

CREATE INDEX idx_test_results_report ON test_results(report_id);
CREATE INDEX idx_test_results_status ON test_results(status);
CREATE INDEX idx_test_results_test_id ON test_results(test_id);

COMMENT ON TABLE test_results IS 'Individual test case results within a compliance report';
COMMENT ON COLUMN test_results.test_id IS 'Unique identifier for the test case';
COMMENT ON COLUMN test_results.status IS 'Test execution status';
COMMENT ON COLUMN test_results.duration_ms IS 'Test execution duration in milliseconds';

-- API keys table
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    scopes VARCHAR(100)[] NOT NULL,
    rate_limit INTEGER NOT NULL DEFAULT 1000 CHECK (rate_limit > 0),
    active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    CONSTRAINT chk_expiry CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX idx_api_keys_active ON api_keys(active) WHERE active = true;
CREATE INDEX idx_api_keys_expires ON api_keys(expires_at) WHERE expires_at IS NOT NULL;

COMMENT ON TABLE api_keys IS 'API keys for authentication and rate limiting';
COMMENT ON COLUMN api_keys.key_hash IS 'SHA-256 hash of the API key';
COMMENT ON COLUMN api_keys.scopes IS 'Array of permission scopes (e.g., report:write, report:read)';
COMMENT ON COLUMN api_keys.rate_limit IS 'Maximum requests per hour for this key';

-- Create a function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for engines table
CREATE TRIGGER update_engines_updated_at
    BEFORE UPDATE ON engines
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert sample data for development (optional, can be removed for production)
-- This helps with initial testing
INSERT INTO engines (name, version, vendor, substrait_version) VALUES
    ('DuckDB', '0.10.0', 'DuckDB Labs', '0.80.0'),
    ('DataFusion', '35.0.0', 'Apache', '0.80.0')
ON CONFLICT (name, version) DO NOTHING;

-- Made with Bob
