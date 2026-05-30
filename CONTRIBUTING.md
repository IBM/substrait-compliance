# Contributing to Substrait Compliance Framework

Thank you for your interest in contributing to the Substrait Compliance Framework! This document provides guidelines and instructions for contributing to the project.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Commit Message Guidelines](#commit-message-guidelines)
- [Pull Request Process](#pull-request-process)
- [Community](#community)

## Code of Conduct

This project adheres to a Code of Conduct that all contributors are expected to follow. Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) before contributing.

## Getting Started

### Prerequisites

- Java 17 or higher
- Python 3.8+ (for Python SDK)
- Rust 1.70+ (for Rust SDK)
- Gradle 8.0+
- Git

### Setting Up Your Development Environment

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/substrait-compliance.git
   cd substrait-compliance
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/substrait-io/substrait-compliance.git
   ```
4. **Build the project**:
   ```bash
   ./gradlew build
   ```
5. **Run tests** to verify setup:
   ```bash
   ./gradlew test
   ```

## How to Contribute

### Areas for Contribution

We welcome contributions in the following areas:

1. **Test Suite Development**
   - Add new test cases to existing suites
   - Create new test suites (TPC-DS, SSB, etc.)
   - Improve test coverage for edge cases
   - Enhance test quality and documentation

2. **SDK Development**
   - Improve existing SDKs (Java, Python, Rust)
   - Add new language SDKs
   - Fix bugs and improve performance
   - Enhance documentation and examples

3. **Engine Integration**
   - Integrate new query engines
   - Improve existing integrations
   - Share compliance reports
   - Document integration experiences

4. **Documentation**
   - Improve existing documentation
   - Add tutorials and guides
   - Translate documentation
   - Fix typos and clarify content

5. **Tools and Automation**
   - Improve CI/CD workflows
   - Enhance quality checking tools
   - Add new analysis capabilities
   - Improve dashboard and visualization

6. **Bug Fixes**
   - Report bugs with detailed reproduction steps
   - Fix reported issues
   - Improve error handling and messages

## Development Workflow

### 1. Create a Branch

Create a feature branch from `main`:

```bash
git checkout main
git pull upstream main
git checkout -b feature/your-feature-name
```

Branch naming conventions:
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation changes
- `test/` - Test additions or modifications
- `refactor/` - Code refactoring

### 2. Make Your Changes

- Write clean, maintainable code
- Follow the coding standards (see below)
- Add tests for new functionality
- Update documentation as needed
- Keep commits focused and atomic

### 3. Test Your Changes

Run the full test suite:

```bash
# Java SDK tests
./gradlew test

# Python SDK tests
cd sdk/python
python -m pytest

# Rust SDK tests
cd sdk/rust
cargo test
```

### 4. Commit Your Changes

Follow the commit message guidelines (see below):

```bash
git add .
git commit -m "feat: add new test suite for window functions"
```

### 5. Push and Create Pull Request

```bash
git push origin feature/your-feature-name
```

Then create a pull request on GitHub.

## Coding Standards

### Java

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Keep methods focused and under 50 lines when possible
- Target Java 17 compatibility unless a change is explicitly scoped otherwise

Example:
```java
/**
 * Executes a compliance test against the engine.
 *
 * @param testCase the test case to execute
 * @return the test result
 * @throws ComplianceException if execution fails
 */
public TestResult executeTest(TestCase testCase) throws ComplianceException {
    // Implementation
}
```

### Python

- Follow [PEP 8](https://pep8.org/) style guide
- Use type hints for function signatures
- Add docstrings for all public functions and classes
- Use meaningful variable names
- Keep functions focused and under 50 lines

Example:
```python
def execute_test(test_case: TestCase) -> TestResult:
    """
    Execute a compliance test against the engine.
    
    Args:
        test_case: The test case to execute
        
    Returns:
        The test result
        
    Raises:
        ComplianceException: If execution fails
    """
    # Implementation
```

### Rust

- Follow [Rust API Guidelines](https://rust-lang.github.io/api-guidelines/)
- Use `rustfmt` for formatting
- Use `clippy` for linting
- Add documentation comments for public APIs
- Handle errors explicitly with `Result`

Example:
```rust
/// Executes a compliance test against the engine.
///
/// # Arguments
/// * `test_case` - The test case to execute
///
/// # Returns
/// The test result
///
/// # Errors
/// Returns `ComplianceError` if execution fails
pub fn execute_test(test_case: &TestCase) -> Result<TestResult, ComplianceError> {
    // Implementation
}
```

## Testing Guidelines

### Test Requirements

- All new features must include tests
- Bug fixes should include regression tests
- Aim for 80%+ code coverage
- Tests should be deterministic and reproducible
- Use descriptive test names

### Test Structure

```java
@Test
public void testExecuteTest_withValidInput_returnsSuccess() {
    // Arrange
    TestCase testCase = createValidTestCase();
    
    // Act
    TestResult result = engine.executeTest(testCase);
    
    // Assert
    assertEquals(TestStatus.PASSED, result.getStatus());
}
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests ComplianceEngineTest

# Run with coverage
./gradlew test jacocoTestReport
```

## Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks
- `perf`: Performance improvements
- `ci`: CI/CD changes

### Examples

```
feat(sdk): add Python async execution support

Implement async execution methods for the Python SDK to improve
performance when running multiple tests concurrently.

Closes #123
```

```
fix(test): correct expected result for string_split test

The expected result was incorrect for edge case with empty delimiter.
Updated test case and documentation.

Fixes #456
```

```
docs(readme): update installation instructions

Add missing prerequisites and clarify setup steps for Windows users.
```

## Pull Request Process

### Before Submitting

1. ✅ Ensure all tests pass
2. ✅ Update documentation if needed
3. ✅ Add tests for new functionality
4. ✅ Follow coding standards
5. ✅ Rebase on latest `main` branch
6. ✅ Write clear commit messages

### PR Description Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe testing performed

## Checklist
- [ ] Tests pass locally
- [ ] Code follows style guidelines
- [ ] Documentation updated
- [ ] No new warnings
- [ ] Added tests for changes
```

### Review Process

1. **Automated Checks**: CI/CD runs tests and checks
2. **Code Review**: At least 2 maintainers review
3. **Feedback**: Address review comments
4. **Approval**: Requires 2 approvals to merge
5. **Merge**: Maintainer merges after approval

### Review Timeline

- Initial review: Within 3 business days
- Follow-up reviews: Within 2 business days
- Merge: After all checks pass and approvals received

## Community

### Communication Channels

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: Questions and general discussion
- **Mailing List**: substrait-dev@googlegroups.com
- **Slack**: #substrait-compliance channel

### Getting Help

- Check existing documentation
- Search GitHub issues
- Ask in GitHub Discussions
- Join community calls (monthly)

### Recognition

Contributors are recognized in:
- CHANGELOG.md for each release
- GitHub contributors page
- Annual contributor highlights

## License

By contributing to this project, you agree that your contributions will be licensed under the Apache License 2.0.

## Questions?

If you have questions about contributing, please:
1. Check this guide and other documentation
2. Search existing GitHub issues and discussions
3. Ask in GitHub Discussions
4. Contact maintainers via email

Thank you for contributing to the Substrait Compliance Framework! 🎉