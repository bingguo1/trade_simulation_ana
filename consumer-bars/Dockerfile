FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY consumer-bars/pom.xml consumer-bars/pom.xml
COPY consumer-bars/src consumer-bars/src
RUN mvn package -pl consumer-bars -am -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/consumer-bars/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
