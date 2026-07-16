# 家忆

“家忆”是面向个人和家庭的本地优先电子族谱与人生档案 App。项目目前完成第八阶段：完整闭环包含本地优先资料、媒体、AI 助手、多模态搜索、AES-256 加密备份、App 锁，并完成原创视觉素材、深色模式、适老化、空状态和错误状态打磨。

> AI 与多模态搜索默认关闭；没有任何模型 Key 时，基础功能、加密备份、App 锁和关键词搜索仍可完整使用。

## 目录

- `app/`：Android Studio App 模块（Kotlin + Jetpack Compose）。
- `local-server/`：FastAPI API、数据库迁移、worker 和后端测试。
- `docs/`：架构等项目文档。
- `docker-compose.yml`：本地一键部署。
- `.env.example`：不含真实密钥的配置模板。
- `scripts/`：启动、初始化、健康检查、备份、恢复和清除演示资料脚本。

## 视觉预览

![欢迎页原创插画](app/src/main/res/drawable-nodpi/welcome_family_album.webp)

![空人物状态](app/src/main/res/drawable-nodpi/empty_people.webp)

全部 10 项素材均由开发阶段的 OpenAI Image 2 原创生成，App 运行时不会调用 OpenAI。完整提示词、尺寸和后期处理记录见 `docs/image-prompts.md`。

## 本地服务启动

1. 安装 Docker Desktop（或 Docker Engine + Compose）。
2. 复制 `.env.example` 为 `.env`。
3. 至少修改 `APP_SECRET` 和 `POSTGRES_PASSWORD`，建议在 `.env` 中设置固定的 `PAIRING_TOKEN`。
4. 在项目根目录运行 `docker compose up -d --build`。
5. 运行 `docker compose logs api`，查看 `Administrator pairing token`。
6. 健康检查地址：`http://电脑局域网IP:8080/health`；OpenAPI 文档：`http://电脑局域网IP:8080/docs`。

Windows 也可直接运行 `powershell -ExecutionPolicy Bypass -File scripts/start-local-server.ps1`；数据库初始化和健康检查分别使用 `scripts/initialize-database.ps1`、`scripts/health-check.ps1`。

如果未设置 `PAIRING_TOKEN`，服务每次启动会生成一个新的管理员配对令牌；已配对手机的访问令牌仍保存在数据库中。

在 App 的“我的 → 安全、备份与授权设备”生成新配对令牌后，旧管理员配对令牌立即失效；新令牌只显示一次，已配对设备不受影响。

## Android 构建与连接

1. 使用 Android Studio 打开项目根目录。
2. 使用项目自带的 Gradle Wrapper，同步并运行 `app`。
3. 模拟器默认地址为 `http://10.0.2.2:8080`；真机请填写电脑的局域网地址，例如 `http://192.168.1.100:8080`。
4. 输入 API 日志中的配对令牌，点击“测试连接并保存”。

Debug 构建允许局域网 HTTP。Release 构建默认不允许明文网络流量；后续发布前应在反向代理上配置 HTTPS。

命令行测试与构建：`gradlew.bat testDebugUnitTest assembleDebug`。

Debug APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 数据目录与维护脚本

Docker Volume 中的数据库、原始媒体、缩略图和备份彼此独立。媒体位于 `/data/media`，服务端备份位于 `/data/backups`；不要直接修改 Volume 内文件。

- 创建并下载加密备份：`powershell -ExecutionPolicy Bypass -File scripts/create-backup.ps1`
- 恢复已有服务端备份：`powershell -ExecutionPolicy Bypass -File scripts/restore-backup.ps1 -BackupId 备份ID`
- 清除虚构演示资料：`powershell -ExecutionPolicy Bypass -File scripts/clear-demo-data.ps1`

脚本会提示输入配对令牌和备份密码。也可以先设置 `FAMILY_MEMORY_PAIRING_TOKEN` 环境变量；不要把真实令牌写入脚本或提交到 Git。恢复前会显示日期和资料数量，必须输入 `RESTORE`，服务端还会自动创建恢复前安全备份。

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

- 模型 API Key 不进入 APK，只能配置在电脑或 NAS 的本地服务环境变量中。
- 配对访问令牌在 Android Keystore 保护下保存，服务端只保存令牌哈希。
- `.env` 已被 Git 忽略，不要提交真实密钥。
- 本项目不接入广告、第三方行为分析或 Google Play Services 关键能力。
- 若要关闭全部第三方 AI 调用，将 `.env` 中 `FEATURE_AI=false`、`FEATURE_MULTIMODAL_SEARCH=false`，然后重启服务；人物、记录、媒体、关键词搜索和备份仍可使用。

## 常见错误

