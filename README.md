# 家忆

“家忆”是面向个人和家庭的本地优先电子族谱与人生档案 App。项目目前完成第一阶段骨架：Android 连接设置、本地 FastAPI 服务、PostgreSQL/pgvector、配对认证、健康检查和 Docker Compose。

> 当前阶段尚未实现人物、关系、记录、媒体、AI 和备份功能；这些能力会严格按开发任务书分阶段完成。

## 目录

- `app/`：Android Studio App 模块（Kotlin + Jetpack Compose）。
- `local-server/`：FastAPI API、数据库迁移、worker 和后端测试。
- `docs/`：架构等项目文档。
- `docker-compose.yml`：本地一键部署。
- `.env.example`：不含真实密钥的配置模板。

## 本地服务启动

1. 安装 Docker Desktop（或 Docker Engine + Compose）。
2. 复制 `.env.example` 为 `.env`。
3. 至少修改 `APP_SECRET` 和 `POSTGRES_PASSWORD`，建议在 `.env` 中设置固定的 `PAIRING_TOKEN`。
4. 在项目根目录运行 `docker compose up -d --build`。
5. 运行 `docker compose logs api`，查看 `Administrator pairing token`。
6. 健康检查地址：`http://电脑局域网IP:8080/health`；OpenAPI 文档：`http://电脑局域网IP:8080/docs`。

如果未设置 `PAIRING_TOKEN`，服务每次启动会生成一个新的管理员配对令牌；已配对手机的访问令牌仍保存在数据库中。

## Android 构建与连接

1. 使用 Android Studio 打开项目根目录。
2. 使用项目自带的 Gradle Wrapper，同步并运行 `app`。
3. 模拟器默认地址为 `http://10.0.2.2:8080`；真机请填写电脑的局域网地址，例如 `http://192.168.1.100:8080`。
4. 输入 API 日志中的配对令牌，点击“测试连接并保存”。

Debug 构建允许局域网 HTTP。Release 构建默认不允许明文网络流量；后续发布前应在反向代理上配置 HTTPS。

命令行测试与构建：`gradlew.bat testDebugUnitTest assembleDebug`。

Debug APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 后端开发测试

在 `local-server` 下创建 Python 3.12 虚拟环境并安装 `requirements-dev.txt`，然后运行 `python -m pytest`。

测试使用临时 SQLite 数据库，不需要真实 API Key 或正在运行的 PostgreSQL；实际运行环境始终使用 PostgreSQL。

## 局域网排查

- Windows：运行 `ipconfig`，查找当前网卡的 IPv4 地址。
- macOS/Linux：运行 `ip addr` 或在系统网络设置中查看 IPv4 地址。
- 手机与电脑必须连接同一可信局域网。
- 防火墙需要允许 TCP 8080 入站，仅建议开放给家庭局域网。
- 访问 `/health` 失败时，先查看 `docker compose ps` 和 `docker compose logs api`。

## 隐私与密钥

- 模型 API Key 不进入 APK；第一阶段也不调用任何 AI 服务。
- 配对访问令牌在 Android Keystore 保护下保存，服务端只保存令牌哈希。
- `.env` 已被 Git 忽略，不要提交真实密钥。
- 本项目不接入广告、第三方行为分析或 Google Play Services 关键能力。

## 第一阶段验收点

- Android App 可打开本地服务连接页。
- `/health` 能验证 API 与数据库状态。
- 正确管理员配对令牌可换取访问令牌，错误令牌返回 401。
- 访问令牌可访问受保护接口，并可撤销。
- Docker Compose 定义 `api`、`worker`、`postgres` 三个服务。
- Android 单元测试、后端测试和 Debug 构建通过。
