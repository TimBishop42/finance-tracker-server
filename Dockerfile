# Use a maintained Java runtime image
FROM eclipse-temurin:17-jre-jammy

# Set the working directory in the container
WORKDIR /app

# Define the log directory (referenced by logback-spring.xml via ${LOGS})
ENV LOGS=/var/log/finance-tracker

# Copy the Spring Boot application JAR file into the container
COPY build/libs/*.jar ./app.jar

# Expose the port your application listens on (if needed)
EXPOSE 8080

# Command to run the Spring Boot application when the container starts
CMD ["java", "-jar", "app.jar"]