# REST API Architecture Diagram

## System Architecture Overview

```mermaid
graph TB
    subgraph "Client Layer"
        CLI[CLI Tools]
        CICD[CI/CD Pipelines]
        DASH[Dashboard]
        THIRD[Third-party Apps]
    end

    subgraph "API Gateway Layer"
        LB[Load Balancer]
        RATE[Rate Limiter]
    end

    subgraph "Spring Boot Application"
        subgraph "Security Layer"
            JWT[JWT Filter]
            AUTH[Auth Service]
        end

        subgraph "Controller Layer"
            RC[Report Controller]
            QC[Query Controller]
            WC[Webhook Controller]
            AC[Auth Controller]
        end

        subgraph "Service Layer"
            RS[Report Service]
            QS[Query Service]
            WS[Webhook Service]
            CS[Cache Service]
            RLS[Rate Limit Service]
        end

        subgraph "Repository Layer"
            RR[Report Repository]
            ER[Engine Repository]
            TR[Test Result Repository]
            WR[Webhook Repository]
        end

        subgraph "Integration Layer"
            WP[Webhook Publisher]
            WD[Webhook Delivery]
        end
    end

    subgraph "Data Layer"
        PG[(PostgreSQL)]
        CACHE[(Caffeine Cache)]
    end

    subgraph "External Systems"
        WEBHOOK[Webhook Endpoints]
    end

    CLI --> LB
    CICD --> LB
    DASH --> LB
    THIRD --> LB

    LB --> RATE
    RATE --> JWT

    JWT --> RC
    JWT --> QC
    JWT --> WC
    JWT --> AC

    RC --> RS
    QC --> QS
    WC --> WS
    AC --> AUTH

    RS --> RR
    RS --> CS
    QS --> QS
    QS --> CS
    WS --> WR

    RR --> PG
    ER --> PG
    TR --> PG
    WR --> PG

    CS --> CACHE

    RS --> WP
    WP --> WD
    WD --> WEBHOOK

    style CLI fill:#e1f5ff
    style CICD fill:#e1f5ff
    style DASH fill:#e1f5ff
    style THIRD fill:#e1f5ff
    style PG fill:#ffe1e1
    style CACHE fill:#ffe1e1
    style WEBHOOK fill:#e1ffe1
```

## Request Flow Diagram

```mermaid
sequenceDiagram
    participant Client
    participant API Gateway
    participant JWT Filter
    participant Controller
    participant Service
    participant Repository
    participant Database
    participant Webhook

    Client->>API Gateway: POST /api/v1/reports
    API Gateway->>API Gateway: Check Rate Limit
    API Gateway->>JWT Filter: Forward Request
    JWT Filter->>JWT Filter: Validate JWT Token
    JWT Filter->>Controller: Authenticated Request
    Controller->>Controller: Validate Request Body
    Controller->>Service: Process Report
    Service->>Repository: Save Report
    Repository->>Database: INSERT INTO compliance_reports
    Database-->>Repository: Report ID
    Repository-->>Service: Report Entity
    Service->>Service: Check Cache
    Service->>Webhook: Trigger Event
    Webhook->>Webhook: Async Delivery
    Service-->>Controller: Report Response
    Controller-->>Client: 201 Created
    Webhook-->>External: POST webhook payload
```

## Database Entity Relationship Diagram

```mermaid
erDiagram
    ENGINES ||--o{ COMPLIANCE_REPORTS : has
    COMPLIANCE_REPORTS ||--o{ TEST_RESULTS : contains
    WEBHOOKS ||--o{ WEBHOOK_DELIVERIES : generates
    API_KEYS ||--o{ COMPLIANCE_REPORTS : authenticates

    ENGINES {
        bigint id PK
        varchar name
        varchar version
        varchar vendor
        varchar substrait_version
        timestamp created_at
        timestamp updated_at
    }

    COMPLIANCE_REPORTS {
        bigint id PK
        bigint engine_id FK
        varchar test_suite_name
        bigint timestamp
        int total_tests
        int passed_count
        int failed_count
        int skipped_count
        decimal compliance_score
        bigint execution_time_ms
        jsonb metadata
        timestamp created_at
    }

    TEST_RESULTS {
        bigint id PK
        bigint report_id FK
        varchar test_id
        varchar status
        text message
        bigint duration_ms
        timestamp created_at
    }

    WEBHOOKS {
        bigint id PK
        varchar url
        varchar secret
        varchar[] events
        boolean active
        varchar created_by
        timestamp created_at
        timestamp updated_at
    }

    WEBHOOK_DELIVERIES {
        bigint id PK
        bigint webhook_id FK
        varchar event_type
        jsonb payload
        varchar status
        int response_code
        text response_body
        int attempt_count
        timestamp delivered_at
        timestamp created_at
    }

    API_KEYS {
        bigint id PK
        varchar key_hash
        varchar name
        text description
        varchar[] scopes
        int rate_limit
        boolean active
        timestamp expires_at
        varchar created_by
        timestamp created_at
        timestamp last_used_at
    }
```

## Component Interaction Diagram

