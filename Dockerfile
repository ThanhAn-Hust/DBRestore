# Stage 1: Build JAR using Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY src/main ./src/main
RUN mvn clean package -DskipTests -B

# Stage 2: Minimalist Runtime Image
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="db-backup" \
      org.opencontainers.image.description="Automated Streaming Database Backup & Selective Restore CLI" \
      org.opencontainers.image.source="https://github.com/dbbackup/db-backup"

WORKDIR /app

# Install Database Client CLIs and dependencies
RUN apk add --no-cache \
    mysql-client \
    postgresql-client \
    bash \
    tzdata \
    ca-certificates

COPY --from=builder /build/target/db-backup-*.jar /app/db-backup.jar
COPY docker-entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

VOLUME ["/root/.db-backup", "/backups"]

ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["help"]
