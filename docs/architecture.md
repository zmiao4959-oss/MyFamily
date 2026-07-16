# 家忆架构概览

当前为第一阶段项目骨架。

- Android：Kotlin、Jetpack Compose、Material 3、MVVM、Repository、Hilt、DataStore、Android Keystore。
- 本地服务：FastAPI、SQLAlchemy 2、Alembic、PostgreSQL 17、pgvector。
- 后台任务：独立 worker 进程，使用同一 PostgreSQL；后续阶段通过数据库任务表取代额外消息中间件。
- 部署：Docker Compose 同时启动 `api`、`worker`、`postgres`。

## 安全边界

管理员配对令牌只用于首次配对。服务端签发随机访问令牌，只保存 HMAC-SHA256 哈希；Android 端使用 Android Keystore 的 AES-GCM 密钥加密保存访问令牌。Debug 构建允许可信局域网 HTTP，Release 构建默认禁止明文流量。

## 阶段边界

人物、关系、记录、Room、同步和演示数据属于第二阶段，本阶段没有用静态假接口冒充这些能力。
