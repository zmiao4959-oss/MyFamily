# API 概览

所有 `/ai/*` 接口都要求已配对设备的 Bearer Token。

- `GET /ai/status`：AI 开关、配置状态及模型名称。
- `POST /ai/organize-record`：创建记录整理任务。
- `POST /ai/generate-biography`：创建人物小传任务。
- `POST /ai/generate-interview-questions`：创建采访问题任务。
- `POST /ai/ask`：创建带检索工具的资料问答任务。
- `GET /ai/jobs/{id}`：查询任务状态。
- `POST /ai/jobs/{id}/retry`：重试失败任务。
- `GET /ai/artifacts`：按记录或人物读取 AI Artifact。
- `GET /ai/artifacts/{id}`：读取单个结果。
- `POST /ai/artifacts/{id}/confirm`：由用户确认或忽略建议。

任务状态为 `pending`、`running`、`completed` 或 `failed`。AI 关闭返回 409，未配置 Key 返回 503。OpenAPI 的完整请求和响应结构可在本地服务 `/docs` 查看。

## 搜索接口

所有 `/search/*` 接口都要求已配对设备的 Bearer Token。

- `GET /search/status`：功能开关、配置状态、模型/Endpoint 和 ready/pending/failed 索引数量。
- `POST /search/text`：不依赖模型的关键词搜索，支持人物、标签、媒体类型和年份筛选。
- `POST /search/semantic`：对文字、人物资料和图片进行自然语言语义检索。
- `POST /search/multimodal`：用文字描述检索图片向量。
- `POST /search/reindex`：按内容哈希补建或重新生成记录、人物和图片索引。
- `POST /search/jobs/{id}/retry`：重新排队一个失败的 Embedding 任务。

语义和多模态搜索关闭时返回 409；缺少火山引擎 Key/模型配置或 Provider 暂时不可用时返回 503。搜索结果只包含当前家庭中未软删除且通过请求筛选条件的对象。
