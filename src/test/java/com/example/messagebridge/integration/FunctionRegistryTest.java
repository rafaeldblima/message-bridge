package com.example.messagebridge.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class FunctionRegistryTest {

    @Autowired
    private FunctionCatalog functionCatalog;

    @Test
    void shouldHaveAllFunctionsRegistered() {
        System.out.println("Registered functions:");
        functionCatalog.getNames(null).forEach(name -> {
            System.out.println(" - " + name);
        });
        
        // Verify all three functions are registered
        assertThat(functionCatalog.getNames(null))
            .containsAll(java.util.List.of("processMessage", "processRabbitToKafka", "processKafkaToRabbit"));
    }
}