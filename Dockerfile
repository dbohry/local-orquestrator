FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY build/libs/local-orquestrator.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
