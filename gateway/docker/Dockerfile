# Gateway runtime image (JDK 21)
FROM eclipse-temurin:21-jre

# Security: run as non-root
RUN useradd -ms /bin/bash appuser
USER appuser

WORKDIR /opt/app

# Copy application jar (expect it to be built before building image)
# Example: ./gradlew :gateway:bootJar
COPY ../build/libs/gateway-*.jar app.jar

# Default config/logs mount points
VOLUME ["/config", "/logs"]

# HTTP (and RSocket-over-WebSocket) port
EXPOSE 9000

# JVM & Spring Boot defaults for containers
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75"
ENV SPRING_CONFIG_ADDITIONAL_LOCATION="file:/config/"
ENV SPRING_APPLICATION_NAME="gateway"

ENTRYPOINT ["java","-jar","/opt/app/app.jar"]
