FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -f pom.xml dependency:go-offline -Dmaven.repo.local=.m2/repository -q || true
COPY src ./src
RUN mvn -B -f pom.xml -DskipTests package -Dmaven.repo.local=.m2/repository

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