```mermaid
graph LR
    subgraph "API Module"
        A[Controllers]
        B[Services]
        C[Repositories]
    end

    subgraph "SDK Module"
        D[ComplianceReport]
        E[ComplianceResult]
        F[TestResult]
        G[EngineInfo]
    end

    subgraph "External Dependencies"
        H[Spring Boot]
        I[Spring Security]
        J[Spring Data JPA]
        K[PostgreSQL Driver]
    end

    A --> B
    B --> C
    B --> D
    B --> E
    B --> F
    B --> G

    A --> H
    A --> I
    C --> J
    C --> K

    style A fill:#bbdefb
    style B fill:#c5e1a5
    style C fill:#ffccbc
    style D fill:#f8bbd0
    style E fill:#f8bbd0
    style F fill:#f8bbd0
    style G fill:#f8bbd0
```

## Deployment Architecture

```mermaid
graph TB
    subgraph "Container Orchestration"
        subgraph "API Container"
            APP[Spring Boot App]
            JVM[JVM 21]
        end

        subgraph "Database Container"
            PG[PostgreSQL 15]
            PGDATA[(Data Volume)]
        end
    end

    subgraph "External Services"
        GITHUB[GitHub Actions]
        REGISTRY[Container Registry]
    end

    GITHUB -->|Build & Push| REGISTRY
    REGISTRY -->|Pull Image| APP
    APP -->|JDBC| PG
    PG -->|Persist| PGDATA

    style APP fill:#4caf50
    style PG fill:#2196f3
    style GITHUB fill:#ff9800
    style REGISTRY fill:#9c27b0
```

## Security Flow

```mermaid
sequenceDiagram
    participant User
    participant Auth Controller
    participant Auth Service
    participant JWT Provider
    participant Database

    User->>Auth Controller: POST /auth/login
    Auth Controller->>Auth Service: authenticate(credentials)
    Auth Service->>Database: Verify credentials
    Database-->>Auth Service: User valid
    Auth Service->>JWT Provider: generateToken(user)
    JWT Provider-->>Auth Service: JWT Token
    Auth Service-->>Auth Controller: Token + Expiry
    Auth Controller-->>User: 200 OK + Token

    Note over User: Store token securely

    User->>API: GET /reports (with JWT)
    API->>JWT Provider: validateToken(jwt)
    JWT Provider-->>API: Token valid + Claims
    API->>API: Check scopes
    API-->>User: 200 OK + Data
```

## Caching Strategy

```mermaid
graph LR
    A[Request] --> B{Cache Hit?}
    B -->|Yes| C[Return Cached Data]
    B -->|No| D[Query Database]
    D --> E[Store in Cache]
    E --> F[Return Data]

    G[New Report] --> H[Invalidate Cache]
    H --> I[Update Leaderboard]

    style B fill:#ffeb3b
    style C fill:#4caf50
    style D fill:#2196f3
    style H fill:#f44336
```

## Webhook Delivery Flow

```mermaid
stateDiagram-v2
    [*] --> Pending: Event Triggered
    Pending --> Delivering: Attempt Delivery
    Delivering --> Success: HTTP 2xx
    Delivering --> Retry: HTTP 5xx/Timeout
    Retry --> Delivering: Retry (max 3)
    Retry --> Failed: Max Retries
    Success --> [*]
    Failed --> [*]

    note right of Retry
        Exponential backoff:
        1s, 2s, 4s
    end note
```

## Rate Limiting Algorithm

```mermaid
graph TD
    A[Request] --> B{Token Available?}
    B -->|Yes| C[Consume Token]
    B -->|No| D[Return 429]
    C --> E[Process Request]
    E --> F[Return Response]

    G[Time Window] --> H[Refill Tokens]
    H --> B

    style B fill:#ffeb3b
    style D fill:#f44336
    style E fill:#4caf50
```

## API Versioning Strategy

```mermaid
graph LR
    A[Client Request] --> B{Version Header?}
    B -->|v1| C[API v1]
    B -->|v2| D[API v2]
    B -->|None| E[Latest Version]

    C --> F[Legacy Support]
    D --> G[Current Features]
    E --> G

    style C fill:#ffccbc
    style D fill:#c5e1a5
    style E fill:#bbdefb
```

## Monitoring & Observability

```mermaid
graph TB
    subgraph "Application"
        APP[Spring Boot API]
        METRICS[Micrometer Metrics]
    end

    subgraph "Monitoring Stack"
        PROM[Prometheus]
        GRAF[Grafana]
        ALERT[Alertmanager]
    end

    subgraph "Logging Stack"
        LOGS[Application Logs]
        ELK[ELK Stack]
    end

    APP --> METRICS
    METRICS --> PROM
    PROM --> GRAF
    PROM --> ALERT

    APP --> LOGS
    LOGS --> ELK

    style APP fill:#4caf50
    style PROM fill:#e6522c
    style GRAF fill:#f46800
    style ELK fill:#00bfb3
```

---

**Document Version:** 1.0  
**Last Updated:** 2026-04-16  
**Purpose:** Visual architecture reference for REST API implementation