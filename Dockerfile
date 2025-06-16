# Stage 1: Build the JAR
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Run the JAR
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=build /app/target/randomchat-0.0.1-SNAPSHOT.jar randomchat.jar
EXPOSE 8082

# stage 3: Run the application
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "randomchat.jar"]
