-- Substrait Compliance API - Webhooks Schema
-- Version: 2.0
-- Description: Adds webhook registration and delivery tracking tables

-- Webhooks table
CREATE TABLE webhooks (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    events VARCHAR(255)[] NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_webhook_url CHECK (url ~ '^https?://'),
    CONSTRAINT chk_webhook_events CHECK (array_length(events, 1) > 0)
);

CREATE INDEX idx_webhooks_active ON webhooks(active) WHERE active = true;
CREATE INDEX idx_webhooks_created_by ON webhooks(created_by);
CREATE INDEX idx_webhooks_events ON webhooks USING GIN (events);

COMMENT ON TABLE webhooks IS 'Registered webhooks for event notifications';
COMMENT ON COLUMN webhooks.url IS 'Webhook endpoint URL (must be HTTP/HTTPS)';
COMMENT ON COLUMN webhooks.secret IS 'Secret key for HMAC signature verification';
COMMENT ON COLUMN webhooks.events IS 'Array of event types to subscribe to';

-- Webhook deliveries table
CREATE TABLE webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'RETRYING')),
    response_code INTEGER,
    response_body TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_retry_at TIMESTAMP,
    CONSTRAINT fk_delivery_webhook FOREIGN KEY (webhook_id) REFERENCES webhooks(id) ON DELETE CASCADE,
    CONSTRAINT chk_delivery_status CHECK (
        (status = 'SUCCESS' AND delivered_at IS NOT NULL) OR
        (status IN ('PENDING', 'FAILED', 'RETRYING'))
    )
);

CREATE INDEX idx_deliveries_webhook ON webhook_deliveries(webhook_id);
CREATE INDEX idx_deliveries_status ON webhook_deliveries(status);
CREATE INDEX idx_deliveries_event_type ON webhook_deliveries(event_type);
CREATE INDEX idx_deliveries_created_at ON webhook_deliveries(created_at DESC);
CREATE INDEX idx_deliveries_next_retry ON webhook_deliveries(next_retry_at) 
    WHERE status = 'RETRYING' AND next_retry_at IS NOT NULL;

COMMENT ON TABLE webhook_deliveries IS 'Webhook delivery attempts and results';
COMMENT ON COLUMN webhook_deliveries.payload IS 'Event payload sent to webhook';
COMMENT ON COLUMN webhook_deliveries.status IS 'Delivery status';
COMMENT ON COLUMN webhook_deliveries.attempt_count IS 'Number of delivery attempts';
COMMENT ON COLUMN webhook_deliveries.next_retry_at IS 'Timestamp for next retry attempt';

-- Create trigger for webhooks table
CREATE TRIGGER update_webhooks_updated_at
    BEFORE UPDATE ON webhooks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create a view for webhook statistics
CREATE VIEW webhook_stats AS
SELECT 
    w.id as webhook_id,
    w.url,
    w.active,
    COUNT(wd.id) as total_deliveries,
    COUNT(CASE WHEN wd.status = 'SUCCESS' THEN 1 END) as successful_deliveries,
    COUNT(CASE WHEN wd.status = 'FAILED' THEN 1 END) as failed_deliveries,
    COUNT(CASE WHEN wd.status = 'RETRYING' THEN 1 END) as retrying_deliveries,
    MAX(wd.created_at) as last_delivery_at,
    CASE 
        WHEN COUNT(wd.id) > 0 THEN 
            ROUND(100.0 * COUNT(CASE WHEN wd.status = 'SUCCESS' THEN 1 END) / COUNT(wd.id), 2)
        ELSE 0
    END as success_rate
FROM webhooks w
LEFT JOIN webhook_deliveries wd ON w.id = wd.webhook_id
GROUP BY w.id, w.url, w.active;

COMMENT ON VIEW webhook_stats IS 'Aggregated statistics for webhook deliveries';

-- Made with Bob
