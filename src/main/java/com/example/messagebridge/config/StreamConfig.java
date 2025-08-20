package com.example.messagebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import com.example.messagebridge.service.MessageBridgeService;
import com.example.messagebridge.service.RabbitToKafkaBridgeService;
import com.example.messagebridge.service.KafkaToRabbitBridgeService;
import java.util.function.Function;

@Configuration
public class StreamConfig {

    private final MessageBridgeService messageBridgeService;
    private final RabbitToKafkaBridgeService rabbitToKafkaBridgeService;
    private final KafkaToRabbitBridgeService kafkaToRabbitBridgeService;

    public StreamConfig(MessageBridgeService messageBridgeService,
                       RabbitToKafkaBridgeService rabbitToKafkaBridgeService,
                       KafkaToRabbitBridgeService kafkaToRabbitBridgeService) {
        this.messageBridgeService = messageBridgeService;
        this.rabbitToKafkaBridgeService = rabbitToKafkaBridgeService;
        this.kafkaToRabbitBridgeService = kafkaToRabbitBridgeService;
    }

    // Original RabbitMQ to RabbitMQ bridge
    @Bean
    public Function<Message<Object>, Message<Object>> processMessage() {
        return messageBridgeService::processMessage;
    }

    // RabbitMQ to Kafka bridge
    @Bean
    public Function<Message<Object>, Message<Object>> processRabbitToKafka() {
        return rabbitToKafkaBridgeService::processRabbitToKafkaMessage;
    }

    // Kafka to RabbitMQ bridge
    @Bean
    public Function<Message<Object>, Message<Object>> processKafkaToRabbit() {
        return kafkaToRabbitBridgeService::processKafkaToRabbitMessage;
    }
}