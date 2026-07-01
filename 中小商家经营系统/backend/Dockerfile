ARG SERVICE

FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/catalog-service/pom.xml backend/catalog-service/pom.xml
COPY backend/inventory-service/pom.xml backend/inventory-service/pom.xml
COPY backend/pos-service/pom.xml backend/pos-service/pom.xml
COPY backend/report-service/pom.xml backend/report-service/pom.xml
COPY backend backend
WORKDIR /workspace/backend
RUN mvn -pl ${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/backend/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
