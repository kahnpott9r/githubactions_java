# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Tải dependencies trước để tận dụng cache của Docker layer
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /workspace/target/todo-report.jar app.jar

ENV PORT=8080
ENV NODE_API_BASE_URL=http://localhost:3000

EXPOSE 8080

# Không chạy bằng root
RUN addgroup -S app && adduser -S app -G app
USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
