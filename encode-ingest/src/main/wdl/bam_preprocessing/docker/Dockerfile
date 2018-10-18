FROM java:openjdk-8-alpine

RUN apk --update add \
    bash \
    ca-certificates \
    libc6-compat \
    wget && \
  update-ca-certificates

WORKDIR /app

ARG PICARD_VERSION

RUN wget -O picard.jar https://github.com/broadinstitute/picard/releases/download/${PICARD_VERSION}/picard.jar
