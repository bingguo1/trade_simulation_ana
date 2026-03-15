FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY consumer-monitor/pom.xml consumer-monitor/pom.xml
COPY consumer-monitor/src consumer-monitor/src
RUN mvn package -pl consumer-monitor -am -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/consumer-monitor/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
