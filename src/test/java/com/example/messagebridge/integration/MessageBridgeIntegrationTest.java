package com.example.messagebridge.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class MessageBridgeIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"))
            .withUser("admin", "admin123")
            .withVhost("/");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure source RabbitMQ binder
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.addresses", 
                () -> rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.username", () -> "admin");
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.password", () -> "admin123");
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.virtual-host", () -> "/");
        
        // Configure destination RabbitMQ binder (using same instance for test)
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.addresses", 
                () -> rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.username", () -> "admin");
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.password", () -> "admin123");
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.virtual-host", () -> "/");
    }

    @Test
    void shouldStartApplicationWithRabbitMQ() {
        // This test verifies that the application can start successfully with a real RabbitMQ instance
        // The actual message flow testing would require more complex setup with RabbitMQ clients
    }
}