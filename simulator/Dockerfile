FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY simulator/pom.xml simulator/pom.xml
COPY simulator/src simulator/src
RUN mvn package -pl simulator -am -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/simulator/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
