# Image công cụ: kind + kubectl + docker CLI.
# Dùng docker socket của máy host -> cluster kind chạy ngay trên Docker của host,
# không cần cài kind/kubectl lên máy.
FROM alpine:3.20

ARG KIND_VERSION=v0.27.0
ARG KUBECTL_VERSION=v1.32.2
ARG TARGETARCH

RUN apk add --no-cache bash curl ca-certificates docker-cli docker-cli-buildx \
 && curl -fsSLo /usr/local/bin/kind \
      "https://github.com/kubernetes-sigs/kind/releases/download/${KIND_VERSION}/kind-linux-${TARGETARCH}" \
 && curl -fsSLo /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl" \
 && chmod +x /usr/local/bin/kind /usr/local/bin/kubectl

COPY kind.sh /usr/local/bin/kind.sh
RUN chmod +x /usr/local/bin/kind.sh

WORKDIR /work
ENTRYPOINT ["/usr/local/bin/kind.sh"]
CMD ["up"]
