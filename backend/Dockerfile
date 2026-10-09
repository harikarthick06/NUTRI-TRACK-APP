# Build the API jar, then run it on a slim Java 21 runtime as a non-root user.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home nutritrack
WORKDIR /app
COPY --from=build /src/target/nutritrack-backend-1.0.0.jar app.jar
USER nutritrack
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
