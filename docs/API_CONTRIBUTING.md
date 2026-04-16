# Contributing to Substrait Compliance REST API

Thank you for your interest in contributing to the Substrait Compliance REST API! This document provides guidelines for contributing to the API component of the Substrait Compliance Framework.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Community](#community)

## 🤝 Code of Conduct

This project adheres to the Substrait Community Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

### Our Standards

- **Be Respectful**: Treat everyone with respect and consideration
- **Be Collaborative**: Work together and help each other
- **Be Inclusive**: Welcome newcomers and diverse perspectives
- **Be Professional**: Focus on what is best for the community

## 🚀 Getting Started

### Prerequisites

- **JDK 11 or higher**
- **Gradle 7.x or higher**
- **PostgreSQL 15** (for local development)
- **Podman or Docker** (for containerized development)
- **Git**

### Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/substrait-compliance.git
   cd substrait-compliance
   ```
3. Add upstream remote:
   ```bash
   git remote add upstream https://github.com/substrait-io/substrait-compliance.git
   ```

## 💻 Development Setup

### Local Development

1. **Build the SDK** (required dependency):
   ```bash
   cd sdk/java
   ./gradlew build publishToMavenLocal
   ```

2. **Set up PostgreSQL**:
   ```bash
   # Using Podman
   podman run -d \
     --name substrait-postgres \
     -e POSTGRES_DB=substrait_compliance \
     -e POSTGRES_USER=substrait \
     -e POSTGRES_PASSWORD=password \
     -p 5432:5432 \
     postgres:15-alpine
   ```

3. **Build the API**:
   ```bash
   cd api
   ./gradlew build
   ```

4. **Run the API**:
   ```bash
   ./gradlew bootRun
   ```

5. **Access the API**:
   - API: http://localhost:8080/api/v1
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health: http://localhost:8080/actuator/health

### Using Docker Compose

```bash
cd api
podman-compose up -d
```

## 🛠️ How to Contribute

### Types of Contributions

We welcome various types of contributions:

1. **Bug Fixes**: Fix issues in existing functionality
2. **New Features**: Add new API endpoints or capabilities
3. **Documentation**: Improve API documentation, guides, or examples
4. **Tests**: Add or improve test coverage
5. **Performance**: Optimize API performance
6. **Security**: Enhance security measures

### Finding Issues to Work On

- Check the [Issues](https://github.com/substrait-io/substrait-compliance/issues) page
- Look for issues labeled `good-first-issue` or `help-wanted`
- Issues labeled `api` are specific to the REST API component

### Before You Start

1. **Check existing issues**: Ensure the issue hasn't been reported
2. **Create an issue**: For new features, create an issue to discuss first
3. **Assign yourself**: Comment on the issue to let others know you're working on it
4. **Create a branch**: Use a descriptive branch name
   ```bash
   git checkout -b feature/add-batch-report-endpoint
   git checkout -b fix/rate-limit-bug
   git checkout -b docs/improve-api-guide
   ```

## 📝 Coding Standards

### Java Code Style

We follow standard Java conventions with some specific guidelines:

#### General Guidelines

- **Java Version**: Target Java 11
- **Formatting**: Use 4 spaces for indentation (no tabs)
- **Line Length**: Maximum 120 characters
- **Naming Conventions**:
  - Classes: `PascalCase`
  - Methods/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: `lowercase`

#### Spring Boot Conventions

```java
// Controller example
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Compliance report endpoints")
public class ReportController {
    
    private final ReportService reportService;
    
    // Constructor injection (preferred)
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }
    
    @PostMapping
    @Operation(summary = "Submit a compliance report")
    public ResponseEntity<ReportResponse> submitReport(
            @Valid @RequestBody ReportSubmissionRequest request) {
        // Implementation
    }
}
```

#### Service Layer

```java
@Service
public class ReportService {
    
    private final ReportRepository repository;
    
    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }
    
    @Transactional
    @CacheEvict(value = "leaderboard", allEntries = true)
    public ReportResponse submitReport(ReportSubmissionRequest request) {
        // Implementation
    }
}
```

#### Error Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
```

### Documentation Standards

#### JavaDoc

All public APIs must have JavaDoc:

```java
/**
 * Submits a compliance report to the system.
 * 
 * @param request the report submission request containing engine info and test results
 * @return the submitted report with generated ID and summary
 * @throws ValidationException if the request is invalid
 * @throws DuplicateReportException if a report with the same signature exists
 */
public ReportResponse submitReport(ReportSubmissionRequest request) {
    // Implementation
}
```

#### OpenAPI Annotations

Use comprehensive OpenAPI annotations:

```java
@Operation(
    summary = "Submit a compliance report",
    description = "Submits a new compliance report with test results. " +
                  "The report will be validated and stored in the database.",
    responses = {
        @ApiResponse(
            responseCode = "201",
            description = "Report successfully submitted",
            content = @Content(schema = @Schema(implementation = ReportResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    }
)
```

## 🧪 Testing Guidelines

### Test Coverage Requirements

- **Minimum Coverage**: 80% line coverage
- **Critical Paths**: 100% coverage for security and data integrity
- **All Public APIs**: Must have unit tests

