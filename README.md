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

### Chạy toàn bộ bằng Docker Compose

```bash
docker compose up --build -d
```

| Service | URL trên host | Ghi chú |
|---|---|---|
| `node-app` | http://localhost:3000 | Build từ `../githubactions` |
| `java-app` | http://localhost:8080/api/report | |
| `ministack` | http://localhost:4567 | AWS emulator (S3, SQS, DynamoDB, ...). Health: `/_ministack/health` |
| `stackport` | **http://localhost:8090** | **UI web** để xem tài nguyên AWS trên MiniStack |
| `aws-init` | – | Chạy một lần rồi thoát, tạo tài nguyên trên MiniStack |

`aws-init` chạy [infra/aws/init.sh](infra/aws/init.sh) để tạo các tài nguyên sau. Script idempotent, chạy lại nhiều lần không lỗi:

| Loại | Tên |
|---|---|
| S3 bucket | `todo-reports` |
| SQS queue | `todo-events` |
| DynamoDB table | `todo-report-history` (khóa `id`) |

Có 2 cách xem tài nguyên:
- **Bằng UI:** mở http://localhost:8090 (StackPort).
- **Bằng CLI** (credentials giả là `test` / `test`, region `us-east-1`):
  ```bash
  export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
  aws --endpoint-url http://localhost:4567 s3 ls
  ```

Chỉ chạy phần AWS: `docker compose up -d ministack aws-init stackport`

**Cấu hình:** copy `.env.example` thành `.env` để đổi port hoặc image. `../docker-compose.yml` ở thư mục cha dùng port 4566 (ministack) và 8089 (stackport), nên stack này mặc định dùng **4567 / 8090** để 2 stack chạy song song được.

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
