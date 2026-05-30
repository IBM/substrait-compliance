#pragma once

#include <exception>
#include <string>
#include <memory>

namespace substrait::compliance {

/**
 * @brief Base exception class for all compliance framework errors
 */
class ComplianceError : public std::exception {
public:
    explicit ComplianceError(const std::string& message)
        : message_(message) {}
    
    const char* what() const noexcept override {
        return message_.c_str();
    }
    
    const std::string& message() const noexcept {
        return message_;
    }

private:
    std::string message_;
};

/**
 * @brief Exception thrown when plan execution fails
 */
class ExecutionError : public ComplianceError {
public:
    explicit ExecutionError(const std::string& message)
        : ComplianceError(message) {}
};

/**
 * @brief Exception thrown when plan validation fails
 */
class ValidationError : public ComplianceError {
public:
    explicit ValidationError(const std::string& message)
        : ComplianceError(message) {}
};

/**
 * @brief Exception thrown when test suite loading fails
 */
class LoaderError : public ComplianceError {
public:
    explicit LoaderError(const std::string& message)
        : ComplianceError(message) {}
};

/**
 * @brief Exception thrown when result comparison fails
 */
class ComparisonError : public ComplianceError {
public:
    explicit ComparisonError(const std::string& message)
        : ComplianceError(message) {}
};

/**
 * @brief Exception thrown for unsupported operations
 */
class UnsupportedError : public ComplianceError {
public:
    explicit UnsupportedError(const std::string& message)
        : ComplianceError(message) {}
};

/**
 * @brief Result type for operations that may fail
 * @tparam T The success value type
 */
template<typename T>
class Result {
public:
    // Success constructor
    static Result<T> success(T value) {
        return Result<T>(std::move(value));
    }
    
    // Error constructor
    static Result<T> error(const std::string& message) {
        return Result<T>(message);
    }
    
    // Check if result is successful
    bool is_success() const { return success_; }
    bool is_error() const { return !success_; }
    
    // Get value (throws if error)
    const T& value() const {
        if (!success_) {
            throw ComplianceError(error_message_);
        }
        return value_;
    }
    
    T& value() {
        if (!success_) {
            throw ComplianceError(error_message_);
        }
        return value_;
    }
    
    // Get error message
    const std::string& error() const {
        return error_message_;
    }
    
    // Conversion operators
    explicit operator bool() const { return success_; }

private:
    // Private constructors
    explicit Result(T value)
        : success_(true), value_(std::move(value)) {}
    
    explicit Result(const std::string& error)
        : success_(false), error_message_(error) {}
    
    bool success_;
    T value_;
    std::string error_message_;
};

} // namespace substrait::compliance

// Made with Bob
