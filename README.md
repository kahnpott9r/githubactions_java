# githubactions_java — Todo Report Service

Service **Spring Boot (Java 21)** dùng để học CI/CD với GitHub Actions. Service này **tích hợp với app Node.js** trong repo [`githubactions`](https://github.com/kahnpott9r/githubactions): nó gọi API `/api/todos`, `/api/health`, `/api/info` của app Node rồi trả về báo cáo tổng hợp.

```
┌──────────────────────┐   HTTP (JSON)   ┌──────────────────────────┐
│ githubactions_java   │ ──────────────▶ │ githubactions (Node.js)  │
│ :8080 /api/report    │                 │ :3000 /api/todos, health │
└──────────────────────┘                 └──────────────────────────┘
```

## API

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/report` | Tổng số việc, số việc đã xong / chưa xong, % hoàn thành, danh sách việc còn lại |
| GET | `/api/upstream` | Thông tin build của app Node đang được kết nối |
| GET | `/actuator/health` | Health check của service Java |

Khi không gọi được app Node, service trả về `502 Bad Gateway` kèm JSON lỗi.

## Chạy local

Yêu cầu: JDK 21+ và Node 20+. Không cần cài Maven vì repo đã có Maven wrapper (`./mvnw`).

```bash
# 1. Chạy app Node (repo bên cạnh)
cd ../githubactions && npm ci && npm start

# 2. Chạy app Java
cd ../githubactions_java
NODE_API_BASE_URL=http://localhost:3000 ./mvnw spring-boot:run
curl http://localhost:8080/api/report
```

### Chạy 2 app bằng Docker Compose

```bash
docker compose up --build -d
```

| Service | URL trên host |
|---|---|
| `node-app` | http://localhost:3000 (build từ `../githubactions`) |
| `java-app` | http://localhost:8080/api/report |

## Kubernetes local: kind + ArgoCD ([k8s/](k8s/))

Mọi thứ chạy bằng Docker Compose, **không cần cài kind, kubectl hay helm lên máy**. Container `kind` dùng docker socket của máy nên các node kind chạy ngay trên Docker của host.

```bash
docker compose -f k8s/docker-compose.yml up --build            # tạo cluster + cài ArgoCD + deploy 2 app
docker compose -f k8s/docker-compose.yml run --rm kind status  # xem trạng thái
docker compose -f k8s/docker-compose.yml run --rm kind kubectl get pods -A
docker compose -f k8s/docker-compose.yml run --rm kind down    # xoá cluster
```

| Thành phần | URL |
|---|---|
| ArgoCD UI | https://localhost:8443 (user `admin`, mật khẩu in ở cuối log của lệnh `up`) |
| Node app | http://localhost:30000 |
| Java app | http://localhost:30080/api/report |

Nếu máy có sẵn `kubectl`, có thể dùng trực tiếp kubeconfig mà lệnh `up` xuất ra: `export KUBECONFIG=$PWD/k8s/.kube/config`.

### Cấu trúc thư mục

| Đường dẫn | Nội dung |
|---|---|
| `k8s/bootstrap/` | Image công cụ (kind v0.27, kubectl v1.32, docker CLI) và script [kind.sh](k8s/bootstrap/kind.sh) |
| `k8s/kind/cluster.yaml` | Cluster 1 node, map các NodePort ra host |
| [`../githubactions_gitops`](https://github.com/kahnpott9r/githubactions_gitops) | **Repo riêng**: manifest 2 app (`apps/*`) và ArgoCD Application (`argocd/`). Được mount vào container `kind` |

### Luồng GitOps

```
push code ──▶ GitHub Actions ──▶ image ghcr.io/...:sha-<commit>
                    └─ job update-gitops ──▶ commit newTag vào githubactions_gitops
                                               └─▶ ArgoCD (trong kind) tự sync ──▶ cluster
```

Cần clone repo `githubactions_gitops` cạnh repo này (`../githubactions_gitops`). Mặc định, lệnh `up` **build image từ source local, gắn đúng tag đang khai báo trong `githubactions_gitops/apps/*/kustomization.yaml`, rồi `kind load`** vào cluster (`BUILD_LOCAL_IMAGES=true`), và **apply manifest ngay** (`APPLY_APPS_NOW=true`). Nhờ vậy app chạy được kể cả khi repo chưa push lên GitHub hoặc GHCR chưa có image.

ArgoCD chỉ sync được khi repo `githubactions_gitops` đã có trên GitHub và đọc được:
- **Repo public:** không cần làm gì thêm.
- **Repo private:** thêm repo vào ArgoCD (Settings → Repositories) bằng fine-grained PAT chỉ có quyền Contents: Read-only.

Image trên GHCR mặc định là private. Hoặc chuyển package sang public, hoặc tạo `imagePullSecret`.

Các biến môi trường (đặt trong `k8s/.env`): `CLUSTER_NAME`, `ARGOCD_VERSION`, `ARGOCD_REPO_URL`, `ARGOCD_REVISION`, `BUILD_LOCAL_IMAGES`, `APPLY_APPS_NOW`.

## Test

| Loại | Lệnh | Cần app Node? |
|---|---|---|
| Unit test (`*Test.java`) | `./mvnw verify` | Không, Node API được giả lập bằng `MockRestServiceServer` |
| Integration test (`*IT.java`) | `NODE_API_BASE_URL=http://localhost:3000 ./mvnw -Pintegration verify` | **Có**, chạy với app Node thật |

Integration test đóng vai trò **contract test**: nếu repo Node thay đổi format JSON hoặc hành vi API, test bên Java sẽ fail.

## Pipeline CI ([.github/workflows/ci.yml](.github/workflows/ci.yml))

```
unit-test ──▶ integration-test ──┬──▶ build  (jar artifact)
                  │              └──▶ docker (build image → push ghcr.io khi push vào main)
                  └─ checkout repo githubactions → npm start → chờ /api/health → ./mvnw -Pintegration verify
```

### Docker image trên GHCR

| Sự kiện | Hành động |
|---|---|
| `push` vào `main` | Build và **push** `ghcr.io/kahnpott9r/githubactions_java` với tag `latest`, `sha-<short>`, `main` |
| Pull Request, `repository_dispatch`, `workflow_dispatch` | Chỉ build để kiểm tra Dockerfile, **không push** |

Đăng nhập GHCR bằng `GITHUB_TOKEN`, với quyền `packages: write` chỉ cấp cho job `docker`, nên không cần secret riêng. Repo Node `githubactions` có job tương tự, push lên `ghcr.io/kahnpott9r/githubactions`.

```bash
docker pull ghcr.io/kahnpott9r/githubactions_java:latest
```

> Lần push đầu tiên, package mặc định là **private**. Muốn ai cũng pull được thì vào GitHub → Packages → package → Package settings → Change visibility.

Pipeline được kích hoạt khi:
- Có `push` hoặc `pull_request` vào `main` của repo này.
- Chạy thủ công bằng `workflow_dispatch`, có thể chọn `node_ref` (branch/tag/SHA của repo Node).
- Nhận `repository_dispatch` (`node-app-updated`) do repo **githubactions** gửi sang sau mỗi lần build xanh trên `main`. Khi đó integration test chạy với **đúng commit Node vừa build**.

## Cấu hình secrets

| Repo | Secret | Khi nào cần | Quyền tối thiểu (fine-grained PAT) |
|---|---|---|---|
| `githubactions` (Node) | `JAVA_REPO_DISPATCH_TOKEN` | Để repo Node trigger CI của repo Java | Chỉ cấp cho repo `githubactions_java`, quyền **Contents: Read and write** |
| `githubactions_java` | `NODE_REPO_TOKEN` | **Chỉ khi** repo Node là private | Chỉ cấp cho repo `githubactions`, quyền **Contents: Read-only** |

Nếu thiếu `JAVA_REPO_DISPATCH_TOKEN`, job `notify-java` bên repo Node chỉ in cảnh báo và bỏ qua, CI không bị fail.

> ⚠️ Không bao giờ nhúng token vào remote URL (`https://user:token@github.com/...`) hay commit token vào code. Token chỉ nên được lưu trong GitHub Secrets.
