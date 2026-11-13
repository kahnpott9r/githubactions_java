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

Chạy cả 2 bằng Docker:

```bash
docker compose up --build
```

## Test

| Loại | Lệnh | Cần app Node? |
|---|---|---|
| Unit test (`*Test.java`) | `./mvnw verify` | Không, Node API được giả lập bằng `MockRestServiceServer` |
| Integration test (`*IT.java`) | `NODE_API_BASE_URL=http://localhost:3000 ./mvnw -Pintegration verify` | **Có**, chạy với app Node thật |

Integration test đóng vai trò **contract test**: nếu repo Node thay đổi format JSON hoặc hành vi API, test bên Java sẽ fail.

## Pipeline CI ([.github/workflows/ci.yml](.github/workflows/ci.yml))

```
unit-test ──▶ integration-test ──▶ build (jar + docker image)
                  │
                  └─ checkout repo githubactions → npm start → chờ /api/health → ./mvnw -Pintegration verify
```

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
