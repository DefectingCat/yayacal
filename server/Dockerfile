FROM rust:1.98.1-trixie AS builder
WORKDIR /build
COPY Cargo.toml Cargo.lock build.rs build_support.rs ./
COPY src ./src
COPY migrations ./migrations
ARG YAYA_GIT_SHA
ENV YAYA_GIT_SHA=${YAYA_GIT_SHA} YAYA_REQUIRE_GIT_SHA=1
RUN cargo build --release --locked --jobs 2

FROM debian:trixie-slim
ARG YAYA_GIT_SHA
LABEL org.opencontainers.image.title="YaYa Server" \
      org.opencontainers.image.revision=${YAYA_GIT_SHA}
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
RUN mkdir -p /data/media && chown 10001:10001 /data/media
COPY --from=builder /build/target/release/yayacal-server /usr/local/bin/
USER 10001:10001
ENV BIND_ADDR=0.0.0.0:8088 MEDIA_DIR=/data/media
EXPOSE 8088
ENTRYPOINT ["yayacal-server"]
