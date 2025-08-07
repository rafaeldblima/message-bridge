# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Cloud Stream message bridge service that forwards messages between two separate RabbitMQ instances without modification. The service uses Spring Boot 3.4.5 with Java 24 and employs the modern Spring Cloud Stream functional programming model.

### Core Architecture

- **Message Flow**: `source.queue` (RabbitMQ 1) → Spring Cloud Stream Function → `destination.queue` (RabbitMQ 2)
- **Processing Model**: Functional approach using `Function<Message<Object>, Message<Object>>`  
- **Header Enrichment**: Adds `x-original-source` and `x-forwarded-timestamp` headers while preserving original payload
- **Multi-Binder Configuration**: Separate RabbitMQ binders (`rabbit-source`, `rabbit-destination`) for independent connection management

### Key Components

- `MessageBridgeService`: Core business logic for message processing and header enrichment
- `StreamConfig`: Spring configuration defining the `processMessage` function bean
- **Binding Names**: `processMessage-in-0` (input) and `processMessage-out-0` (output) following Spring Cloud Stream functional naming conventions

## Development Commands

### Build and Test
```bash
# Clean build
./gradlew clean build

# Run unit tests only  
./gradlew test

# Run integration tests (requires Docker)
./gradlew integrationTest

# Run all tests (unit + integration)
./gradlew allTests

# Quick test runner script
./run-tests.sh [unit|integration|all|e2e|quick]

# Run single test class
./gradlew test --tests MessageBridgeServiceTest

# Run single test method
./gradlew test --tests MessageBridgeServiceTest.shouldProcessMessageFromSource

# Run end-to-end tests only
./gradlew integrationTest --tests "*EndToEndTest"
```

### Local Development
```bash
# Start RabbitMQ infrastructure
docker-compose up -d rabbitmq-source rabbitmq-destination

# Run application locally
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Docker Operations
```bash
# Start entire stack
docker-compose up --build

# Start infrastructure only
docker-compose up -d rabbitmq-source rabbitmq-destination

# Test message publishing
docker-compose run --rm message-publisher
```

## Configuration Architecture

### Spring Cloud Stream Functional Model
- **Function Definition**: `spring.cloud.function.definition=processMessage`
- **Input Binding**: `processMessage-in-0` → `source.queue` via `rabbit-source` binder
- **Output Binding**: `processMessage-out-0` → `destination.queue` via `rabbit-destination` binder

### Multi-Binder Setup
Two separate RabbitMQ binders allow independent configuration of source and destination connections:
- `rabbit-source`: Connects to source RabbitMQ instance (port 5672)  
- `rabbit-destination`: Connects to destination RabbitMQ instance (port 5673)

### Environment Variables
Key configuration through environment variables:
- `RABBITMQ_SOURCE_HOST/PORT/USER/PASSWORD`: Source RabbitMQ connection
- `RABBITMQ_DESTINATION_HOST/PORT/USER/PASSWORD`: Destination RabbitMQ connection

## Testing Strategy

### Unit Tests
- **MessageBridgeServiceTest**: Direct testing of message processing logic using `@SpringBootTest`
- **Test Profile**: Uses `application-test.yml` with test binder configuration
- **Type Handling**: Tests use `Message<Object>` type casting for generic message support

### Integration Tests  
- **MessageBridgeIntegrationTest**: Basic Spring Boot context loading with RabbitMQ
- **MessageBridgeEndToEndTest**: Complete end-to-end message flow validation
- **Testcontainers**: Uses separate RabbitMQ containers for source and destination
- **Test Profiles**: `@Tag("integration")` for selective test execution
- **Real Message Flow**: Tests actual RabbitMQ message publishing, processing, and consumption

### End-to-End Test Architecture
The `MessageBridgeEndToEndTest` provides comprehensive validation:
- **Dual RabbitMQ Containers**: Separate source and destination RabbitMQ instances using Testcontainers
- **AMQP Client Integration**: Direct RabbitMQ client usage for message publishing and consumption  
- **Message Flow Validation**: Publishes to source queue, verifies forwarding to destination queue
- **Header Preservation**: Validates original headers are preserved and bridge headers are added
- **Multiple Message Patterns**: Tests simple text, JSON payloads, custom headers, and batch processing

## Important Technical Details

### Spring Cloud Stream Migration
This project uses the **modern functional programming model** (Spring Cloud Stream 3.1+):
- ❌ **Avoid**: `@EnableBinding`, `@StreamListener`, `@Input/@Output` annotations (deprecated/removed)
- ✅ **Use**: `Function<Message<Object>, Message<Object>>` beans with automatic binding

### Message Processing Pattern
```java
// Core pattern in MessageBridgeService.processMessage()
Message<Object> forwardedMessage = MessageBuilder
    .withPayload(message.getPayload())           // Preserve original payload
    .copyHeaders(message.getHeaders())           // Preserve all original headers  
    .setHeader("x-original-source", "source")    // Add tracking header
    .setHeader("x-forwarded-timestamp", timestamp) // Add timestamp
    .build();
```

### RabbitMQ Management Access
- **Source Instance**: http://localhost:15672 (admin/admin123)
- **Destination Instance**: http://localhost:15673 (admin/admin123)
- **Queue Monitoring**: Check `source.queue` and `destination.queue` in respective instances

## Monitoring and Debugging

### Application Health
- **Health Endpoint**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics  
- **Logs**: Set `logging.level.com.example.messagebridge=DEBUG` for detailed message processing logs

### Common Troubleshooting
- **Connection Issues**: Verify RabbitMQ instances are healthy: `docker-compose ps`
- **Message Flow**: Check logs for "Received message from source" and "Successfully processed and forwarded message"
- **Queue Status**: Monitor queue depths in RabbitMQ management UIs