- 模拟器访问电脑必须使用 `http://10.0.2.2:8080`，不能使用 `localhost`。
- 真机连接受限时，检查手机和电脑是否在同一 Wi-Fi、防火墙是否只对家庭网络开放 TCP 8080。
- “等待网络上传”时可点击立即尝试；Android 显示网络受限不代表本地服务一定不可访问。
- `401` 表示访问令牌已撤销或配对令牌不正确；在连接设置中重新配对。
- `409` 通常表示 AI/语义功能尚未启用；关键词搜索与基础资料不受影响。
- `503` 表示数据库或模型 Provider 暂时不可用；先检查 `docker compose ps` 和服务日志。
- 备份密码无法找回。错误密码只会拒绝恢复，不会修改现有资料。

## DeepSeek AI 配置

AI 默认关闭。需要使用时，在项目根目录 `.env` 中设置：

```dotenv
FEATURE_AI=true
DEEPSEEK_API_KEY=你的服务端Key
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_FAST_MODEL=deepseek-v4-flash
DEEPSEEK_MAIN_MODEL=deepseek-v4-pro
```

然后运行 `docker compose up -d --build`。不要把真实 Key 写进 `.env.example`、Android 工程、截图或日志。App 的“AI 助手”会显示服务是否启用及配置完成；资料发送前会提示第三方模型处理范围。

## 火山引擎多模态搜索配置

关键词搜索不需要模型 Key。需要自然语言搜索文字和图片时，在项目根目录 `.env` 中设置：

```dotenv
FEATURE_MULTIMODAL_SEARCH=true
VOLCANO_ARK_API_KEY=你的服务端Key
VOLCANO_ARK_BASE_URL=https://ark.cn-beijing.volces.com
VOLCANO_EMBEDDING_MODEL=doubao-embedding-vision-251215
VOLCANO_EMBEDDING_ENDPOINT_ID=你的Endpoint-ID
```

Endpoint ID 优先于模型名；如果账号支持直接使用模型 ID，可以将 Endpoint ID 留空。重新运行 `docker compose up -d --build`，然后在 App 首页进入“搜索家族资料”，点击“重新生成搜索索引”。文字、人物简介和用户主动保存的图片会在电脑端排队生成向量；图片优先发送缩略图。关闭 `FEATURE_MULTIMODAL_SEARCH` 后，worker 不再调用火山引擎，已有基础资料与关键词搜索不受影响。

## 第一阶段验收点

- Android App 可打开本地服务连接页。
- `/health` 能验证 API 与数据库状态。
- 正确管理员配对令牌可换取访问令牌，错误令牌返回 401。
- 访问令牌可访问受保护接口，并可撤销。
- Docker Compose 定义 `api`、`worker`、`postgres` 三个服务。
- Android 单元测试、后端测试和 Debug 构建通过。

## 第二阶段验收点

- 配对成功后点击“进入第二阶段资料测试”。
- 关闭网络后新增人物或文字记录，页面“待同步”数量应增加，重新打开 App 后数据仍在。
- 恢复网络并点击“立即同步”，待同步数量应归零，服务端能查到新增资料。
- 点击“载入演示家族”，应显示 9 位虚构人物、12 条关系、5 条文字记录和 3 个标签；重复点击不会重复创建。
- 点击“仅清空手机本地测试数据”不会删除服务器资料，再次同步可以恢复。

## 第三阶段验收点

1. 在第二阶段页面点击“进入第三阶段媒体测试”。
2. 点击“选择照片”或“选择视频”，只会读取用户主动选择的文件；文件先复制到 App 私有目录。
3. 点击“使用 CameraX 拍照”，授权相机后拍摄并保存原图。
4. 点击“开始录音”，验证暂停、继续、停止、试听、删除未保存录音和保存为记录。
5. 系统语音识别可用时可进行短口述识别；不可用时仍能录音，并可手填转写文字。
6. 断网导入媒体后应显示“等待网络上传”；联网后 WorkManager 自动分块上传，失败时可手动重试。
7. 上传过程显示进度，完成后显示“已上传”；图片和视频可本地预览或播放。

服务端默认限制单文件 500 MB，可通过 `MAX_UPLOAD_SIZE_MB` 调整。服务端校验文件签名、MIME、大小、分块偏移和 SHA-256，使用 UUID 磁盘文件名并检测重复内容。原始文件和缩略图分目录保存，所有下载接口都要求 Bearer Token。

## 第四阶段验收点

