# 家忆架构概览

当前完成第三阶段媒体闭环。

- Android：Kotlin、Jetpack Compose、Material 3、MVVM、Repository、Hilt、Room、DataStore、WorkManager、CameraX、Media3、Android Keystore。
- 本地服务：FastAPI、SQLAlchemy 2、Alembic、PostgreSQL 17、pgvector。
- 后台任务：Android WorkManager 负责有网络约束的媒体上传和指数退避；服务端独立 worker 使用同一 PostgreSQL，不引入消息中间件。
- 部署：Docker Compose 同时启动 `api`、`worker`、`postgres`。

## 安全边界

管理员配对令牌只用于首次配对。服务端签发随机访问令牌，只保存 HMAC-SHA256 哈希；Android 端使用 Android Keystore 的 AES-GCM 密钥加密保存访问令牌。Debug 构建允许可信局域网 HTTP，Release 构建默认禁止明文流量。

## 媒体数据流

用户主动选择、CameraX 拍摄或 MediaRecorder 录制的原始文件先进入 App 私有目录，同时写入 Room 上传队列。WorkManager 先同步对应记录，再以 1 MB 分块上传。服务器校验声明大小、文件签名、MIME 和 SHA-256，使用 UUID 文件名保存原件；图片用 Pillow 修正 EXIF 方向并生成缩略图，视频用 FFmpeg/FFprobe 生成封面和元数据。下载和缩略图接口均经过配对令牌认证。

## 阶段边界

第三阶段不提前实现第四阶段的首页、正式记录详情、家族树和时间线；目前的媒体页面用于验证真实录制、选择、上传和播放链路。
