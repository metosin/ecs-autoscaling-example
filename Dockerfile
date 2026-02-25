FROM clojure:temurin-21-tools-deps-alpine AS builder

WORKDIR /app

ARG BUILD_TARGET=uber-web

# Cache dependency resolution
COPY deps.edn ./
RUN clojure -P

# Copy source and build
COPY build.clj ./
COPY src/ src/

RUN clojure -T:build ${BUILD_TARGET}

# --- Runtime ---
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
