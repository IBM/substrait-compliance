# Webhook Delivery Guide

## Overview

The Substrait Compliance API includes a robust webhook delivery system that sends real-time notifications when compliance events occur. This guide covers webhook registration, delivery mechanics, retry logic, and security.

## Table of Contents

1. [Features](#features)
2. [Event Types](#event-types)
3. [Webhook Registration](#webhook-registration)
4. [Delivery Mechanics](#delivery-mechanics)
5. [Retry Logic](#retry-logic)
6. [Security](#security)
7. [Monitoring](#monitoring)
8. [Configuration](#configuration)
9. [Examples](#examples)
10. [Troubleshooting](#troubleshooting)

---

## Features

### ✅ Core Capabilities

- **Async Event-Driven Delivery**: Non-blocking webhook notifications
- **Exponential Backoff Retry**: Automatic retry with increasing delays
- **HMAC-SHA256 Signatures**: Cryptographic verification of webhook authenticity
- **Delivery Tracking**: Complete audit trail of all delivery attempts
- **Selective Subscriptions**: Subscribe to specific event types
- **Active/Inactive Control**: Enable/disable webhooks without deletion

### 📊 Delivery Statistics

- Success/failure rates per webhook
- Delivery attempt counts
- Response codes and bodies
- Last delivery timestamps

---

## Event Types

The system supports three event types:

### 1. `report.submitted`

Triggered when a new compliance report is submitted.

**Payload**:
```json
{
  "reportId": 123,
  "engineName": "DuckDB",
  "engineVersion": "0.10.0",
  "testSuiteName": "tpch",
  "complianceScore": 90.9,
  "passed": 20,
  "failed": 2,
  "skipped": 0,
  "timestamp": "2026-04-30T13:00:00Z"
}
```

### 2. `report.failed`

Triggered when a report has a low compliance score (configurable threshold).

**Payload**:
```json
{
  "reportId": 124,
  "engineName": "MyEngine",
  "engineVersion": "1.0.0",
  "testSuiteName": "tpch",
  "complianceScore": 45.5,
  "failedTests": 12
}
```

### 3. `leaderboard.updated`

Triggered when the compliance leaderboard is updated.

**Payload**:
```json
{
  "message": "Compliance leaderboard has been updated",
  "timestamp": 1714485600000
}
```

---

## Webhook Registration

### Register a Webhook

**Endpoint**: `POST /api/v1/webhooks`

**Request**:
```json
{
  "url": "https://your-domain.com/webhooks/compliance",
  "secret": "your-webhook-secret-key",
  "events": ["report.submitted", "report.failed"],
  "active": true
}
```

**Response**:
```json
{
  "id": 1,
  "url": "https://your-domain.com/webhooks/compliance",
  "events": ["report.submitted", "report.failed"],
  "active": true,
  "createdAt": "2026-04-30T13:00:00Z",
  "updatedAt": "2026-04-30T13:00:00Z"
}
```

### List Webhooks

**Endpoint**: `GET /api/v1/webhooks`

**Response**:
```json
{
  "webhooks": [
    {
      "id": 1,
      "url": "https://your-domain.com/webhooks/compliance",
      "events": ["report.submitted"],
      "active": true,
      "createdAt": "2026-04-30T13:00:00Z"
    }
  ]
}
```

### Update Webhook

**Endpoint**: `PUT /api/v1/webhooks/{id}`

**Request**:
```json
{
  "active": false
}
```

### Delete Webhook

**Endpoint**: `DELETE /api/v1/webhooks/{id}`

---

## Delivery Mechanics

### Request Format

When an event occurs, the system sends an HTTP POST request to your webhook URL:

**Headers**:
```
Content-Type: application/json
X-Event-Type: report.submitted
X-Delivery-ID: 12345
X-Webhook-Signature: base64-encoded-hmac-signature
```

**Body**: JSON payload (see Event Types above)

### Expected Response

Your webhook endpoint should:

1. **Respond quickly** (< 5 seconds)
2. **Return 2xx status code** for success
3. **Return 4xx/5xx** for failures (triggers retry)

**Example Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "received": true,
  "processedAt": "2026-04-30T13:00:01Z"
}
```

---

## Retry Logic

### Retry Strategy

Failed deliveries are automatically retried with **exponential backoff**:

| Attempt | Delay | Total Time |
|---------|-------|------------|
| 1 | Immediate | 0 min |
| 2 | 1 minute | 1 min |
| 3 | 2 minutes | 3 min |
| 4 | 4 minutes | 7 min |
| 5 | 8 minutes | 15 min |

**Maximum Attempts**: 5 (configurable)

### Retry Triggers

Retries occur when:
- HTTP status code is not 2xx
- Connection timeout (5 seconds default)
- Network errors
- DNS resolution failures

### Retry Processing

- Retries are processed every **60 seconds** by a scheduled task
- Up to **100 retries** processed per batch
- Failed deliveries after max attempts are marked as `FAILED`

---

## Security

### HMAC Signature Verification

Every webhook delivery includes an HMAC-SHA256 signature for verification.

#### Generating the Signature (Server-Side)

```java
Mac mac = Mac.getInstance("HmacSHA256");
SecretKeySpec secretKey = new SecretKeySpec(
    secret.getBytes(StandardCharsets.UTF_8),
    "HmacSHA256"
);
mac.init(secretKey);
byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
String signature = Base64.getEncoder().encodeToString(signatureBytes);
```

#### Verifying the Signature (Client-Side)

**Python Example**:
```python
import hmac
import hashlib
import base64

def verify_webhook_signature(payload, signature, secret):
    expected_signature = base64.b64encode(
        hmac.new(
            secret.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).digest()
    ).decode('utf-8')
    
    return hmac.compare_digest(signature, expected_signature)

# Usage
signature = request.headers.get('X-Webhook-Signature')
payload = request.get_data(as_text=True)
secret = 'your-webhook-secret-key'

if verify_webhook_signature(payload, signature, secret):
    # Process webhook
    pass
else:
    # Reject webhook
    return 401
```

**Node.js Example**:
```javascript
const crypto = require('crypto');

function verifyWebhookSignature(payload, signature, secret) {
    const expectedSignature = crypto
        .createHmac('sha256', secret)
        .update(payload)
        .digest('base64');
    
    return crypto.timingSafeEqual(
        Buffer.from(signature),
        Buffer.from(expectedSignature)
    );
}

// Usage
const signature = req.headers['x-webhook-signature'];
const payload = JSON.stringify(req.body);
const secret = 'your-webhook-secret-key';

if (verifyWebhookSignature(payload, signature, secret)) {
    // Process webhook
} else {
    // Reject webhook
    res.status(401).send('Invalid signature');
}
```

### Best Practices

1. **Always verify signatures** before processing webhooks
2. **Use HTTPS** for webhook URLs
3. **Keep secrets secure** (environment variables, secret managers)
4. **Rotate secrets periodically**
5. **Implement rate limiting** on your webhook endpoint
6. **Log all webhook deliveries** for audit trails

---

## Monitoring

### Delivery Statistics

**Endpoint**: `GET /api/v1/webhooks/{id}/stats`

**Response**:
```json
{
  "webhookId": 1,
  "url": "https://your-domain.com/webhooks/compliance",
  "active": true,
  "totalDeliveries": 150,
  "successfulDeliveries": 145,
  "failedDeliveries": 5,
  "retryingDeliveries": 0,
  "successRate": 96.67,
  "lastDeliveryAt": "2026-04-30T13:00:00Z"
}
```

### Delivery History

**Endpoint**: `GET /api/v1/webhooks/{id}/deliveries`

**Response**:
```json
{
  "deliveries": [
    {
      "id": 12345,
      "eventType": "report.submitted",
      "status": "SUCCESS",
      "responseCode": 200,
      "attemptCount": 1,
      "deliveredAt": "2026-04-30T13:00:01Z",
      "createdAt": "2026-04-30T13:00:00Z"
    },
    {
      "id": 12344,
      "eventType": "report.failed",
      "status": "FAILED",
      "responseCode": 500,
      "attemptCount": 5,
      "createdAt": "2026-04-30T12:00:00Z"
    }
  ]
}
```

---

## Configuration

### Environment Variables

```bash
# Enable/disable webhook delivery
WEBHOOK_DELIVERY_ENABLED=true

# Delivery timeout in milliseconds
WEBHOOK_DELIVERY_TIMEOUT=5000

# Maximum retry attempts
WEBHOOK_MAX_RETRY_ATTEMPTS=5

# Initial retry delay in minutes
WEBHOOK_INITIAL_RETRY_DELAY=1
```

### Application Configuration

**application.yml**:
```yaml
webhook:
  delivery:
    enabled: true
    timeout: 5000
    max-retry-attempts: 5
    initial-retry-delay-minutes: 1
```

---

## Examples

### Complete Webhook Endpoint (Python/Flask)

```python
from flask import Flask, request, jsonify
import hmac
import hashlib
import base64

app = Flask(__name__)
WEBHOOK_SECRET = 'your-webhook-secret-key'

@app.route('/webhooks/compliance', methods=['POST'])
def handle_compliance_webhook():
    # Verify signature
    signature = request.headers.get('X-Webhook-Signature')
    payload = request.get_data(as_text=True)
    
    if not verify_signature(payload, signature):
        return jsonify({'error': 'Invalid signature'}), 401
    
    # Get event details
    event_type = request.headers.get('X-Event-Type')
    delivery_id = request.headers.get('X-Delivery-ID')
    data = request.get_json()
    
    # Process event
    if event_type == 'report.submitted':
        handle_report_submitted(data)
    elif event_type == 'report.failed':
        handle_report_failed(data)
    elif event_type == 'leaderboard.updated':
        handle_leaderboard_updated(data)
    
    return jsonify({
        'received': True,
        'deliveryId': delivery_id,
        'processedAt': datetime.utcnow().isoformat()
    }), 200

def verify_signature(payload, signature):
    expected = base64.b64encode(
        hmac.new(
            WEBHOOK_SECRET.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).digest()
    ).decode('utf-8')
    return hmac.compare_digest(signature, expected)

def handle_report_submitted(data):
    print(f"Report {data['reportId']} submitted: {data['complianceScore']}%")
    # Your business logic here

def handle_report_failed(data):
    print(f"Report {data['reportId']} failed with score: {data['complianceScore']}%")
    # Send alert, create ticket, etc.

def handle_leaderboard_updated(data):
    print("Leaderboard updated, refreshing cache...")
    # Refresh leaderboard cache

if __name__ == '__main__':
    app.run(port=5000)
```

---

## Troubleshooting

### Common Issues

#### 1. Webhooks Not Delivering

**Symptoms**: No webhook deliveries received

**Solutions**:
- Check `WEBHOOK_DELIVERY_ENABLED=true`
- Verify webhook is `active: true`
- Check webhook URL is accessible from server
- Review application logs for errors

#### 2. Signature Verification Failing

**Symptoms**: 401 errors, signature mismatch

**Solutions**:
- Ensure secret matches on both sides
- Verify payload is not modified before verification
- Check character encoding (UTF-8)
- Use timing-safe comparison

#### 3. Deliveries Timing Out

**Symptoms**: All deliveries fail with timeout

**Solutions**:
- Increase `WEBHOOK_DELIVERY_TIMEOUT`
- Optimize webhook endpoint response time
- Process webhooks asynchronously
- Return 200 immediately, process later

#### 4. Too Many Retries

**Symptoms**: Excessive retry attempts

**Solutions**:
- Fix webhook endpoint errors
- Return proper HTTP status codes
- Implement idempotency in webhook handler
- Temporarily disable webhook if needed

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    io.substrait.compliance.api.service.WebhookDeliveryService: DEBUG
```

---

## API Reference

### Webhook Management Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/webhooks` | Register new webhook |
| GET | `/api/v1/webhooks` | List all webhooks |
| GET | `/api/v1/webhooks/{id}` | Get webhook details |
| PUT | `/api/v1/webhooks/{id}` | Update webhook |
| DELETE | `/api/v1/webhooks/{id}` | Delete webhook |
| GET | `/api/v1/webhooks/{id}/stats` | Get delivery statistics |
| GET | `/api/v1/webhooks/{id}/deliveries` | Get delivery history |

---

## Support

For issues or questions:
- GitHub Issues: [substrait-compliance/issues](https://github.com/substrait-io/substrait-compliance/issues)
- Documentation: [Main README](../README.md)
- API Docs: http://localhost:8080/swagger-ui.html

---

**Version**: 1.0.0  
**Last Updated**: 2026-04-30  
**Status**: Production Ready ✅