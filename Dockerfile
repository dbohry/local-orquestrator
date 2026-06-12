FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY build/libs/local-orquestrator.jar app.jar

ENTRYPOINT ["java", "-Xmx32m", "-Xss512k", "-XX:MaxMetaspaceSize=48m", "-XX:ReservedCodeCacheSize=16m", "-XX:+UseSerialGC", "-jar", "app.jar"]
