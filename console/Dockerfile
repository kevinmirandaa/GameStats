FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

FROM eclipse-temurin:21-jre-alpine
RUN adduser -D -u 10001 console
WORKDIR /app
COPY --from=build /source/console-server/target/console-server.jar /app/console-server.jar
COPY --from=build /source/game-connect4/target/game-connect4-1.0.0.jar /app/plugins/game-connect4.jar
COPY --from=build /source/game-memory/target/game-memory-1.0.0.jar /app/plugins/game-memory.jar
COPY --from=build /source/game-minesweeper/target/game-minesweeper-1.0.0.jar /app/plugins/game-minesweeper.jar
USER console
ENV PORT=8100 PLUGINS_DIRECTORY=/app/plugins
EXPOSE 8100
HEALTHCHECK --interval=5s --timeout=3s --retries=20 CMD wget -qO- http://localhost:8100/api/health >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/console-server.jar"]
