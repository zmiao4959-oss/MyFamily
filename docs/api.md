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
