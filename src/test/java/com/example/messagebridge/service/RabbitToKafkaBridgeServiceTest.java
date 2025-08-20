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
class RabbitToKafkaBridgeServiceTest {

    @Autowired
    private RabbitToKafkaBridgeService rabbitToKafkaBridgeService;

    @Test
    void shouldProcessRabbitToKafkaMessage() {
        // Given
        String testPayload = "Hello from RabbitMQ to Kafka";
        Message<Object> originalMessage = MessageBuilder
                .withPayload((Object) testPayload)
                .setHeader("original-header", "original-value")
                .build();

        // When
        Message<Object> result = rabbitToKafkaBridgeService.processRabbitToKafkaMessage(originalMessage);

        // Then
        assertThat(result.getPayload()).isEqualTo(testPayload);
        assertThat(result.getHeaders().get("original-header")).isEqualTo("original-value");
        assertThat(result.getHeaders().get("x-original-source")).isEqualTo("rabbitmq");
        assertThat(result.getHeaders().get("x-destination")).isEqualTo("kafka");
        assertThat(result.getHeaders().get("x-bridge-type")).isEqualTo("rabbit-to-kafka");
        assertThat(result.getHeaders().get("x-forwarded-timestamp")).isNotNull();
    }
}