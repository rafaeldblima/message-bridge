package com.example.messagebridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RabbitToKafkaBridgeService {

    private static final Logger logger = LoggerFactory.getLogger(RabbitToKafkaBridgeService.class);

    public Message<Object> processRabbitToKafkaMessage(Message<Object> message) {
        logger.info("Received message from RabbitMQ for Kafka forwarding: {}", message.getPayload());

        try {
            // Convert byte array payload to String if necessary (RabbitMQ often sends byte arrays)
            Object payload = message.getPayload();
            if (payload instanceof byte[]) {
                payload = new String((byte[]) payload, java.nio.charset.StandardCharsets.UTF_8);
            }
            
            // Create a new message with converted payload and headers
            // Add bridge-specific headers to track the message flow
            Message<Object> forwardedMessage = MessageBuilder
                    .withPayload(payload)
                    .copyHeaders(message.getHeaders())
                    .setHeader("x-original-source", "rabbitmq")
                    .setHeader("x-destination", "kafka")
                    .setHeader("x-bridge-type", "rabbit-to-kafka")
                    .setHeader("x-forwarded-timestamp", Instant.now().toString())
                    .build();

            logger.info("Successfully processed and forwarding message from RabbitMQ to Kafka");
            return forwardedMessage;

        } catch (Exception e) {
            logger.error("Error processing message from RabbitMQ to Kafka: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process message from RabbitMQ to Kafka", e);
        }
    }
}