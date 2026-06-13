FROM eclipse-temurin:25-jdk-alpine AS jlink

RUN jlink \
    --add-modules java.base,java.logging,java.naming,java.management \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6 \
    --output /custom-jre

FROM alpine:3.23

COPY --from=jlink /custom-jre /opt/java
ENV PATH="/opt/java/bin:${PATH}"

WORKDIR /app
COPY build/libs/local-orquestrator.jar app.jar

ENTRYPOINT ["java", "-Xmx32m", "-Xss512k", "-XX:MaxMetaspaceSize=48m", "-XX:ReservedCodeCacheSize=16m", "-XX:+UseSerialGC", "-jar", "app.jar"]
