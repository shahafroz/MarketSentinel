# Build stage: compile and package the Spring Boot jar.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# Run stage: a small JRE image, running as a non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 sentinel
WORKDIR /app
COPY --from=build /app/target/backend-*.jar app.jar
USER sentinel
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
