# 家忆架构概览

当前完成第四阶段主要界面闭环。

- Android：Kotlin、Jetpack Compose、Material 3、MVVM、Repository、Hilt、Room、DataStore、WorkManager、CameraX、Media3、Android Keystore。
- 本地服务：FastAPI、SQLAlchemy 2、Alembic、PostgreSQL 17、pgvector。
- 后台任务：Android WorkManager 负责媒体上传和指数退避；受限局域网也会实际尝试访问用户本地服务。服务端独立 worker 使用同一 PostgreSQL，不引入消息中间件。
- 部署：Docker Compose 同时启动 `api`、`worker`、`postgres`。

## 安全边界

管理员配对令牌只用于首次配对。服务端签发随机访问令牌，只保存 HMAC-SHA256 哈希；Android 端使用 Android Keystore 的 AES-GCM 密钥加密保存访问令牌。Debug 构建允许可信局域网 HTTP，Release 构建默认禁止明文流量。

## 媒体数据流

用户主动选择、CameraX 拍摄或 MediaRecorder 录制的原始文件先进入 App 私有目录，同时写入 Room 上传队列。WorkManager 先同步对应记录，再以 1 MB 分块上传。服务器校验声明大小、文件签名、MIME 和 SHA-256，使用 UUID 文件名保存原件；图片用 Pillow 修正 EXIF 方向并生成缩略图，视频用 FFmpeg/FFprobe 生成封面和元数据。下载和缩略图接口均经过配对令牌认证。

## Android 主要界面

单 Activity Compose 架构提供首页、家族、记录、时间线和我的五个主入口。人物详情与记录详情直接订阅 Room 数据；家族树使用可缩放、可拖动的 Compose 图层，布局算法与 UI 分离以便单元测试。记录编辑草稿写入 Room `record_drafts`，进程退出后仍可恢复。正式界面和媒体管理共享 Repository，不建立重复数据源。

## 阶段边界

第四阶段不调用模型服务。AI 状态在首页明确显示为未启用，第五阶段再实现结构化整理、人物小传、采访问题、问答和用户确认流程。
