FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY event-gateway/pom.xml event-gateway/pom.xml
COPY account-service/pom.xml account-service/pom.xml
RUN ./mvnw -pl account-service -am dependency:go-offline -q

COPY account-service/src account-service/src
RUN ./mvnw -pl account-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /event-ledger/account-service/target/account-service*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]