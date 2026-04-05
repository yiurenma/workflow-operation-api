# Deploy on Render（配合 GitHub Actions）

## 仓库里已包含的内容

| 文件 | 作用 |
|------|------|
| `.github/workflows/ci.yml` | 在 `push` / `pull_request`（`main` 或 `master`）上执行 `mvn verify -Djacoco.skip=true` |
| `.github/workflows/render-deploy.yml` | 在 **CI 成功** 且事件为 **push** 到 `main`/`master` 后，若配置了 `RENDER_DEPLOY_HOOK_URL`，则 `POST` 触发 Render 部署 |
| `Dockerfile` | Java 21 + Maven 多阶段构建；Render 对 Spring Boot 推荐使用 **Docker** 运行时 |
| `render.yaml` | 可选 Blueprint；若与已有服务同名，可能与 Dashboard 中的服务同步 |
| `application.yml` 中 `server.port` | 使用 `${PORT:8080}`，兼容 Render 注入的 `PORT` |

## 两种与 Render 的配合方式

### A. 仅用 Render 自带的「推送即部署」

你已连接 GitHub 时，Render 会在每次 push 后自动构建部署。此时：

- **仍建议保留 `ci.yml`**，在 GitHub 上看到构建是否通过。
- 在 Render 控制台可开启 **Wait for CI**（若你的套餐支持）：要求 GitHub 上名为 **CI** 的检查通过后再部署。
- **不要**配置 `RENDER_DEPLOY_HOOK_URL`，或删除 / 禁用 `render-deploy.yml`，避免与自动部署重复触发。

### B. CI 通过后再部署（Deploy Hook）

1. Render → Web Service → **Manual Deploy** → **Deploy Hook** → 复制 URL。  
2. GitHub → **Settings → Secrets and variables → Actions** → 新建 `RENDER_DEPLOY_HOOK_URL`。  
3. 在 Render 中 **关闭**「每次 push 自动部署」，只保留通过 Hook 部署（按你平台选项调整）。

`render-deploy.yml` 会在 **CI workflow 成功结束** 后触发 Hook；若未设置 Secret，会跳过并打印说明。

## 数据库与环境变量

应用在默认 `application.yml` 里使用 PostgreSQL（及 P6Spy 等）。在 Render 上请用 **环境变量** 覆盖敏感配置（推荐不要把生产密码提交进仓库），例如：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

若使用标准 PostgreSQL 驱动而非 P6Spy，可能还需要设置 `SPRING_DATASOURCE_DRIVER_CLASS_NAME` 等；请与当前 `application.yml` 中的 JDBC 配置对齐。

## 首次在 Render 上选 Docker

若当前服务仍是「Native」构建且没有 Java 运行时：

1. 将服务改为 **Docker** 部署，根目录 `Dockerfile` 使用本仓库提供的文件。  
2. 或将本仓库的 `render.yaml` 与 Dashboard 中的服务名对齐，用 Blueprint 管理。

## 本地验证镜像

```bash
docker build -t workflow-operation-api .
docker run --rm -p 8080:8080 -e PORT=8080 workflow-operation-api
```

（数据库仍需通过环境变量指向可访问的 PostgreSQL。）
