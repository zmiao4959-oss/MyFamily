# 数据库结构概览

所有业务主键使用 UUID 字符串。`owner_id` 与 `family_id` 在个人版仍然保留，为未来多用户迁移预留边界。

核心资料表包括 `persons`、`relationships`、`records`、`tags` 和 `record_tags`；媒体表包括 `media_assets` 与 `upload_sessions`；配对和同步使用 `pairing_tokens` 与 `sync_changes`。

第五阶段新增：

- `processing_jobs`：数据库任务队列，保存任务类型、对象 ID、Provider、模型、状态、尝试次数和脱敏错误；不保存 API Key。
- `ai_artifacts`：每次重要 AI 输出，保存对象 ID、类型、Provider、模型、Prompt 版本、输入哈希、输出 JSON 和用户确认状态。
- `claims`：可验证信息，保存主体、谓词、对象、来源记录、信息类型、确认状态、置信度和是否由 AI 创建。

AI Artifact 默认是 `suggestion`。用户确认记录整理结果后，摘要写入 `records.ai_summary`，原始正文继续保存在 `records.original_text`；两者不会互相覆盖。

第六阶段新增 `embeddings`：保存对象类型、对象 ID、模态、模型、Endpoint ID、内容哈希、来源更新时间、pgvector 向量、状态和脱敏错误。唯一约束防止同一内容来源和模型产生重复活动索引。内容变化后向量置为待生成，由 `processing_jobs` 的 `embedding_generate` 任务重新计算。

PostgreSQL 迁移会执行 `CREATE EXTENSION IF NOT EXISTS vector`；向量列使用 pgvector，第一版个人数据量较小，不创建 HNSW 索引。Alembic 当前版本为 `0005`。
