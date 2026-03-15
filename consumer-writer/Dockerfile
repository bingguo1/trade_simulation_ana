FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY consumer-writer/pom.xml consumer-writer/pom.xml
COPY consumer-writer/src consumer-writer/src
RUN mvn package -pl consumer-writer -am -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/consumer-writer/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