### Test Structure

```
api/src/test/java/
├── io/substrait/compliance/api/
│   ├── controller/          # Controller tests with MockMvc
│   ├── service/             # Service tests with Mockito
│   ├── repository/          # Repository tests with H2
│   └── integration/         # Integration tests with TestContainers
```

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    
    @Mock
    private ReportRepository repository;
    
    @Mock
    private WebhookPublisher webhookPublisher;
    
    @InjectMocks
    private ReportService service;
    
    @Test
    void submitReport_shouldSaveAndPublishEvent() {
        // Given
        ReportSubmissionRequest request = createValidRequest();
        ReportEntity entity = new ReportEntity();
        when(repository.save(any())).thenReturn(entity);
        
        // When
        ReportResponse response = service.submitReport(request);
        
        // Then
        assertNotNull(response);
        verify(repository).save(any());
        verify(webhookPublisher).publishReportSubmitted(entity);
    }
}
```

### Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportApiIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:15-alpine");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void submitReport_shouldReturn201() throws Exception {
        String requestJson = """
            {
              "engineInfo": {
                "name": "TestEngine",
                "version": "1.0.0"
              },
              "testSuiteName": "tpch",
              "testResults": []
            }
            """;
        
        mockMvc.perform(post("/api/v1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").exists());
    }
}
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Run integration tests only
./gradlew integrationTest

# Run specific test class
./gradlew test --tests ReportServiceTest
```

## 🔄 Pull Request Process

### Before Submitting

1. **Update your branch**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests**:
   ```bash
   ./gradlew clean build test
   ```

3. **Check code style**:
   ```bash
   ./gradlew checkstyleMain checkstyleTest
   ```

4. **Update documentation**: If you changed APIs, update:
   - JavaDoc comments
   - OpenAPI annotations
   - README.md (if applicable)
   - API_CONTRIBUTING.md (if process changed)

### PR Guidelines

#### Title Format

Use conventional commit format:
- `feat(api): add batch report submission endpoint`
- `fix(security): resolve JWT token validation issue`
- `docs(api): improve webhook documentation`
- `test(service): add tests for rate limiting`
- `refactor(controller): simplify error handling`

#### Description Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] Tests pass locally
- [ ] No new warnings introduced

## Related Issues
Closes #123
```

### Review Process

1. **Automated Checks**: CI/CD will run tests and checks
2. **Code Review**: At least one maintainer will review
3. **Feedback**: Address review comments
4. **Approval**: Once approved, maintainers will merge

### After Merge

- Delete your branch
- Update your fork:
  ```bash
  git checkout main
  git pull upstream main
  git push origin main
  ```

## 🏗️ Architecture Guidelines

### Adding New Endpoints

1. **Define DTO**: Create request/response DTOs in `model/dto/`
2. **Create Service**: Implement business logic in `service/`
3. **Add Controller**: Create REST endpoint in `controller/`
4. **Add Tests**: Write unit and integration tests
5. **Document**: Add OpenAPI annotations

### Database Changes

1. **Create Migration**: Add Flyway migration in `resources/db/migration/`
2. **Update Entity**: Modify or create JPA entity
3. **Update Repository**: Add repository methods if needed
4. **Test Migration**: Ensure migration works on clean database

### Adding Dependencies

1. **Check Compatibility**: Ensure Java 11 compatibility
2. **Update build.gradle**: Add dependency with version
3. **Document**: Update README if it affects setup
4. **License Check**: Ensure dependency license is compatible

## 🐛 Reporting Bugs

### Bug Report Template

```markdown
**Describe the bug**
Clear description of the bug

**To Reproduce**
Steps to reproduce:
1. Send POST request to /api/v1/reports
2. With payload: {...}
3. Observe error

**Expected behavior**
What should happen

**Actual behavior**
What actually happens

**Environment**
- API Version: 1.0.0
- Java Version: 11
- Database: PostgreSQL 15
- OS: Ubuntu 22.04

**Logs**
```
Relevant log output
```

**Additional context**
Any other relevant information
```

## 💡 Feature Requests

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
Description of the problem

**Describe the solution you'd like**
Clear description of desired functionality

**Describe alternatives you've considered**
Other approaches you've thought about

**Additional context**
Mockups, examples, or references

**Would you like to implement this?**
- [ ] Yes, I can work on this
- [ ] No, but I can help test
- [ ] No, just suggesting
```

## 📚 Resources

### Documentation

- [REST API Plan](REST_API_PLAN.md)
- [Architecture Diagrams](REST_API_ARCHITECTURE.md)
- [Implementation Guide](REST_API_IMPLEMENTATION_GUIDE.md)
- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Substrait Specification](https://substrait.io/)

### Communication

- **GitHub Issues**: For bugs and features
- **GitHub Discussions**: For questions and ideas
- **Substrait Slack**: For real-time chat (if available)

## 🙏 Recognition

Contributors will be recognized in:
- CONTRIBUTORS.md file
- Release notes
- Project README

Thank you for contributing to the Substrait Compliance Framework! 🎉

---

**License**: Apache 2.0  
**Maintainers**: See MAINTAINERS.md  
**Last Updated**: 2026-04-16