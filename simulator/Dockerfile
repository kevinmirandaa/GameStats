FROM golang:1.22-alpine AS build
WORKDIR /source
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /simulator ./cmd/simulator

FROM alpine:3.20
RUN adduser -D -u 10001 simulator && mkdir /inbox && chown simulator /inbox
USER simulator
COPY --from=build /simulator /usr/local/bin/simulator
ENV SIMULATOR_TARGET_URL=http://ingest-go:8080
ENTRYPOINT ["simulator"]
