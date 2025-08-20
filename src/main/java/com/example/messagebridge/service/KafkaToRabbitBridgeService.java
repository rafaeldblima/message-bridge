package com.example.messagebridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class KafkaToRabbitBridgeService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaToRabbitBridgeService.class);

    public Message<Object> processKafkaToRabbitMessage(Message<Object> message) {
        logger.info("Received message from Kafka for RabbitMQ forwarding: {}", message.getPayload());

        try {
            // Ensure payload is in proper format (handle both String and byte array)
            Object payload = message.getPayload();
            if (payload instanceof byte[]) {
                payload = new String((byte[]) payload, java.nio.charset.StandardCharsets.UTF_8);
            }
            
            // Create a new message with processed payload and headers
            // Add bridge-specific headers to track the message flow
            Message<Object> forwardedMessage = MessageBuilder
                    .withPayload(payload)
                    .copyHeaders(message.getHeaders())
                    .setHeader("x-original-source", "kafka")
                    .setHeader("x-destination", "rabbitmq")
                    .setHeader("x-bridge-type", "kafka-to-rabbit")
                    .setHeader("x-forwarded-timestamp", Instant.now().toString())
                    .build();

            logger.info("Successfully processed and forwarding message from Kafka to RabbitMQ");
            return forwardedMessage;

        } catch (Exception e) {
            logger.error("Error processing message from Kafka to RabbitMQ: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process message from Kafka to RabbitMQ", e);
        }
    }
}