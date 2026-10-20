FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
   COPY backend/ .
RUN chmod +x gradlew && ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8081
CMD ["java", "-Xmx384m", "-jar", "app.jar"]
