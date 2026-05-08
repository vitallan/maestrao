FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

COPY src/ src/

RUN ./mvnw -Pproduction -DskipTests package


FROM eclipse-temurin:21-jre

WORKDIR /app

ARG APP_VERSION
ENV MAESTRAO_APP_VERSION=$APP_VERSION

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
