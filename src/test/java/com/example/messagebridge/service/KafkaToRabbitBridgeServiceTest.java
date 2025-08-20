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
class KafkaToRabbitBridgeServiceTest {

    @Autowired
    private KafkaToRabbitBridgeService kafkaToRabbitBridgeService;

    @Test
    void shouldProcessKafkaToRabbitMessage() {
        // Given
        String testPayload = "Hello from Kafka to RabbitMQ";
        Message<Object> originalMessage = MessageBuilder
                .withPayload((Object) testPayload)
                .setHeader("kafka-header", "kafka-value")
                .build();

        // When
        Message<Object> result = kafkaToRabbitBridgeService.processKafkaToRabbitMessage(originalMessage);

        // Then
        assertThat(result.getPayload()).isEqualTo(testPayload);
        assertThat(result.getHeaders().get("kafka-header")).isEqualTo("kafka-value");
        assertThat(result.getHeaders().get("x-original-source")).isEqualTo("kafka");
        assertThat(result.getHeaders().get("x-destination")).isEqualTo("rabbitmq");
        assertThat(result.getHeaders().get("x-bridge-type")).isEqualTo("kafka-to-rabbit");
        assertThat(result.getHeaders().get("x-forwarded-timestamp")).isNotNull();
    }
}