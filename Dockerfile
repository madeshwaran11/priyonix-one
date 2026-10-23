FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system priyonix && useradd --system --gid priyonix priyonix
COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER priyonix
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
