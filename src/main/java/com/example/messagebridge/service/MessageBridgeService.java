package com.example.messagebridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class MessageBridgeService {

    private static final Logger logger = LoggerFactory.getLogger(MessageBridgeService.class);

    public Message<Object> processMessage(Message<Object> message) {
        logger.info("Received message from source: {}", message.getPayload());
        
        try {
            Message<Object> forwardedMessage = MessageBuilder
                    .withPayload(message.getPayload())
                    .copyHeaders(message.getHeaders())
                    .setHeader("x-original-source", "source")
                    .setHeader("x-forwarded-timestamp", System.currentTimeMillis())
                    .build();

            logger.info("Successfully processed and forwarded message to destination");
            return forwardedMessage;
        } catch (Exception e) {
            logger.error("Failed to process message: {}", e.getMessage(), e);
            throw new RuntimeException("Message processing failed", e);
        }
    }
}