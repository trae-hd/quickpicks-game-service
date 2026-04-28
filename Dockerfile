# Stage 1: Build
FROM gradle:8-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
EXPOSE 8080
COPY --from=build /home/gradle/src/build/libs/*.jar /app/quickpicks-game-service.jar
ENTRYPOINT ["java", "-jar", "/app/quickpicks-game-service.jar"]
