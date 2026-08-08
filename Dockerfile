# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
# Copy pom and fetch dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/paydost-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
