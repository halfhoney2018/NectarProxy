# Agent runtime image (JDK 21)
FROM eclipse-temurin:21-jre

# Security: run as non-root
RUN useradd -ms /bin/bash appuser
USER appuser

WORKDIR /opt/app

# Copy application jar (expect it to be built before building image)
# Example: ./gradlew :agent:bootJar
COPY ../build/libs/agent-*.jar app.jar

# Optional mount points for external config and logs
VOLUME ["/config", "/logs"]

# Agent is non-web; no port exposure required

# JVM & Spring Boot defaults for containers
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75"
ENV SPRING_CONFIG_ADDITIONAL_LOCATION="file:/config/"
ENV SPRING_APPLICATION_NAME="agent"

ENTRYPOINT ["java","-jar","/opt/app/app.jar"]
