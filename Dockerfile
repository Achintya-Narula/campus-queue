FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S campusqueue && adduser -S campusqueue -G campusqueue
WORKDIR /app
COPY --from=build /workspace/target/campus-queue-2.0.0-SNAPSHOT.jar app.jar

USER campusqueue
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
