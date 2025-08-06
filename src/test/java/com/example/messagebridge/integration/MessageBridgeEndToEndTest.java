package com.example.messagebridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class MessageBridgeEndToEndTest {

    private static final String SOURCE_QUEUE = "source.queue";
    private static final String DESTINATION_QUEUE = "destination.queue";
    
    private static final Network network = Network.newNetwork();

    @Container
    static RabbitMQContainer sourceRabbitMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"))
            .withNetwork(network)
            .withNetworkAliases("rabbitmq-source")
            .withUser("guest", "guest")
            .withExposedPorts(5672, 15672)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());

    @Container  
    static RabbitMQContainer destinationRabbitMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"))
            .withNetwork(network)
            .withNetworkAliases("rabbitmq-destination") 
            .withUser("guest", "guest")
            .withExposedPorts(5672, 15672)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure source RabbitMQ binder
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.addresses", 
                () -> sourceRabbitMQ.getHost() + ":" + sourceRabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.username", () -> "guest");
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.password", () -> "guest");
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.virtual-host", () -> "/");
        
        // Configure destination RabbitMQ binder
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.addresses", 
                () -> destinationRabbitMQ.getHost() + ":" + destinationRabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.username", () -> "guest");
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.password", () -> "guest");
        registry.add("spring.cloud.stream.binders.rabbit-destination.environment.spring.rabbitmq.virtual-host", () -> "/");
    }

    private ConnectionFactory sourceConnectionFactory;
    private ConnectionFactory destinationConnectionFactory;
    private Connection sourceConnection;
    private Connection destinationConnection;
    private Channel sourceChannel;
    private Channel destinationChannel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        // Allow some time for RabbitMQ containers to fully initialize and Spring Boot to start
        Thread.sleep(5000);
        
        // Setup source RabbitMQ connection
        sourceConnectionFactory = new ConnectionFactory();
        sourceConnectionFactory.setHost(sourceRabbitMQ.getHost());
        sourceConnectionFactory.setPort(sourceRabbitMQ.getAmqpPort());
        sourceConnectionFactory.setUsername("guest");
        sourceConnectionFactory.setPassword("guest");
        sourceConnectionFactory.setVirtualHost("/");
        
        sourceConnection = sourceConnectionFactory.newConnection();
        sourceChannel = sourceConnection.createChannel();
        
        // Setup destination RabbitMQ connection  
        destinationConnectionFactory = new ConnectionFactory();
        destinationConnectionFactory.setHost(destinationRabbitMQ.getHost());
        destinationConnectionFactory.setPort(destinationRabbitMQ.getAmqpPort());
        destinationConnectionFactory.setUsername("guest");
        destinationConnectionFactory.setPassword("guest");
        destinationConnectionFactory.setVirtualHost("/");
        
        destinationConnection = destinationConnectionFactory.newConnection();
        destinationChannel = destinationConnection.createChannel();
        
        // Declare queues - using the exact names that Spring Cloud Stream expects
        sourceChannel.queueDeclare(SOURCE_QUEUE, true, false, false, null);
        destinationChannel.queueDeclare(DESTINATION_QUEUE, true, false, false, null);
        
        // Declare the Spring Cloud Stream queues that we expect to exist
        sourceChannel.queueDeclare("source.queue.message-bridge-group", true, false, false, null);
        destinationChannel.queueDeclare("destination.queue.destination-group", true, false, false, null);
        
        // Purge all queues to ensure clean state - only purge queues that exist
        try { sourceChannel.queuePurge(SOURCE_QUEUE); } catch (Exception e) { }
        try { destinationChannel.queuePurge(DESTINATION_QUEUE); } catch (Exception e) { }
        try { sourceChannel.queuePurge("source.queue.message-bridge-group"); } catch (Exception e) { }
        try { destinationChannel.queuePurge("destination.queue.destination-group"); } catch (Exception e) { }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sourceChannel != null && sourceChannel.isOpen()) {
            sourceChannel.close();
        }
        if (sourceConnection != null && sourceConnection.isOpen()) {
            sourceConnection.close();
        }
        if (destinationChannel != null && destinationChannel.isOpen()) {
            destinationChannel.close();
        }
        if (destinationConnection != null && destinationConnection.isOpen()) {
            destinationConnection.close();
        }
    }

    @Test
    @Timeout(30)
    void shouldForwardSimpleTextMessage() throws Exception {
        // Given
        String testMessage = "Hello from source RabbitMQ!";
        
        // Setup consumer for destination queue - try both possible queue names
        BlockingQueue<TestMessage> receivedMessages = new LinkedBlockingQueue<>();
        
        // Setup consumers on both possible destination queues
        String consumerTag1 = destinationChannel.basicConsume("destination.queue", true, new DefaultConsumer(destinationChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                String message = new String(body, StandardCharsets.UTF_8);
                TestMessage testMsg = new TestMessage();
                testMsg.payload = message;
                testMsg.headers = properties.getHeaders() != null ? new HashMap<>(properties.getHeaders()) : new HashMap<>();
                System.out.println("*** RECEIVED MESSAGE ON destination.queue: " + message);
                receivedMessages.offer(testMsg);
            }
        });
        
        String consumerTag2 = destinationChannel.basicConsume("destination.queue.destination-group", true, new DefaultConsumer(destinationChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                String message = new String(body, StandardCharsets.UTF_8);
                TestMessage testMsg = new TestMessage();
                testMsg.payload = message;
                testMsg.headers = properties.getHeaders() != null ? new HashMap<>(properties.getHeaders()) : new HashMap<>();
                System.out.println("*** RECEIVED MESSAGE ON destination.queue.destination-group: " + message);
                receivedMessages.offer(testMsg);
            }
        });

        // When - publish message to the actual Spring Cloud Stream queue
        sourceChannel.basicPublish("", "source.queue.message-bridge-group", null, testMessage.getBytes(StandardCharsets.UTF_8));

        // Wait a moment for processing
        Thread.sleep(2000);
        
        // Debug: List all queues and their message counts in destination RabbitMQ
        System.out.println("=== DESTINATION RABBITMQ QUEUES ===");
        try {
            // Use RabbitMQ management API to check queues
            String managementUrl = "http://localhost:" + destinationRabbitMQ.getHttpPort() + "/api/queues";
            // Skip the HTTP call and just try to find messages by consuming from all possible queues
            
            // Try to get messages from any queue that might have messages
            String[] candidateQueues = {"destination.queue", "destination.queue.destination-group"};
            for (String queueName : candidateQueues) {
                try {
                    // Check if queue has messages using basicGet (non-blocking)
                    var response = destinationChannel.basicGet(queueName, true);
                    if (response != null) {
                        String message = new String(response.getBody(), StandardCharsets.UTF_8);
                        System.out.println("*** FOUND MESSAGE IN QUEUE " + queueName + ": " + message);
                        TestMessage testMsg = new TestMessage();
                        testMsg.payload = message;
                        testMsg.headers = response.getProps().getHeaders() != null ? 
                            new HashMap<>(response.getProps().getHeaders()) : new HashMap<>();
                        receivedMessages.offer(testMsg);
                    }
                } catch (Exception e) {
                    System.out.println("Could not get message from queue " + queueName + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Queue inspection failed: " + e.getMessage());
        }
        
        // Then - verify message is received at destination
        TestMessage receivedMessage = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "Message should be forwarded to destination queue");
        assertThat(receivedMessage.payload).isEqualTo(testMessage);
        assertThat(receivedMessage.headers).containsKey("x-original-source");
        assertThat(receivedMessage.headers.get("x-original-source")).isEqualTo("source");
        assertThat(receivedMessage.headers).containsKey("x-forwarded-timestamp");
        
        destinationChannel.basicCancel(consumerTag1);
        destinationChannel.basicCancel(consumerTag2);
    }

    @Test
    @Timeout(30)
    void shouldForwardJsonMessage() throws Exception {
        // Given
        TestPayload originalPayload = new TestPayload("test-123", "Integration Test Message", 42);
        String jsonMessage = objectMapper.writeValueAsString(originalPayload);
        
        // Setup consumer for destination queue (Spring Cloud Stream publishes to "destination.queue.destination-group")
        BlockingQueue<TestMessage> receivedMessages = new LinkedBlockingQueue<>();
        String consumerTag = destinationChannel.basicConsume("destination.queue.destination-group", true, new DefaultConsumer(destinationChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                String message = new String(body, StandardCharsets.UTF_8);
                TestMessage testMsg = new TestMessage();
                testMsg.payload = message;
                testMsg.headers = properties.getHeaders() != null ? new HashMap<>(properties.getHeaders()) : new HashMap<>();
                receivedMessages.offer(testMsg);
            }
        });

        // When - publish JSON message to source queue
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .build();
        sourceChannel.basicPublish("", "source.queue.message-bridge-group", props, jsonMessage.getBytes(StandardCharsets.UTF_8));

        // Then - verify JSON message is received at destination
        TestMessage receivedMessage = receivedMessages.poll(20, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "JSON message should be forwarded to destination queue");
        
        // Verify payload is unchanged
        TestPayload receivedPayload = objectMapper.readValue(receivedMessage.payload, TestPayload.class);
        assertThat(receivedPayload).isEqualTo(originalPayload);
        
        // Verify headers are added
        assertThat(receivedMessage.headers).containsKey("x-original-source");
        assertThat(receivedMessage.headers.get("x-original-source")).isEqualTo("source");
        assertThat(receivedMessage.headers).containsKey("x-forwarded-timestamp");
        
        destinationChannel.basicCancel(consumerTag);
    }

    @Test
    @Timeout(30)
    void shouldPreserveOriginalHeaders() throws Exception {
        // Given
        String testMessage = "Message with custom headers";
        
        // Setup consumer for destination queue (Spring Cloud Stream publishes to "destination.queue.destination-group")
        BlockingQueue<TestMessage> receivedMessages = new LinkedBlockingQueue<>();
        String consumerTag = destinationChannel.basicConsume("destination.queue.destination-group", true, new DefaultConsumer(destinationChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                String message = new String(body, StandardCharsets.UTF_8);
                TestMessage testMsg = new TestMessage();
                testMsg.payload = message;
                testMsg.headers = properties.getHeaders() != null ? new HashMap<>(properties.getHeaders()) : new HashMap<>();
                receivedMessages.offer(testMsg);
            }
        });

        // When - publish message with custom headers
        Map<String, Object> customHeaders = new HashMap<>();
        customHeaders.put("custom-header", "custom-value");
        customHeaders.put("message-id", "test-123");
        customHeaders.put("priority", 5);
        
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .headers(customHeaders)
                .contentType("text/plain")
                .build();
        
        sourceChannel.basicPublish("", "source.queue.message-bridge-group", props, testMessage.getBytes(StandardCharsets.UTF_8));

        // Then - verify original headers are preserved and new ones are added
        TestMessage receivedMessage = receivedMessages.poll(20, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "Message with headers should be forwarded");
        assertThat(receivedMessage.payload).isEqualTo(testMessage);
        
        // Verify original headers are preserved
        assertThat(receivedMessage.headers).containsKey("custom-header");
        assertThat(receivedMessage.headers.get("custom-header")).isEqualTo("custom-value");
        assertThat(receivedMessage.headers).containsKey("message-id");
        assertThat(receivedMessage.headers.get("message-id")).isEqualTo("test-123");
        assertThat(receivedMessage.headers).containsKey("priority");
        assertThat(receivedMessage.headers.get("priority")).isEqualTo(5);
        
        // Verify bridge headers are added
        assertThat(receivedMessage.headers).containsKey("x-original-source");
        assertThat(receivedMessage.headers.get("x-original-source")).isEqualTo("source");
        assertThat(receivedMessage.headers).containsKey("x-forwarded-timestamp");
        
        destinationChannel.basicCancel(consumerTag);
    }

    @Test
    @Timeout(30)
    void shouldHandleMultipleMessages() throws Exception {
        // Given
        int messageCount = 5;
        
        // Setup consumer for destination queue (Spring Cloud Stream publishes to "destination.queue.destination-group")
        BlockingQueue<TestMessage> receivedMessages = new LinkedBlockingQueue<>();
        String consumerTag = destinationChannel.basicConsume("destination.queue.destination-group", true, new DefaultConsumer(destinationChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                String message = new String(body, StandardCharsets.UTF_8);
                TestMessage testMsg = new TestMessage();
                testMsg.payload = message;
                testMsg.headers = properties.getHeaders() != null ? new HashMap<>(properties.getHeaders()) : new HashMap<>();
                receivedMessages.offer(testMsg);
            }
        });

        // When - publish multiple messages
        for (int i = 1; i <= messageCount; i++) {
            String message = "Test message " + i;
            sourceChannel.basicPublish("", "source.queue.message-bridge-group", null, message.getBytes(StandardCharsets.UTF_8));
        }

        // Then - verify all messages are forwarded
        for (int i = 1; i <= messageCount; i++) {
            TestMessage receivedMessage = receivedMessages.poll(10, TimeUnit.SECONDS);
            assertNotNull(receivedMessage, "Message " + i + " should be forwarded");
            assertThat(receivedMessage.payload).isEqualTo("Test message " + i);
            assertThat(receivedMessage.headers).containsKey("x-original-source");
        }
        
        destinationChannel.basicCancel(consumerTag);
    }

    // Helper classes for test data
    private static class TestMessage {
        public String payload;
        public Map<String, Object> headers;
    }

    private record TestPayload(String id, String content, Integer value) {}
}