FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Install minimal dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy JAR and all runtime dependencies
COPY build/libs/jros2-*.jar /app/jros2.jar
COPY build/docker-libs/*.jar /app/libs/

# Copy test source files
COPY integration-tests/docker-whitelist/TestPublisher.java /app/
COPY integration-tests/docker-whitelist/TestSubscriber.java /app/

# Compile test programs inside the container
RUN javac -cp "/app/jros2.jar:/app/libs/*" TestPublisher.java TestSubscriber.java

# Default command
CMD ["bash"]
