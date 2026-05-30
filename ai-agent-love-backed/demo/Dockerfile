# 第一阶段：Maven 打包，生成 JAR 包
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：运行环境，只复制打包好的 JAR
FROM openjdk:21-slim
WORKDIR /app
# 从构建阶段复制 JAR 包，路径完全匹配
COPY --from=builder /build/target/ai-agent-love-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8123
CMD ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]