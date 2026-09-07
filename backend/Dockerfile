# Multi-stage build: compile with a JDK image, run with a JRE image.
# Keeps the shipped image small and reproducible (NFR-001, PRN-006).

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies in a separate layer so source-only changes recompile faster.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# Non-root for the demo.
RUN useradd -r -u 10001 sopdemo
COPY --from=build /app/target/sopdemo-backend.jar app.jar
USER sopdemo
EXPOSE 8080
# Compose healthcheck runs the liveness endpoint.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO /dev/null http://localhost:8080/healthz || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
