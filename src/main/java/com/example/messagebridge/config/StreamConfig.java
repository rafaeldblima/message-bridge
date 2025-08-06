package com.example.messagebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import com.example.messagebridge.service.MessageBridgeService;
import java.util.function.Function;

@Configuration
public class StreamConfig {

    private final MessageBridgeService messageBridgeService;

    public StreamConfig(MessageBridgeService messageBridgeService) {
        this.messageBridgeService = messageBridgeService;
    }

    @Bean
    public Function<Message<Object>, Message<Object>> processMessage() {
        return messageBridgeService::processMessage;
    }
}