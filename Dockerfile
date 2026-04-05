# Multi-stage build for Render or any container host (JDK 21 / Spring Boot 4)
# Build: docker build -t workflow-operation-api .
# Run:  docker run -p 8080:8080 -e PORT=8080 -e SPRING_DATASOURCE_URL=... workflow-operation-api

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests -Djacoco.skip=true package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/workflow-operation-api-*.jar app.jar
USER spring
ENV JAVA_OPTS=""
# Render sets PORT; Spring reads server.port from env via application.yml
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
