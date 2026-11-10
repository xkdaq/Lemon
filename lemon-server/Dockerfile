# ---------- 构建 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
# 先只复制依赖描述，利用镜像层缓存：pom 不变时不重复下载依赖
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests && cp target/lemon-server-*.jar /app.jar

# ---------- 运行 ----------
FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
RUN groupadd --system lemon && useradd --system --gid lemon --home /app lemon \
    && mkdir -p /data/uploads && chown -R lemon:lemon /data
WORKDIR /app
COPY --from=build /app.jar app.jar
USER lemon
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
