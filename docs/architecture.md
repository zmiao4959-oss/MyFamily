# 家忆架构概览

当前完成第八阶段完整第一版闭环。

- Android：Kotlin、Jetpack Compose、Material 3、MVVM、Repository、Hilt、Room、DataStore、WorkManager、CameraX、Media3、Android Keystore。
- 本地服务：FastAPI、SQLAlchemy 2、Alembic、PostgreSQL 17、pgvector。
- 后台任务：Android WorkManager 负责媒体上传和指数退避；受限局域网也会实际尝试访问用户本地服务。服务端独立 worker 使用同一 PostgreSQL，不引入消息中间件。
- 部署：Docker Compose 同时启动 `api`、`worker`、`postgres`。

## 视觉与无障碍

Android 使用固定的暖色 Light/Dark Material 3 色板，避免系统动态色破坏家庭档案视觉和错误对比度。正文、按钮和标签采用更大的基础字号，同时保留系统字体缩放。原创 Image 2 素材统一转换为本地 WebP，Compose 只负责中文文字、交互和无障碍描述；App 运行时不依赖任何图片生成服务。空状态与错误状态使用共享组件，关键错误提供文字“重试”操作。

## 安全边界

管理员配对令牌只用于首次配对。服务端签发随机访问令牌，只保存 HMAC-SHA256 哈希；Android 端使用 Android Keystore 的 AES-GCM 密钥加密保存访问令牌。管理员令牌轮换后只保存 HMAC 哈希，新明文只向当前已认证设备显示一次。App 锁通过 AndroidX BiometricPrompt 调用系统指纹、面容或设备凭据，不采集生物特征。Debug 构建允许可信局域网 HTTP，Release 构建默认禁止明文流量。

## 备份与恢复

服务端把业务表和媒体文件写入 AES-256 加密 ZIP 容器（扩展名 `.fmbackup`），保存数据库内容哈希、格式版本和数量清单。访问令牌、管理员令牌、API Key、上传临时文件和后台任务不进入备份。恢复先完成密码、版本、表集合、路径和内容哈希校验，并自动创建恢复前安全备份；随后在单个数据库事务内替换业务表，再写回已校验媒体文件。

## 媒体数据流

用户主动选择、CameraX 拍摄或 MediaRecorder 录制的原始文件先进入 App 私有目录，同时写入 Room 上传队列。WorkManager 先同步对应记录，再以 1 MB 分块上传。服务器校验声明大小、文件签名、MIME 和 SHA-256，使用 UUID 文件名保存原件；图片用 Pillow 修正 EXIF 方向并生成缩略图，视频用 FFmpeg/FFprobe 生成封面和元数据。下载和缩略图接口均经过配对令牌认证。

## Android 主要界面

单 Activity Compose 架构提供首页、家族、记录、时间线和我的五个主入口。人物详情与记录详情直接订阅 Room 数据；家族树使用可缩放、可拖动的 Compose 图层，布局算法与 UI 分离以便单元测试。记录编辑草稿写入 Room `record_drafts`，进程退出后仍可恢复。正式界面和媒体管理共享 Repository，不建立重复数据源。

## AI 数据流

Android 只向本地服务提交记录或人物 ID。FastAPI 创建 `processing_jobs`，独立 worker 从 PostgreSQL 获取最小必要资料并调用可替换的 `AIProvider`。DeepSeek Provider 使用 OpenAI 兼容的 `/chat/completions`；结构化任务启用 JSON 模式，并由 Pydantic 严格校验。第一次无效时只进行一次结构修复，仍无效则进入受限重试。

模型输出保存到 `ai_artifacts`，包含 Provider、模型、Prompt 版本、输入哈希和独立 JSON。默认均为 AI 建议，不覆盖原文。用户确认后，记录整理结果只写入独立 `ai_summary`、标签和 `claims`；问答来源必须来自本轮只读工具检索。worker 最多自动尝试三次，失败后可由用户手动重试。

## 搜索与 Embedding 数据流

关键词搜索始终可用，不依赖模型。启用多模态搜索后，记录、人物或图片变化会按内容哈希创建 `embedding_generate` 数据库任务；独立 worker 使用可替换的 `VolcanoEmbeddingProvider` 调用火山方舟 `/api/v3/embeddings/multimodal`，将文字与图片映射到同一向量空间。图片优先使用服务端缩略图，Key 只存在于本地服务环境变量中。

向量保存在 PostgreSQL `embeddings.vector` 的 pgvector 列。搜索先根据家庭、软删除状态、人物、标签、时间和媒体类型得到允许访问的对象集合，再计算向量相似度。内容哈希未变化的 ready/pending 索引不会重复排队；变化后旧向量清空并重新生成。

## 阶段边界

第一版仍不实现运行时视觉内容生成、人物识别、OCR 或图片事实提取；图片向量只用于相似性检索。备份密码不保存，忘记密码后无法解密，恢复只面向同一备份格式版本。
