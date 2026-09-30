FROM rust:1.98.1-trixie AS builder
WORKDIR /build
COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY migrations ./migrations
RUN cargo build --release --locked

FROM debian:trixie-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 moments && mkdir -p /data/media && chown moments /data/media
COPY --from=builder /build/target/release/yayacal-server /usr/local/bin/
USER moments
ENV BIND_ADDR=0.0.0.0:8088 MEDIA_DIR=/data/media
EXPOSE 8088
ENTRYPOINT ["yayacal-server"]
