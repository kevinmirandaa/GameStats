FROM golang:1.22-alpine AS build
WORKDIR /source
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /ingest ./cmd/ingest

FROM alpine:3.20
RUN adduser -D -u 10001 ingest && mkdir /data /inbox && chown ingest /data /inbox
USER ingest
COPY --from=build /ingest /usr/local/bin/ingest
ENV PORT=8080 \
    ANALYTICS_URL=http://analytics-scala:8081 \
    REJECTED_EVENTS_PATH=/data/rejected-events.log
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --retries=10 CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["ingest"]
