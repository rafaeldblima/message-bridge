#!/bin/bash

# Test runner script for Spring Cloud Stream Message Bridge Service

set -e

echo "🧪 Spring Cloud Stream Message Bridge - Test Runner"
echo "================================================="

case "$1" in
    "unit"|"")
        echo "Running unit tests..."
        ./gradlew test
        ;;
    "integration")
        echo "Running integration tests (requires Docker)..."
        ./gradlew integrationTest
        ;;
    "all")
        echo "Running all tests..."
        ./gradlew allTests
        ;;
    "e2e")
        echo "Running end-to-end tests only..."
        ./gradlew integrationTest --tests "*EndToEndTest"
        ;;
    "quick")
        echo "Running quick validation (unit tests + build)..."
        ./gradlew clean test assemble
        ;;
    "help")
        echo ""
        echo "Usage: $0 [test-type]"
        echo ""
        echo "Test types:"
        echo "  unit        - Run unit tests only (default)"
        echo "  integration - Run integration tests (requires Docker)"
        echo "  all         - Run all tests (unit + integration)"
        echo "  e2e         - Run end-to-end tests only"
        echo "  quick       - Quick validation (unit tests + build)"
        echo "  help        - Show this help message"
        echo ""
        ;;
    *)
        echo "❌ Unknown test type: $1"
        echo "Run '$0 help' for usage information"
        exit 1
        ;;
esac

echo ""
echo "✅ Tests completed successfully!"