FROM swipl:9.2.9 AS verified
WORKDIR /app
COPY connect4.pl server.pl ./
COPY tests tests
RUN swipl -g run_tests -t halt tests/connect4_tests.pl

FROM swipl:9.2.9
WORKDIR /app
COPY --from=verified /app/connect4.pl /app/server.pl ./
RUN useradd --system --uid 10001 prolog
USER prolog
ENV PORT=8090
EXPOSE 8090
HEALTHCHECK --interval=5s --timeout=3s --retries=20 CMD ["swipl", "-g", "use_module(library(http/http_open)),http_open('http://localhost:8090/health',S,[]),close(S)", "-t", "halt"]
CMD ["swipl", "server.pl"]
