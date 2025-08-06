package com.example.messagebridge.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class MessageBridgeSimpleEndToEndTest {

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"))
            .withUser("guest", "guest");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure both binders to use the same RabbitMQ instance for simplicity
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.addresses", 
                () -> rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.username", () -> "guest");
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.password", () -> "guest");
        
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.addresses", 
                () -> rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.username", () -> "guest");
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    private StreamBridge streamBridge;

    @Test
    void shouldHaveStreamBridgeAvailable() {
        // Verify Spring context and StreamBridge are working
        assertThat(streamBridge).isNotNull();
    }
    
    @Test 
    void shouldSendMessageThroughStreamBridge() throws InterruptedException {
        // Simple test to verify StreamBridge can send messages
        Message<String> testMessage = MessageBuilder.withPayload("Hello World").build();
        
        // Send message using StreamBridge (bypasses the consumer/function flow for now)
        boolean sent = streamBridge.send("processMessage-out-0", testMessage);
        assertThat(sent).isTrue();
        
        // Allow time for message to be sent
        Thread.sleep(1000);
    }
}