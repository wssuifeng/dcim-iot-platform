# 阶段1：构建可执行 jar
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# 先复制 Maven 包装器与 pom.xml，利用 Docker 层缓存依赖
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B

# 再复制源码并打包（跳过测试，CI 中已执行测试）
COPY src src
RUN ./mvnw package -DskipTests -B

# 阶段2：运行环境使用更小的 JRE 镜像
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制 jar
COPY --from=builder /build/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# 健康检查：访问 Actuator health 端点
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
