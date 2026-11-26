# Stage 1: build the server with the full JDK.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
# The tests already ran on your computer; skipping them here keeps the build fast.
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# Stage 2: run it on a small image that has only the Java runtime.
FROM eclipse-temurin:25-jre
WORKDIR /app
# Don't run as root inside the container.
RUN useradd --system --uid 1001 app
# bootJar also makes a "-plain.jar" without the libraries; this picks the runnable one.
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
USER app
# Render's free plan has 512 MB. Let Java use most of it, not more.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]