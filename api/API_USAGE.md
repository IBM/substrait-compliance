# Substrait Compliance API - Usage Guide

## Table of Contents
1. [Authentication](#authentication)
2. [API Endpoints](#api-endpoints)
3. [Request Examples](#request-examples)
4. [Response Format](#response-format)
5. [Error Handling](#error-handling)
6. [Rate Limiting](#rate-limiting)
7. [Webhooks](#webhooks)
8. [Client Libraries](#client-libraries)

---

## Authentication

The API uses JWT (JSON Web Token) authentication with scope-based authorization.

### Obtaining a Token

Contact your API administrator to obtain an API key. Tokens are generated server-side.

### Using the Token

Include the JWT token in the `Authorization` header:

```http
Authorization: Bearer <your-jwt-token>
```

### Token Scopes

| Scope | Permissions |
|-------|-------------|
| `read` | Query reports and view data |
| `write` | Submit new reports |
| `admin` | Full access including user management |

### Token Expiration

Tokens expire after 24 hours by default. Request a new token when expired.

---

## API Endpoints

### Base URL

```
http://localhost:8080/api/v1
```

### Available Endpoints

| Method | Endpoint | Scope | Description |
|--------|----------|-------|-------------|
| POST | `/reports` | write | Submit a compliance report |
| GET | `/reports` | read | Query reports with pagination |
| GET | `/reports/{id}` | read | Get a specific report |
| GET | `/reports/engine/{name}/history` | read | Get engine compliance history |

### OpenAPI Documentation

Interactive API documentation available at:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## Request Examples

### 1. Submit a Compliance Report

**Endpoint:** `POST /api/v1/reports`

**Request:**

```bash
curl -X POST http://localhost:8080/api/v1/reports \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "engineInfo": {
      "name": "MyEngine",
      "version": "1.0.0",
      "vendor": "MyCompany",
      "substraitVersion": "0.20.0"
    },
    "testSuiteName": "arithmetic_functions",
    "timestamp": 1713235200000,
    "testResults": [
      {
        "testId": "add_int32",
        "status": "PASSED",
        "message": "Test passed successfully",
        "durationMs": 150
      },
      {
        "testId": "subtract_int32",
        "status": "PASSED",
        "message": "Test passed successfully",
        "durationMs": 120
      },
      {
        "testId": "multiply_int32",
        "status": "FAILED",
        "message": "Expected 6 but got 5",
        "durationMs": 200
      }
    ],
    "metadata": {
      "environment": "ci",
      "commit": "abc123def456",
      "branch": "main"
    }
  }'
```

**Response:** `201 Created`

```json
{
  "reportId": 123,
  "engineInfo": {
    "name": "MyEngine",
    "version": "1.0.0",
    "vendor": "MyCompany",
    "substraitVersion": "0.20.0"
  },
  "testSuiteName": "arithmetic_functions",
  "timestamp": "2024-04-16T10:00:00Z",
  "summary": {
    "totalTests": 3,
    "passed": 2,
    "failed": 1,
    "skipped": 0,
    "complianceScore": 66.67,
    "executionTimeMs": 470
  },
  "metadata": {
    "environment": "ci",
    "commit": "abc123def456",
    "branch": "main"
  },
  "createdAt": "2024-04-16T10:00:05Z"
}
```

### 2. Query Reports with Pagination

**Endpoint:** `GET /api/v1/reports`

**Request:**

```bash
curl -X GET "http://localhost:8080/api/v1/reports?page=0&size=20&sort=timestamp,desc" \
  -H "Authorization: Bearer <token>"
```

**Response:** `200 OK`

```json
{
  "content": [
    {
      "reportId": 123,
      "engineInfo": {
        "name": "MyEngine",
        "version": "1.0.0"
      },
      "testSuiteName": "arithmetic_functions",
      "summary": {
        "totalTests": 3,
        "passed": 2,
        "failed": 1,
        "complianceScore": 66.67
      },
      "timestamp": "2024-04-16T10:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false
    }
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "numberOfElements": 1
}
```

### 3. Get Specific Report

**Endpoint:** `GET /api/v1/reports/{id}`

**Request:**

```bash
curl -X GET http://localhost:8080/api/v1/reports/123 \
  -H "Authorization: Bearer <token>"
```

**Response:** `200 OK`

```json
{
  "reportId": 123,
  "engineInfo": {
    "name": "MyEngine",
    "version": "1.0.0",
    "vendor": "MyCompany",
    "substraitVersion": "0.20.0"
  },
  "testSuiteName": "arithmetic_functions",
  "timestamp": "2024-04-16T10:00:00Z",
  "summary": {
    "totalTests": 3,
    "passed": 2,
    "failed": 1,
    "skipped": 0,
    "complianceScore": 66.67,
    "executionTimeMs": 470
  },
  "metadata": {
    "environment": "ci",
    "commit": "abc123def456"
  },
  "createdAt": "2024-04-16T10:00:05Z"
}
```

### 4. Get Engine History

**Endpoint:** `GET /api/v1/reports/engine/{name}/history`

**Request:**

```bash
curl -X GET http://localhost:8080/api/v1/reports/engine/MyEngine/history \
  -H "Authorization: Bearer <token>"
```

**Response:** `200 OK`

```json
[
  {
    "reportId": 123,
    "engineInfo": {
      "name": "MyEngine",
      "version": "1.0.0"
    },
    "testSuiteName": "arithmetic_functions",
    "summary": {
      "complianceScore": 66.67
    },
    "timestamp": "2024-04-16T10:00:00Z"
  },
  {
    "reportId": 122,
    "engineInfo": {
      "name": "MyEngine",
      "version": "1.0.0"
    },
    "testSuiteName": "string_functions",
    "summary": {
      "complianceScore": 85.50
    },
    "timestamp": "2024-04-15T10:00:00Z"
  }
]
```

---

## Response Format

### Success Responses

| Status Code | Description |
|-------------|-------------|
| 200 OK | Request successful |
| 201 Created | Resource created successfully |
| 204 No Content | Request successful, no content to return |

### Error Responses

| Status Code | Description |
|-------------|-------------|
| 400 Bad Request | Invalid request format or validation error |
| 401 Unauthorized | Missing or invalid authentication token |
| 403 Forbidden | Insufficient permissions |
| 404 Not Found | Resource not found |
| 429 Too Many Requests | Rate limit exceeded |
| 500 Internal Server Error | Server error |

---

## Error Handling

### Error Response Format

```json
{
  "timestamp": "2024-04-16T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for field 'engineInfo.name': must not be null",
  "path": "/api/v1/reports"
}
```

### Common Errors

#### 1. Validation Error (400)

```json
{
  "timestamp": "2024-04-16T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "engineInfo.name",
      "message": "must not be null"
    },
    {
      "field": "testResults",
      "message": "must not be empty"
    }
  ]
}
```

#### 2. Authentication Error (401)

```json
{
  "timestamp": "2024-04-16T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token"
}
```

#### 3. Authorization Error (403)

```json
{
  "timestamp": "2024-04-16T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Insufficient permissions. Required scope: write"
}
```

#### 4. Rate Limit Error (429)

```json
{
  "timestamp": "2024-04-16T10:00:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 60 seconds"
}
```

---

## Rate Limiting

### Rate Limit Headers

Every response includes rate limit information:

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1713238800
```

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Maximum requests allowed in the window |
| `X-RateLimit-Remaining` | Requests remaining in current window |
| `X-RateLimit-Reset` | Unix timestamp when the limit resets |

### Default Limits

- **1000 requests per hour** per API key
- Limits are per-user/API key
- Configurable by administrators

### Handling Rate Limits

```python
import requests
import time

def submit_report_with_retry(report_data, token, max_retries=3):
    url = "http://localhost:8080/api/v1/reports"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    for attempt in range(max_retries):
        response = requests.post(url, json=report_data, headers=headers)
        
        if response.status_code == 429:
            # Rate limit exceeded
            reset_time = int(response.headers.get('X-RateLimit-Reset', 0))
            wait_time = max(reset_time - time.time(), 0) + 1
            print(f"Rate limit exceeded. Waiting {wait_time} seconds...")
            time.sleep(wait_time)
            continue
        
        return response
    
    raise Exception("Max retries exceeded")
```

---

## Webhooks

### Registering a Webhook

Webhooks are registered via database (admin access required):

```sql
INSERT INTO webhooks (url, secret, event_types, active)
VALUES (
  'https://your-server.com/webhook',
  'your-webhook-secret',
  '["report.submitted", "report.failed"]'::jsonb,
  true
);
```

### Webhook Events

| Event Type | Description | Trigger |
|------------|-------------|---------|
| `report.submitted` | New report submitted | After successful report submission |
| `report.failed` | Report with low compliance | When compliance score < threshold |
| `leaderboard.updated` | Leaderboard changed | After report affects rankings |

### Webhook Payload

```json
{
  "eventType": "report.submitted",
  "timestamp": "2024-04-16T10:00:00Z",
  "payload": {
    "reportId": 123,
    "engineName": "MyEngine",
    "engineVersion": "1.0.0",
    "testSuiteName": "arithmetic_functions",
    "complianceScore": 66.67,
    "passed": 2,
    "failed": 1,
    "skipped": 0
  }
}
```

### Webhook Security

Verify webhook signatures:

```python
import hmac
import hashlib

def verify_webhook(payload, signature, secret):
    expected = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(signature, expected)

# In your webhook handler
@app.route('/webhook', methods=['POST'])
def handle_webhook():
    signature = request.headers.get('X-Webhook-Signature')
    payload = request.get_data(as_text=True)
    
    if not verify_webhook(payload, signature, WEBHOOK_SECRET):
        return 'Invalid signature', 401
    
    # Process webhook
    data = request.json
    print(f"Received event: {data['eventType']}")
    return 'OK', 200
```

---

## Client Libraries

### Java Client

```java
import java.net.http.*;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SubstraitComplianceClient {
    private final String baseUrl;
    private final String token;
    private final HttpClient client;
    private final ObjectMapper mapper;
    
    public SubstraitComplianceClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }
    
    public ReportResponse submitReport(ReportSubmissionRequest request) 
            throws Exception {
        String json = mapper.writeValueAsString(request);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/reports"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        HttpResponse<String> response = client.send(
            httpRequest, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 201) {
            throw new RuntimeException("Failed: " + response.body());
        }
        
        return mapper.readValue(response.body(), ReportResponse.class);
    }
}

// Usage
SubstraitComplianceClient client = new SubstraitComplianceClient(
    "http://localhost:8080",
    "your-jwt-token"
);

ReportSubmissionRequest request = // ... build request
ReportResponse response = client.submitReport(request);
System.out.println("Report ID: " + response.getReportId());
```

### Python Client

```python
import requests
from typing import Dict, List, Optional

class SubstraitComplianceClient:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        })
    
    def submit_report(self, report_data: Dict) -> Dict:
        """Submit a compliance report."""
        response = self.session.post(
            f'{self.base_url}/api/v1/reports',
            json=report_data
        )
        response.raise_for_status()
        return response.json()
    
    def get_report(self, report_id: int) -> Dict:
        """Get a specific report."""
        response = self.session.get(
            f'{self.base_url}/api/v1/reports/{report_id}'
        )
        response.raise_for_status()
        return response.json()
    
    def query_reports(self, page: int = 0, size: int = 20) -> Dict:
        """Query reports with pagination."""
        response = self.session.get(
            f'{self.base_url}/api/v1/reports',
            params={'page': page, 'size': size}
        )
        response.raise_for_status()
        return response.json()
    
    def get_engine_history(self, engine_name: str) -> List[Dict]:
        """Get compliance history for an engine."""
        response = self.session.get(
            f'{self.base_url}/api/v1/reports/engine/{engine_name}/history'
        )
        response.raise_for_status()
        return response.json()

# Usage
client = SubstraitComplianceClient(
    'http://localhost:8080',
    'your-jwt-token'
)

report = {
    'engineInfo': {
        'name': 'MyEngine',
        'version': '1.0.0',
        'vendor': 'MyCompany',
        'substraitVersion': '0.20.0'
    },
    'testSuiteName': 'arithmetic_functions',
    'timestamp': 1713235200000,
    'testResults': [
        {
            'testId': 'add_int32',
            'status': 'PASSED',
            'message': 'Test passed',
            'durationMs': 150
        }
    ]
}

response = client.submit_report(report)
print(f"Report ID: {response['reportId']}")
print(f"Compliance Score: {response['summary']['complianceScore']}%")
```

### cURL Examples

```bash
# Set variables
export API_URL="http://localhost:8080"
export TOKEN="your-jwt-token"

# Submit report
curl -X POST "$API_URL/api/v1/reports" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @report.json

# Query reports
curl -X GET "$API_URL/api/v1/reports?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

# Get specific report
curl -X GET "$API_URL/api/v1/reports/123" \
  -H "Authorization: Bearer $TOKEN"

# Get engine history
curl -X GET "$API_URL/api/v1/reports/engine/MyEngine/history" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Best Practices

### 1. Error Handling

Always handle errors gracefully:

```python
try:
    response = client.submit_report(report_data)
except requests.exceptions.HTTPError as e:
    if e.response.status_code == 429:
        # Handle rate limit
        print("Rate limit exceeded")
    elif e.response.status_code == 401:
        # Handle authentication error
        print("Invalid token")
    else:
        # Handle other errors
        print(f"Error: {e}")
```

### 2. Retry Logic

Implement exponential backoff for transient errors:

```python
import time
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.retry import Retry

session = requests.Session()
retry = Retry(
    total=3,
    backoff_factor=1,
    status_forcelist=[500, 502, 503, 504]
)
adapter = HTTPAdapter(max_retries=retry)
session.mount('http://', adapter)
session.mount('https://', adapter)
```

### 3. Batch Operations

For multiple reports, submit them sequentially with rate limit awareness:

```python
def submit_reports_batch(reports, client, delay=1):
    results = []
    for report in reports:
        try:
            result = client.submit_report(report)
            results.append(result)
            time.sleep(delay)  # Respect rate limits
        except Exception as e:
            print(f"Failed to submit report: {e}")
    return results
```

### 4. Caching

Cache frequently accessed data:

```python
from functools import lru_cache
import time

@lru_cache(maxsize=100)
def get_report_cached(report_id, cache_time):
    # cache_time changes every 5 minutes
    return client.get_report(report_id)

# Usage
cache_key = int(time.time() / 300)  # 5-minute buckets
report = get_report_cached(123, cache_key)
```

---

## Support

- **API Documentation**: http://localhost:8080/swagger-ui.html
- **GitHub Issues**: https://github.com/substrait-io/substrait-compliance
- **Email**: support@substrait.io

---

*Last Updated: 2026-04-16*