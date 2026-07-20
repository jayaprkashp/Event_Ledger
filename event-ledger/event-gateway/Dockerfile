FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY event-gateway/pom.xml event-gateway/pom.xml
COPY account-service/pom.xml account-service/pom.xml
RUN ./mvnw -pl event-gateway -am dependency:go-offline -q

COPY event-gateway/src event-gateway/src
RUN ./mvnw -pl event-gateway -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /event-ledger/event-gateway/target/event-gateway*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]