#!/usr/bin/env bash
# Quản lý cluster kind + ArgoCD, chạy bên trong container (xem k8s/docker-compose.yml).
# Manifest app và ArgoCD Application nằm ở repo githubactions_gitops (mount vào /src/githubactions_gitops).
#
#   up      Tạo cluster (nếu chưa có), cài ArgoCD, build + load image 2 app, deploy
#   down    Xoá cluster
#   status  Xem trạng thái cluster / ArgoCD / app
#   <lệnh>  Chạy lệnh bất kỳ (vd: kubectl get pods -A) với kubeconfig của cluster
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-todo}"
ARGOCD_VERSION="${ARGOCD_VERSION:-v3.1.8}"
ARGOCD_REPO_URL="${ARGOCD_REPO_URL:-https://github.com/kahnpott9r/githubactions_gitops.git}"
ARGOCD_REVISION="${ARGOCD_REVISION:-main}"
# true: build image 2 app từ source local và load vào kind (không cần GHCR)
BUILD_LOCAL_IMAGES="${BUILD_LOCAL_IMAGES:-true}"
# true: kubectl apply manifest app ngay, không chờ ArgoCD đọc được repo trên GitHub
APPLY_APPS_NOW="${APPLY_APPS_NOW:-true}"

K8S_DIR=/src/githubactions_java/k8s
# Repo githubactions_gitops: manifest app + ArgoCD Application (nguồn sự thật duy nhất)
GITOPS_DIR=/src/githubactions_gitops
NODE_SRC=/src/githubactions
JAVA_SRC=/src/githubactions_java

# Tag image lấy đúng từ images[].newTag trong kustomization của repo gitops,
# để image build local trùng với image mà manifest đang trỏ tới.
image_tag() { sed -n "s/^ *newTag: *//p" "$GITOPS_DIR/apps/$1/kustomization.yaml" | head -n1; }
NODE_IMAGE="ghcr.io/kahnpott9r/githubactions:$(image_tag node-app)"
JAVA_IMAGE="ghcr.io/kahnpott9r/githubactions_java:$(image_tag java-app)"

# Kubeconfig cho máy host (API server 127.0.0.1:6550) -> dùng được với kubectl local
HOST_KUBECONFIG=/work/.kube/config
# Kubeconfig nội bộ cho container này (đi qua docker network "kind")
INTERNAL_KUBECONFIG=/tmp/kubeconfig-internal

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

cluster_exists() { kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; }

use_cluster() {
  # Container này phải vào cùng network "kind" mới gọi được API server.
  docker network connect kind "$(hostname)" >/dev/null 2>&1 || true
  kind get kubeconfig --name "$CLUSTER_NAME" --internal > "$INTERNAL_KUBECONFIG"
  export KUBECONFIG="$INTERNAL_KUBECONFIG"
}

create_cluster() {
  if cluster_exists; then
    log "Cluster '$CLUSTER_NAME' đã tồn tại -> bỏ qua bước tạo"
  else
    log "Tạo cluster kind '$CLUSTER_NAME'"
    kind create cluster --name "$CLUSTER_NAME" --config "$K8S_DIR/kind/cluster.yaml" --wait 120s
  fi
  mkdir -p "$(dirname "$HOST_KUBECONFIG")"
  kind get kubeconfig --name "$CLUSTER_NAME" > "$HOST_KUBECONFIG"
  use_cluster
}

install_argocd() {
  log "Cài ArgoCD $ARGOCD_VERSION"
  kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -n argocd --server-side --force-conflicts \
    -f "https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml" >/dev/null

  # Mở ArgoCD UI qua NodePort 30443 (-> https://localhost:8443 trên host)
  kubectl -n argocd patch svc argocd-server --type merge -p \
    '{"spec":{"type":"NodePort","ports":[{"name":"http","port":80,"targetPort":8080,"nodePort":30081},{"name":"https","port":443,"targetPort":8080,"nodePort":30443}]}}' >/dev/null

  log "Chờ ArgoCD sẵn sàng"
  kubectl -n argocd rollout status deploy/argocd-server --timeout=300s
  kubectl -n argocd rollout status deploy/argocd-repo-server --timeout=300s
  kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=300s
}

load_images() {
  [ "$BUILD_LOCAL_IMAGES" = "true" ] || return 0
  log "Build image 2 app từ source local"
  docker build -q -t "$NODE_IMAGE" "$NODE_SRC"
  docker build -q -t "$JAVA_IMAGE" "$JAVA_SRC"
  log "Load image vào cluster kind"
  kind load docker-image --name "$CLUSTER_NAME" "$NODE_IMAGE" "$JAVA_IMAGE"
}

deploy_apps() {
  if [ "$APPLY_APPS_NOW" = "true" ]; then
    log "Deploy 2 app trực tiếp (kubectl apply)"
    kubectl apply -k "$GITOPS_DIR/apps/node-app"
    kubectl apply -k "$GITOPS_DIR/apps/java-app"
  fi

  log "Tạo ArgoCD Application (repo: $ARGOCD_REPO_URL @ $ARGOCD_REVISION)"
  sed -e "s#repoURL: .*#repoURL: ${ARGOCD_REPO_URL}#" -e "s#targetRevision: .*#targetRevision: ${ARGOCD_REVISION}#" \
    "$GITOPS_DIR/argocd/applications.yaml" | kubectl apply -f -

  if [ "$APPLY_APPS_NOW" = "true" ]; then
    log "Chờ 2 app chạy"
    kubectl -n todo rollout status deploy/node-app --timeout=180s
    kubectl -n todo rollout status deploy/java-app --timeout=300s
  fi
}

print_info() {
  local password
  password=$(kubectl -n argocd get secret argocd-initial-admin-secret \
    -o jsonpath='{.data.password}' 2>/dev/null | base64 -d || echo "(đã xoá secret)")
  cat <<EOF

✅ Xong!
   ArgoCD UI : https://localhost:8443   (user: admin / pass: ${password})
   Node app  : http://localhost:30000
   Java app  : http://localhost:30080/api/report
   Kubeconfig cho máy host: k8s/.kube/config
     export KUBECONFIG=\$PWD/k8s/.kube/config && kubectl get pods -A
EOF
}

status() {
  cluster_exists || { echo "Cluster '$CLUSTER_NAME' chưa được tạo."; exit 0; }
  use_cluster
  kubectl get nodes
  echo; kubectl -n argocd get applications 2>/dev/null || true
  echo; kubectl -n todo get deploy,svc,pods 2>/dev/null || true
}

cmd="${1:-up}"
case "$cmd" in
  up)
    create_cluster
    install_argocd
    load_images
    deploy_apps
    print_info
    ;;
  down)
    log "Xoá cluster '$CLUSTER_NAME'"
    kind delete cluster --name "$CLUSTER_NAME"
    rm -f "$HOST_KUBECONFIG"
    ;;
  status)
    status
    ;;
  *)
    cluster_exists && use_cluster
    exec "$@"
    ;;
esac
