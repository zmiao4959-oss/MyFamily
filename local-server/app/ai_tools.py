from __future__ import annotations

from typing import Any

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.models import DEFAULT_FAMILY_ID, MediaAsset, Person, Record, Relationship


def _tool(name: str, description: str, properties: dict, required: list[str] | None = None) -> dict:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": required or [],
                "additionalProperties": False,
            },
        },
    }


AI_TOOL_DEFINITIONS = [
    _tool("search_records", "按关键词检索用户有权查看的家族记录", {"query": {"type": "string"}, "limit": {"type": "integer"}}, ["query"]),
    _tool("search_people", "按姓名检索家族人物", {"query": {"type": "string"}, "limit": {"type": "integer"}}, ["query"]),
    _tool("get_person_profile", "读取指定人物档案", {"person_id": {"type": "string"}}, ["person_id"]),
    _tool("get_person_relationships", "读取指定人物关系", {"person_id": {"type": "string"}}, ["person_id"]),
    _tool("get_person_timeline", "读取指定人物的记录时间线", {"person_id": {"type": "string"}, "limit": {"type": "integer"}}, ["person_id"]),
    _tool("get_record_detail", "读取指定原始记录及来源信息", {"record_id": {"type": "string"}}, ["record_id"]),
    _tool("find_related_media", "读取指定记录的媒体元数据", {"record_id": {"type": "string"}}, ["record_id"]),
    _tool("suggest_record_links", "根据关键词建议相关记录，不修改数据", {"record_id": {"type": "string"}, "limit": {"type": "integer"}}, ["record_id"]),
    _tool("create_record_draft", "仅创建建议草稿内容，不写入正式档案", {"title": {"type": "string"}, "original_text": {"type": "string"}}, ["title", "original_text"]),
    _tool("suggest_relationship", "仅返回待用户确认的人物关系建议", {"person_a_id": {"type": "string"}, "person_b_id": {"type": "string"}, "relation_type": {"type": "string"}}, ["person_a_id", "person_b_id", "relation_type"]),
]


class ToolValidationError(ValueError):
    pass


