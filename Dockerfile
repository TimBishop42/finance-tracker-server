# Use a base image with Java pre-installed
FROM openjdk:17-jre-slim

# Set the working directory in the container
WORKDIR /app

# Copy the Spring Boot application JAR file into the container
COPY build/libs/*.jar ./app.jar

# Expose the port your application listens on (if needed)
EXPOSE 8080

# Command to run the Spring Boot application when the container starts
CMD ["java", "-jar", "app.jar"]