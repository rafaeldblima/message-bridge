package com.example.messagebridge.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class MultiProtocolBridgeTest {

    private static final Network network = Network.newNetwork();

    @Container
    private static final RabbitMQContainer rabbitMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.11-management"))
            .withNetwork(network)
            .withNetworkAliases("rabbitmq")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withUser("guest", "guest")
            .withExposedPorts(5672, 15672)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Server startup complete.*", 1));

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"))
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_NUM_PARTITIONS", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", "1")
            // Configure listeners properly for container networking
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,BROKER://0.0.0.0:9093")
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092,BROKER://kafka:9093")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER")
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*started.*", 1));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Enable all function definitions
        registry.add("spring.cloud.function.definition", () -> "processMessage;processRabbitToKafka;processKafkaToRabbit");
        
        // Configure RabbitMQ binders to use the same instance
        registry.add("spring.cloud.stream.binders.rabbit-source.type", () -> "rabbit");
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.addresses", 
                () -> rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.username", 
                () -> rabbitMQ.getAdminUsername());
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.password", 
                () -> rabbitMQ.getAdminPassword());
        registry.add("spring.cloud.stream.binders.rabbit-source.environment.spring.rabbitmq.connection-timeout", 
                () -> "10000");
        
        registry.add("spring.cloud.stream.binders.rabbit-sink.type", () -> "rabbit");
        registry.add("spring.cloud.stream.binders.rabbit-sink.environment.spring.rabbitmq.addresses", 
                () -> rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        registry.add("spring.cloud.stream.binders.rabbit-sink.environment.spring.rabbitmq.username", 
                () -> rabbitMQ.getAdminUsername());
        registry.add("spring.cloud.stream.binders.rabbit-sink.environment.spring.rabbitmq.password", 
                () -> rabbitMQ.getAdminPassword());
        registry.add("spring.cloud.stream.binders.rabbit-sink.environment.spring.rabbitmq.connection-timeout", 
                () -> "10000");
        
        // For Spring Cloud Stream, use the internal container network address (kafka:9092)
        String kafkaBootstrapServers = "kafka:9092";
        System.out.println("Using Kafka bootstrap servers for Spring Cloud Stream: " + kafkaBootstrapServers);
        
        // Configure Kafka binders to use the same instance
        registry.add("spring.cloud.stream.binders.kafka-source.type", () -> "kafka");
        registry.add("spring.cloud.stream.binders.kafka-source.environment.spring.kafka.bootstrap-servers", 
                () -> kafkaBootstrapServers);
        registry.add("spring.cloud.stream.binders.kafka-source.environment.spring.kafka.consumer.auto-offset-reset", 
                () -> "earliest");
        registry.add("spring.cloud.stream.binders.kafka-source.environment.spring.kafka.consumer.max-poll-records", 
                () -> "1");
        registry.add("spring.cloud.stream.binders.kafka-source.environment.spring.kafka.consumer.fetch-min-size", 
                () -> "1");
        registry.add("spring.cloud.stream.binders.kafka-source.environment.spring.kafka.consumer.fetch-max-wait", 
                () -> "100");
        registry.add("spring.cloud.stream.binders.kafka-source.environment.spring.kafka.consumer.heartbeat-interval", 
                () -> "3000");
        registry.add("spring.cloud.stream.binders.kafka-source.environment.spring.kafka.consumer.session-timeout", 
                () -> "30000");
        
        registry.add("spring.cloud.stream.binders.kafka-sink.type", () -> "kafka");
        registry.add("spring.cloud.stream.binders.kafka-sink.environment.spring.kafka.bootstrap-servers", 
                () -> kafkaBootstrapServers);
        registry.add("spring.cloud.stream.binders.kafka-sink.environment.spring.kafka.producer.acks", 
                () -> "all");
        registry.add("spring.cloud.stream.binders.kafka-sink.environment.spring.kafka.producer.delivery-timeout", 
                () -> "30000");
        registry.add("spring.cloud.stream.binders.kafka-sink.environment.spring.kafka.producer.request-timeout", 
                () -> "15000");
        
        // Configure bindings for RabbitMQ to Kafka
        registry.add("spring.cloud.stream.bindings.processRabbitToKafka-in-0.destination", () -> "rabbit.to.kafka.queue");
        registry.add("spring.cloud.stream.bindings.processRabbitToKafka-in-0.binder", () -> "rabbit-source");
        registry.add("spring.cloud.stream.bindings.processRabbitToKafka-out-0.destination", () -> "rabbit-to-kafka-topic");
        registry.add("spring.cloud.stream.bindings.processRabbitToKafka-out-0.binder", () -> "kafka-sink");
        
        // Configure bindings for Kafka to RabbitMQ
        registry.add("spring.cloud.stream.bindings.processKafkaToRabbit-in-0.destination", () -> "kafka-source-topic");
        registry.add("spring.cloud.stream.bindings.processKafkaToRabbit-in-0.binder", () -> "kafka-source");
        registry.add("spring.cloud.stream.bindings.processKafkaToRabbit-in-0.group", () -> "kafka-to-rabbit-group");
        registry.add("spring.cloud.stream.bindings.processKafkaToRabbit-out-0.destination", () -> "kafka.to.rabbit.queue");
        registry.add("spring.cloud.stream.bindings.processKafkaToRabbit-out-0.binder", () -> "rabbit-sink");
        
        // Configure bindings for direct message processing
        registry.add("spring.cloud.stream.bindings.processMessage-in-0.destination", () -> "source.queue");
        registry.add("spring.cloud.stream.bindings.processMessage-in-0.binder", () -> "rabbit-source");
        registry.add("spring.cloud.stream.bindings.processMessage-out-0.destination", () -> "target.queue");
        registry.add("spring.cloud.stream.bindings.processMessage-out-0.binder", () -> "rabbit-sink");
        
        // Log the configuration
        System.out.println("Configured RabbitMQ at: " + rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        System.out.println("Configured Kafka at: " + kafka.getBootstrapServers());
    }

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Channel channel;
    
    private KafkaProducer<String, String> kafkaProducer;
    private KafkaConsumer<String, String> kafkaConsumer;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure containers are running and wait for them to be ready
        System.out.println("Setting up test environment...");
        assertTrue(rabbitMQ.isRunning(), "RabbitMQ container must be running");
        assertTrue(kafka.isRunning(), "Kafka container must be running");
        
        System.out.println("RabbitMQ container is running: " + rabbitMQ.isRunning());
        System.out.println("Kafka container is running: " + kafka.isRunning());
        System.out.println("Kafka bootstrap servers: " + kafka.getBootstrapServers());
        System.out.println("Kafka network aliases: kafka:9092");
        
        // Wait for containers to be fully initialized
        waitForKafka();
        waitForRabbitMQ();
        
        // Allow time for Spring Cloud Stream bindings to initialize
        System.out.println("Waiting for Spring Cloud Stream bindings to initialize...");
        Thread.sleep(15000);
        
        // Setup RabbitMQ connection
        System.out.println("Setting up RabbitMQ connection...");
        connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitMQ.getHost());
        connectionFactory.setPort(rabbitMQ.getAmqpPort());
        connectionFactory.setUsername(rabbitMQ.getAdminUsername());
        connectionFactory.setPassword(rabbitMQ.getAdminPassword());
        connectionFactory.setConnectionTimeout(10000);
        
        System.out.println("Connecting to RabbitMQ at " + rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
        connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        
        // Declare queues for testing
        System.out.println("Declaring RabbitMQ queues...");
        channel.queueDeclare("source.queue", true, false, false, null);
        channel.queueDeclare("rabbit.to.kafka.queue", true, false, false, null);
        channel.queueDeclare("kafka.to.rabbit.queue", true, false, false, null);
        
        // Setup Kafka consumer properties
        System.out.println("Setting up Kafka consumer...");
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 30000);
        
        kafkaConsumer = new KafkaConsumer<>(props);
        System.out.println("Test setup completed successfully");
    }
    
    private void waitForKafka() {
        try {
            // Try to create an AdminClient to verify Kafka is ready
            int maxRetries = 15; // Increased retries
            boolean connected = false;
            
            // For test clients, use the external mapped port
            String bootstrapServers = kafka.getBootstrapServers();
            System.out.println("Waiting for Kafka to be ready at " + bootstrapServers);
            
            for (int i = 0; i < maxRetries && !connected; i++) {
                try {
                    Properties props = new Properties();
                    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
                    props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000);
                    
                    try (AdminClient adminClient = AdminClient.create(props)) {
                        // Try to list topics as a connectivity test
                        Set<String> topics = adminClient.listTopics().names().get(10, TimeUnit.SECONDS);
                        System.out.println("Found " + topics.size() + " topics in Kafka");
                        connected = true;
                        System.out.println("Successfully connected to Kafka after " + (i + 1) + " attempts");
                    }
                } catch (Exception e) {
                    System.out.println("Waiting for Kafka to be ready... Attempt " + (i + 1) + ". Error: " + e.getMessage());
                    Thread.sleep(5000);
                }
            }
            
            if (!connected) {
                throw new RuntimeException("Failed to connect to Kafka after " + maxRetries + " attempts");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error waiting for Kafka", e);
        }
    }
    
    private void waitForRabbitMQ() {
        try {
            // Try to create a connection to verify RabbitMQ is ready
            int maxRetries = 15; // Increased retries
            boolean connected = false;
            
            System.out.println("Waiting for RabbitMQ to be ready at " + rabbitMQ.getHost() + ":" + rabbitMQ.getAmqpPort());
            
            for (int i = 0; i < maxRetries && !connected; i++) {
                try {
                    ConnectionFactory factory = new ConnectionFactory();
                    factory.setHost(rabbitMQ.getHost());
                    factory.setPort(rabbitMQ.getAmqpPort());
                    factory.setUsername(rabbitMQ.getAdminUsername());
                    factory.setPassword(rabbitMQ.getAdminPassword());
                    factory.setConnectionTimeout(5000);
                    
                    try (Connection testConnection = factory.newConnection()) {
                        if (testConnection.isOpen()) {
                            connected = true;
                            System.out.println("Successfully connected to RabbitMQ after " + (i + 1) + " attempts");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Waiting for RabbitMQ to be ready... Attempt " + (i + 1) + ". Error: " + e.getMessage());
                    Thread.sleep(5000);
                }
            }
            
            if (!connected) {
                throw new RuntimeException("Failed to connect to RabbitMQ after " + maxRetries + " attempts");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error waiting for RabbitMQ", e);
        }
    }

    @Test
    @Timeout(60) // Increased timeout for stability
    void shouldBridgeFromRabbitMQToKafka() throws Exception {
        // Given
        String testMessage = "Hello from RabbitMQ to Kafka!";
        
        // Create topic explicitly to ensure it exists
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            // Check if topic exists first
            Set<String> existingTopics = adminClient.listTopics().names().get(30, TimeUnit.SECONDS);
            System.out.println("Existing topics: " + existingTopics);
            
            if (!existingTopics.contains("rabbit-to-kafka-topic")) {
                System.out.println("Creating topic: rabbit-to-kafka-topic");
                adminClient.createTopics(Collections.singleton(new NewTopic("rabbit-to-kafka-topic", 1, (short) 1)))
                        .all().get(30, TimeUnit.SECONDS);
                // Wait a bit for topic to be fully created
                Thread.sleep(5000);
            }
        }
        
        // Subscribe to Kafka topic to receive the message
        kafkaConsumer.subscribe(Collections.singletonList("rabbit-to-kafka-topic"));
        System.out.println("Subscribed to Kafka topic: rabbit-to-kafka-topic");
        
        // When - publish message to RabbitMQ (Spring Cloud Stream creates queue with pattern: destination.group)
        channel.basicPublish("", "rabbit.to.kafka.queue", null, testMessage.getBytes(StandardCharsets.UTF_8));
        
        // Wait for processing (allow more time for cross-protocol bridging)
        Thread.sleep(10000);
        
        // Then - verify message arrived at Kafka with retry logic
        ConsumerRecords<String, String> records = null;
        boolean messageReceived = false;
        for (int i = 0; i < 10 && !messageReceived; i++) { // Increased retry attempts to 10
            records = kafkaConsumer.poll(Duration.ofSeconds(5));
            if (!records.isEmpty()) {
                messageReceived = true;
                System.out.println("Message received from Kafka after " + (i+1) + " attempts");
            } else {
                System.out.println("No message received from Kafka, retrying... Attempt " + (i+1));
                Thread.sleep(3000); // Increased wait time before retrying
            }
        }
        
        assertThat(messageReceived).withFailMessage("No message received from Kafka after multiple attempts").isTrue();
        assertThat(records.count()).isEqualTo(1);
        
        String receivedMessage = records.iterator().next().value();
        assertThat(receivedMessage).isEqualTo(testMessage);
    }

    @Test
    @Timeout(60) // Increased timeout for stability
    void shouldBridgeFromKafkaToRabbitMQ() throws Exception {
        // Given
        String testMessage = "Hello from Kafka to RabbitMQ!";
        
        // Create topic explicitly to ensure it exists
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            // Check if topic exists first
            Set<String> existingTopics = adminClient.listTopics().names().get(30, TimeUnit.SECONDS);
            System.out.println("Existing topics: " + existingTopics);
            
            if (!existingTopics.contains("kafka-source-topic")) {
                System.out.println("Creating topic: kafka-source-topic");
                adminClient.createTopics(Collections.singleton(new NewTopic("kafka-source-topic", 1, (short) 1)))
                        .all().get(30, TimeUnit.SECONDS);
                // Wait a bit for topic to be fully created
                Thread.sleep(5000);
            }
        }
        
        // When - publish message to Kafka
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000);
        producerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 30000);
        
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            ProducerRecord<String, String> record = new ProducerRecord<>("kafka-source-topic", testMessage);
            producer.send(record).get(30, TimeUnit.SECONDS); // Wait for send to complete
            producer.flush();
            System.out.println("Message sent to Kafka topic: kafka-source-topic");
        }
        
        // Wait for processing (allow more time for cross-protocol bridging)
        Thread.sleep(10000);
        
        // Then - verify message arrived at RabbitMQ with retry logic
        GetResponse response = null;
        for (int i = 0; i < 10 && response == null; i++) { // Increased retry attempts to 10
            response = channel.basicGet("kafka.to.rabbit.queue", true);
            if (response == null) {
                System.out.println("No message received from RabbitMQ, retrying... Attempt " + (i+1));
                Thread.sleep(3000); // Increased wait time before retrying
            } else {
                System.out.println("Message received from RabbitMQ after " + (i+1) + " attempts");
            }
        }
        
        assertThat(response).withFailMessage("No message received from RabbitMQ after multiple attempts").isNotNull();
        String receivedMessage = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(receivedMessage).isEqualTo(testMessage);
    }
}