class AIToolExecutor:
    def __init__(self, db: Session):
        self.db = db

    def execute(self, name: str, args: dict[str, Any]) -> dict | list:
        handlers = {
            "search_records": self.search_records,
            "search_people": self.search_people,
            "get_person_profile": self.get_person_profile,
            "get_person_relationships": self.get_person_relationships,
            "get_person_timeline": self.get_person_timeline,
            "get_record_detail": self.get_record_detail,
            "find_related_media": self.find_related_media,
            "suggest_record_links": self.suggest_record_links,
            "create_record_draft": self.create_record_draft,
            "suggest_relationship": self.suggest_relationship,
        }
        if name not in handlers:
            raise ToolValidationError("Tool is not allowed")
        return handlers[name](**args)

    def search_records(self, query: str, limit: int = 10) -> list[dict]:
        query = self._text(query, "query", 200)
        limit = self._limit(limit)
        items = self.db.scalars(
            select(Record).where(
                Record.family_id == DEFAULT_FAMILY_ID,
                Record.deleted_at.is_(None),
                or_(Record.title.ilike(f"%{query}%"), Record.original_text.ilike(f"%{query}%")),
            ).order_by(Record.updated_at.desc()).limit(limit)
        ).unique().all()
        return [self._record_summary(item) for item in items]

    def search_people(self, query: str, limit: int = 10) -> list[dict]:
        query = self._text(query, "query", 120)
        items = self.db.scalars(
            select(Person).where(Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None), Person.name.ilike(f"%{query}%"))
            .order_by(Person.name).limit(self._limit(limit))
        ).all()
        return [{"id": item.id, "name": item.name, "nickname": item.nickname, "birth_year": item.birth_year} for item in items]

    def get_person_profile(self, person_id: str) -> dict:
        item = self._person(person_id)
        return {
            "id": item.id, "name": item.name, "nickname": item.nickname, "birth_year": item.birth_year,
            "death_year": item.death_year, "birth_place": item.birth_place, "ancestral_home": item.ancestral_home,
            "biography": item.biography, "verification_status": item.verification_status,
        }

    def get_person_relationships(self, person_id: str) -> list[dict]:
        self._person(person_id)
        items = self.db.scalars(
            select(Relationship).where(
                Relationship.family_id == DEFAULT_FAMILY_ID, Relationship.deleted_at.is_(None),
                or_(Relationship.person_a_id == person_id, Relationship.person_b_id == person_id),
            )
        ).all()
        return [{"id": item.id, "person_a_id": item.person_a_id, "person_b_id": item.person_b_id, "relation_type": item.relation_type, "verification_status": item.verification_status} for item in items]

    def get_person_timeline(self, person_id: str, limit: int = 20) -> list[dict]:
        self._person(person_id)
        items = self.db.scalars(
            select(Record).where(Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None)).order_by(Record.occurred_at_start.desc().nullslast())
        ).unique().all()
        return [self._record_summary(item) for item in items if person_id in item.person_ids][: self._limit(limit, 50)]

    def get_record_detail(self, record_id: str) -> dict:
        item = self._record(record_id)
        return {
            **self._record_summary(item), "original_text": item.original_text, "edited_text": item.edited_text,
            "location_text": item.location_text, "person_ids": item.person_ids, "source_type": item.source_type,
            "verification_status": item.verification_status,
        }

    def find_related_media(self, record_id: str) -> list[dict]:
        self._record(record_id)
        items = self.db.scalars(
            select(MediaAsset).where(MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.record_id == record_id, MediaAsset.deleted_at.is_(None))
        ).all()
        return [{"id": item.id, "media_type": item.media_type, "mime_type": item.mime_type, "duration_ms": item.duration_ms, "width": item.width, "height": item.height} for item in items]

    def suggest_record_links(self, record_id: str, limit: int = 5) -> list[dict]:
        source = self._record(record_id)
        words = {word for word in source.title.replace("，", " ").split() if len(word) >= 2}
        items = self.db.scalars(select(Record).where(Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None), Record.id != source.id)).unique().all()
        ranked = sorted(items, key=lambda item: sum(word in f"{item.title} {item.original_text}" for word in words), reverse=True)
        return [self._record_summary(item) for item in ranked[: self._limit(limit)] if words]

    def create_record_draft(self, title: str, original_text: str) -> dict:
        return {"status": "draft_only", "title": self._text(title, "title", 240), "original_text": self._text(original_text, "original_text", 8000), "requires_user_confirmation": True}

    def suggest_relationship(self, person_a_id: str, person_b_id: str, relation_type: str) -> dict:
        self._person(person_a_id)
        self._person(person_b_id)
        if person_a_id == person_b_id:
            raise ToolValidationError("A relationship requires two people")
        return {"status": "suggestion_only", "person_a_id": person_a_id, "person_b_id": person_b_id, "relation_type": self._text(relation_type, "relation_type", 50), "requires_user_confirmation": True}

    def _person(self, person_id: str) -> Person:
        item = self.db.scalar(select(Person).where(Person.id == person_id, Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None)))
        if item is None:
            raise ToolValidationError("Person not found")
        return item

    def _record(self, record_id: str) -> Record:
        item = self.db.scalar(select(Record).where(Record.id == record_id, Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None)))
        if item is None:
            raise ToolValidationError("Record not found")
        return item

    @staticmethod
    def _record_summary(item: Record) -> dict:
        return {"id": item.id, "title": item.title, "excerpt": item.original_text[:500], "occurred_at": item.occurred_at_start.isoformat() if item.occurred_at_start else None}

    @staticmethod
    def _text(value: Any, field: str, maximum: int) -> str:
        if not isinstance(value, str) or not value.strip() or len(value) > maximum:
            raise ToolValidationError(f"Invalid {field}")
        return value.strip()

    @staticmethod
    def _limit(value: Any, maximum: int = 20) -> int:
        if not isinstance(value, int):
            raise ToolValidationError("Invalid limit")
        return max(1, min(value, maximum))
