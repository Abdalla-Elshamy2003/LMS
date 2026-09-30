FROM golang:1.24.8-alpine AS build
RUN apk add --no-cache git
WORKDIR /src/minio
RUN git init && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth 1 origin 9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a && git checkout FETCH_HEAD
RUN CGO_ENABLED=0 go build -trimpath -o /out/minio .
WORKDIR /src/mc
RUN git init && git remote add origin https://github.com/minio/mc.git \
    && git fetch --depth 1 origin d6541ea280b73a834b64d4097e21f2be77676104 && git checkout FETCH_HEAD
RUN CGO_ENABLED=0 go build -trimpath -o /out/mc .

FROM alpine:3.22 AS client
RUN apk add --no-cache ca-certificates
COPY --from=build /out/mc /usr/local/bin/mc
ENTRYPOINT ["mc"]

FROM alpine:3.22 AS server
RUN apk add --no-cache ca-certificates curl
COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /out/mc /usr/local/bin/mc
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
