package com.example.messagebridge.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MessageBridgeServiceTest {

    @Autowired
    private MessageBridgeService messageBridgeService;

    @Test
    void shouldProcessMessageFromSource() {
        String testPayload = "Test message from source";
        Message<Object> testMessage = MessageBuilder.withPayload((Object) testPayload).build();

        Message<Object> resultMessage = messageBridgeService.processMessage(testMessage);
        
        assertThat(resultMessage).isNotNull();
        assertThat(resultMessage.getPayload()).isEqualTo(testPayload);
        assertThat(resultMessage.getHeaders().get("x-original-source")).isEqualTo("source");
        assertThat(resultMessage.getHeaders().get("x-forwarded-timestamp")).isNotNull();
    }

    @Test
    void shouldPreserveOriginalHeaders() {
        String testPayload = "Test message with headers";
        Message<Object> testMessage = MessageBuilder
                .withPayload((Object) testPayload)
                .setHeader("custom-header", "custom-value")
                .setHeader("content-type", "application/json")
                .build();

        Message<Object> resultMessage = messageBridgeService.processMessage(testMessage);
        
        assertThat(resultMessage).isNotNull();
        assertThat(resultMessage.getPayload()).isEqualTo(testPayload);
        assertThat(resultMessage.getHeaders().get("custom-header")).isEqualTo("custom-value");
        assertThat(resultMessage.getHeaders().get("content-type")).isEqualTo("application/json");
        assertThat(resultMessage.getHeaders().get("x-original-source")).isEqualTo("source");
        assertThat(resultMessage.getHeaders().get("x-forwarded-timestamp")).isNotNull();
    }

    @Test
    void shouldHandleComplexPayloads() {
        var complexPayload = new TestData("test-id", "Test content", 42);
        Message<Object> testMessage = MessageBuilder.withPayload((Object) complexPayload).build();

        Message<Object> resultMessage = messageBridgeService.processMessage(testMessage);
        
        assertThat(resultMessage).isNotNull();
        assertThat(resultMessage.getPayload()).isEqualTo(complexPayload);
        assertThat(resultMessage.getHeaders().get("x-original-source")).isEqualTo("source");
        assertThat(resultMessage.getHeaders().get("x-forwarded-timestamp")).isNotNull();
    }

    private record TestData(String id, String content, Integer value) {}
}