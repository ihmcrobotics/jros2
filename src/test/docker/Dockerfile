FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Install minimal dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy JAR and runtime dependencies (docker-libs may be empty when there are no third-party jars)
COPY build/libs/jros2-*.jar /app/jros2.jar
RUN mkdir -p /app/libs
COPY build/docker-libs/ /app/libs/

# Copy test source files
COPY src/test/docker/TestPublisher.java /app/
COPY src/test/docker/TestSubscriber.java /app/

# Compile test programs inside the container
RUN javac -cp "/app/jros2.jar:/app/libs/*" TestPublisher.java TestSubscriber.java

# Default command
CMD ["bash"]
