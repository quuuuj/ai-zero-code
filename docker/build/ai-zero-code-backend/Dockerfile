# syntax=docker/dockerfile:1
# 后端 Dockerfile — 多阶段构建
# 阶段1：Maven 编译
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY ai-zero-code-backend/pom.xml .
RUN --mount=type=cache,target=/root/.m2,id=maven-cache \
    mvn dependency:go-offline -B -q 2>/dev/null || true
COPY ai-zero-code-backend/src/ src/
RUN --mount=type=cache,target=/root/.m2,id=maven-cache \
    mvn package -DskipTests -B -q

# 阶段2：JRE 运行
FROM eclipse-temurin:21-jre-jammy
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /opt/app --create-home app

WORKDIR /opt/app
COPY --from=builder --chown=app:app /build/target/ai-zero-code-*.jar app.jar

USER app
EXPOSE 7000

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --fail --silent --show-error http://127.0.0.1:7000/api/actuator/health > /dev/null || exit 1

ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
