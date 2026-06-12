FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache openssh-client

WORKDIR /app

COPY build/libs/local-orquestrator.jar app.jar

RUN mkdir -p /root/.ssh && chmod 700 /root/.ssh

ENTRYPOINT ["java", "-jar", "app.jar"]
