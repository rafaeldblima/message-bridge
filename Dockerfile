FROM openjdk:24-jdk-slim

LABEL maintainer="Message Bridge Service"
LABEL version="1.0.0"
LABEL description="Spring Cloud Stream service for bridging messages between RabbitMQ queues"

WORKDIR /app

COPY gradle ./gradle
COPY gradlew build.gradle ./
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew build --no-daemon -x test

# Better way to check for JAR files and move them
RUN if ls build/libs/*.jar 1> /dev/null 2>&1; then \
        echo "JAR files found:"; \
        ls -la build/libs/*.jar; \
        # Take the first JAR file found (or use a more specific pattern if needed)
        FIRST_JAR=$(ls build/libs/*.jar | head -n 1); \
        echo "Moving $FIRST_JAR to app.jar"; \
        mv "$FIRST_JAR" app.jar; \
    else \
        echo "JAR file not found. Build may have failed."; \
        ls -la build/libs; \
        exit 1; \
    fi

RUN rm -rf gradle gradlew build.gradle src build

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SPRING_PROFILES_ACTIVE="docker"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]