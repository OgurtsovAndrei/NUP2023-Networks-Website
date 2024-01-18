# Dockerfile
FROM openjdk:20-jdk-slim-buster

RUN apt-get update && apt-get install -y libpcap-dev
# Set application directory
WORKDIR /app-2

# Copy jar file into the container
COPY ./build/libs/*all.jar app-2.jar

# Default command when running the container
CMD ["java", "-jar", "app-2.jar"]