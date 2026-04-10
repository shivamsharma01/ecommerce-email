# Multi-stage: build with JDK, run with JRE.

FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
	&& ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test \
	&& JAR_FILE=$(ls build/libs/*.jar | grep -v plain | head -n1) \
	&& cp "$JAR_FILE" /app/app.jar

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S -g 1000 spring && adduser -S -u 1000 -G spring spring

WORKDIR /app
COPY --from=builder --chown=spring:spring /app/app.jar /app/app.jar

USER spring:spring

EXPOSE 8090

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
