FROM eclipse-temurin:21-jdk-jammy AS build
ARG SBT_VERSION=1.10.11
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" | tar -xz -C /opt \
    && rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/sbt/bin:${PATH}"
WORKDIR /source
COPY build.sbt ./
COPY project/build.properties project/plugins.sbt project/
RUN sbt -batch update
COPY src src
RUN sbt -batch stage

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 analytics \
    && mkdir -p /data/results && chown analytics /data/results
USER analytics
COPY --from=build /source/target/universal/stage /opt/analytics
ENV PORT=8081 \
    RESULTS_DIRECTORY=/data/results
EXPOSE 8081
HEALTHCHECK --interval=5s --timeout=3s --retries=12 CMD curl -fs http://localhost:8081/health || exit 1
ENTRYPOINT ["/opt/analytics/bin/analytics-scala"]
