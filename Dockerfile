FROM eclipse-temurin:21-jdk-alpine@sha256:fd10ef3691adde33aa57cd1070eedd4ecbe7eff025e0bc82503fdd15e0e70f47 AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:693c22ea458d62395bac47a2da405d0d18c77b205211ceec4846a550a37684b6
RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/clockify-http-actions-1.0.0-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
