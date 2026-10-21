ARG IMAGE_REGISTRY=docker.io

FROM ${IMAGE_REGISTRY}/library/eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline

COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp clean verify \
    && cp target/gobang-backend-*.jar /workspace/app.jar

FROM ${IMAGE_REGISTRY}/library/eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S gobang \
    && adduser -S -G gobang -h /app gobang

WORKDIR /app

COPY --from=build --chown=gobang:gobang /workspace/app.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai"

USER gobang

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=10 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