1. App 默认进入正式首页，底部包含“首页、家族、记录、时间线、我的”五个入口。
2. 首页显示资料数量、连接/待同步状态、最近人物、最近记录和快速记录入口。
3. 家族页可以搜索、添加和编辑人物；人物详情显示关系与相关记录，并可新增人物关系。
4. 家族树可拖动、双指缩放、点击人物进入详情，并可“回到本人”；关系不完整时仍显示所有人物。
5. 记录页可填写标题、原始文字、年份、地点、人物和标签；未完成内容自动保存，重启 App 后可继续。
6. 记录详情显示原始内容、时间地点、人物、标签、附件及上传状态。
7. 时间线按年份分组，支持正文搜索、人物、类型、标签和时间未知筛选。
8. “我的”提供连接设置、同步、演示家族、媒体管理和带确认提示的本地清理入口。

## 第五阶段验收点

1. 未配置 Key 或 `FEATURE_AI=false` 时，App 明确显示 AI 未启用，人物、记录、媒体、时间线和同步仍正常。
2. 记录详情可进入 AI 助手，生成建议标题、摘要、标签、人物/地点候选和待确认问题。
3. 人物详情可生成人物小传草稿和逐题采访问题；不确定内容与来源记录单独展示。
4. 资料问答先调用后端只读工具检索，答案显示来源记录 ID；找不到资料时必须明确回答不知道。
5. AI 结果以独立 Artifact 保存，默认状态为“尚未确认”；只有点击“由我确认”才应用摘要、标签和确认过的 claim。
6. 原始文字和媒体永远不被 AI 覆盖。失败任务最多自动尝试三次，并提供手动重试。
7. 模型、Provider、Prompt 版本、输入哈希和输出 JSON 均可追踪；API Key 不进入 APK 或数据库任务内容。

## 第六阶段验收点

1. 未配置火山引擎 Key 或关闭功能时，关键词搜索仍可查找人物姓名、记录标题、正文、AI 摘要、地点和标签；语义功能显示明确的未启用提示。
2. 配置 `FEATURE_MULTIMODAL_SEARCH=true`、Key 和模型/Endpoint 后，重建索引会为记录文字、人物资料和图片创建数据库后台任务。
3. worker 成功后，搜索状态显示“已完成”数量；失败任务最多自动尝试三次，重建索引不会重复处理内容哈希未变化的对象。
4. “语义”模式可以用自然语言查找相关人物和记录；“找图片”可以用文字描述查找相关图片，并可跳转到所属记录。
5. 搜索支持人物范围以及关键词模式下的图片、录音、视频筛选；搜索前先同步手机待处理资料。
6. 记录、人物、AI 摘要或图片变化后，旧向量会失效并进入重新生成队列；原始文字和原始媒体不会被覆盖。
7. 语义检索先按当前家庭、未删除状态和筛选条件确定可见对象，再计算相似度；未认证请求不能访问搜索接口。

## 第七阶段验收点

1. “我的 → 安全、备份与授权设备”可创建 AES-256 加密备份；密码至少 10 位且不会保存，错误密码不能检查或恢复。
2. 备份包含人物、关系、记录、标签、媒体元数据和媒体文件，不包含访问令牌、管理员配对令牌、模型 Key、临时上传或任务队列。
3. 备份可通过系统文件选择器导出；文件内容、数据库清单和版本在恢复前均会校验。
4. 恢复必须再次输入备份密码并明确确认；服务器先自动创建当前资料的“恢复前安全备份”，再替换业务资料。
5. App 锁启用和关闭都要求系统身份验证；离开 App 后再次打开，需使用指纹、面容或设备锁屏密码。
6. 已授权设备列表能区分当前设备，可撤销其他设备；被撤销设备不能继续访问服务。
7. 重新生成管理员配对令牌后旧令牌立即失效，新令牌只显示一次；日志会遮盖 Bearer Token、API Key 和密码。

## 第八阶段验收点

1. App 图标、欢迎页、人物/记录空状态、三种默认头像、家族树、录音和备份插画均为原创 WebP，提示词记录在 `docs/image-prompts.md`。
2. 系统切换深色模式后，背景、文字、卡片、错误提示和按钮保持清晰对比，视觉仍使用统一的米色、胡桃木棕、鼠尾草绿和陶土红。
3. 默认正文、标签和按钮字号增大，系统字体缩放仍生效；关键操作均保留文字且使用 Material 最小触控区域。
4. 空人物、空记录、空家族树、搜索无结果和空备份页面提供插画、解释及可执行下一步。
5. 连接、搜索、媒体与安全页面的成功/错误信息使用统一状态卡；可重试错误提供明确“重试”按钮。
6. PowerShell 运维脚本覆盖启动、数据库初始化、健康检查、创建备份、恢复备份和清除演示资料。
7. 后端完整测试、Android 单元测试、Lint、Debug APK、Android 测试代码编译全部通过；APK 中不包含模型 API Key。
