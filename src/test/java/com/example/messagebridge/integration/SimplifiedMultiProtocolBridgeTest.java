package com.example.messagebridge.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.FunctionCatalog;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestChannelBinderConfiguration.class)
class SimplifiedMultiProtocolBridgeTest {

    @Autowired
    private FunctionCatalog functionCatalog;

    @Test
    void shouldProcessRabbitToKafkaFunction() {
        // Given
        String testPayload = "Hello from RabbitMQ to Kafka!";
        Message<Object> inputMessage = MessageBuilder
                .withPayload((Object) testPayload)
                .setHeader("original-header", "original-value")
                .build();
        
        // When
        @SuppressWarnings("unchecked")
        var processRabbitToKafka = (java.util.function.Function<Message<Object>, Message<Object>>) 
                functionCatalog.lookup(java.util.function.Function.class, "processRabbitToKafka");
        assertThat(processRabbitToKafka).isNotNull();
        
        Message<Object> result = processRabbitToKafka.apply(inputMessage);
        
        // Then
        assertThat(result.getPayload()).isEqualTo(testPayload);
        assertThat(result.getHeaders().get("original-header")).isEqualTo("original-value");
        assertThat(result.getHeaders().get("x-original-source")).isEqualTo("rabbitmq");
        assertThat(result.getHeaders().get("x-destination")).isEqualTo("kafka");
        assertThat(result.getHeaders().get("x-bridge-type")).isEqualTo("rabbit-to-kafka");
        assertThat(result.getHeaders().get("x-forwarded-timestamp")).isNotNull();
    }

    @Test
    void shouldProcessKafkaToRabbitFunction() {
        // Given
        String testPayload = "Hello from Kafka to RabbitMQ!";
        Message<Object> inputMessage = MessageBuilder
                .withPayload((Object) testPayload)
                .setHeader("kafka-header", "kafka-value")
                .build();
        
        // When
        @SuppressWarnings("unchecked")
        var processKafkaToRabbit = (java.util.function.Function<Message<Object>, Message<Object>>) 
                functionCatalog.lookup(java.util.function.Function.class, "processKafkaToRabbit");
        assertThat(processKafkaToRabbit).isNotNull();
        
        Message<Object> result = processKafkaToRabbit.apply(inputMessage);
        
        // Then
        assertThat(result.getPayload()).isEqualTo(testPayload);
        assertThat(result.getHeaders().get("kafka-header")).isEqualTo("kafka-value");
        assertThat(result.getHeaders().get("x-original-source")).isEqualTo("kafka");
        assertThat(result.getHeaders().get("x-destination")).isEqualTo("rabbitmq");
        assertThat(result.getHeaders().get("x-bridge-type")).isEqualTo("kafka-to-rabbit");
        assertThat(result.getHeaders().get("x-forwarded-timestamp")).isNotNull();
    }
}