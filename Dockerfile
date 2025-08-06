FROM openjdk:21-jdk-slim

LABEL maintainer="Message Bridge Service"
LABEL version="1.0.0"
LABEL description="Spring Cloud Stream service for bridging messages between RabbitMQ queues"

WORKDIR /app

COPY gradle ./gradle
COPY gradlew build.gradle ./
COPY src ./src

RUN chmod +x ./gradlew

RUN ./gradlew build --no-daemon -x test

RUN mv build/libs/*.jar app.jar

RUN rm -rf gradle gradlew build.gradle src build

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SPRING_PROFILES_ACTIVE="docker"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]