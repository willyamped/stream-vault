# Start from an official Maven image
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml and download dependencies first (for better caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code
COPY src ./src

# Run tests during build
RUN mvn test

# Build the application
RUN mvn clean package -DskipTests

# Run the app in a lightweight JDK image
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

RUN apk add --no-cache ffmpeg

EXPOSE 8080
# Wait for Elasticsearch to be ready
ENTRYPOINT ["sh", "-c", "echo 'Delaying startup...'; sleep 7; java -jar app.jar"]
