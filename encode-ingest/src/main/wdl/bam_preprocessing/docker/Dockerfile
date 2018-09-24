FROM java:openjdk-8-alpine

RUN apk --update add wget ca-certificates && update-ca-certificates

WORKDIR /app

ENV PICARD_VERSION "2.18.14"

RUN wget -O picard.jar https://github.com/broadinstitute/picard/releases/download/${PICARD_VERSION}/picard.jar
