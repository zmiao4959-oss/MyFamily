from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core import create_entity, delete_entity
from app.database import get_db
from app.models import Person, Record, Relationship, Tag
from app.security import require_access_token

router = APIRouter(prefix="/demo", dependencies=[Depends(require_access_token)])
DEMO_MARKER = "demo_seed_v1"


@router.post("/load")
def load_demo(db: Session = Depends(get_db)):
    existing = db.scalar(select(Person).where(Person.source_text == DEMO_MARKER, Person.deleted_at.is_(None)))
    if existing:
        return counts(db, already_loaded=True)

    specs = [
        ("周文山", 1938, "male", False),
        ("林桂芳", 1941, "female", False),
        ("周建国", 1964, "male", False),
        ("陈秀兰", 1967, "female", False),
        ("周建华", 1969, "female", False),
        ("周海峰", 1990, "male", True),
        ("王晓梅", 1992, "female", False),
        ("周雨桐", 2016, "female", False),
        ("周星宇", 2019, "male", False),
    ]
    people = {}
    for name, birth_year, gender, is_self in specs:
        people[name] = create_entity(
            db,
            Person,
            {
                "name": name,
                "surname": name[0],
                "given_name": name[1:],
                "birth_year": birth_year,
                "birth_date_precision": "year",
                "gender": gender,
                "is_self": is_self,
                "source_text": DEMO_MARKER,
                "verification_status": "confirmed_fact",
            },
            "person",
        )

    links = [
        ("周文山", "周建国", "father"), ("林桂芳", "周建国", "mother"),
        ("周文山", "周建华", "father"), ("林桂芳", "周建华", "mother"),
        ("周建国", "陈秀兰", "spouse"), ("周建国", "周海峰", "father"),
        ("陈秀兰", "周海峰", "mother"), ("周海峰", "王晓梅", "spouse"),
        ("周海峰", "周雨桐", "father"), ("王晓梅", "周雨桐", "mother"),
        ("周海峰", "周星宇", "father"), ("王晓梅", "周星宇", "mother"),
    ]
    for left, right, kind in links:
        create_entity(
            db,
            Relationship,
            {
                "person_a_id": people[left].id,
                "person_b_id": people[right].id,
                "relation_type": kind,
                "source_text": DEMO_MARKER,
                "verification_status": "confirmed_fact",
            },
            "relationship",
        )

    tags = {}
    for name, color in [("春节", "#9A4B3D"), ("成长", "#5B7052"), ("家庭聚会", "#8B6B3F")]:
        tags[name] = create_entity(db, Tag, {"name": name, "color": color}, "tag")

    record_specs = [
        ("1988 年春节团圆", "一家人在老屋一起吃年夜饭。", "周建国", ["春节", "家庭聚会"]),
        ("第一次上小学", "海峰背着新书包走进学校。", "周海峰", ["成长"]),
        ("搬到新家", "全家搬进了有阳台的新房子。", "陈秀兰", ["家庭聚会"]),
        ("雨桐出生", "家里迎来了新成员。", "周雨桐", ["成长"]),
        ("周末包饺子", "孩子们第一次学习擀饺子皮。", "周星宇", ["家庭聚会"]),
    ]
    for title, text, person_name, tag_names in record_specs:
        record = create_entity(
            db,
            Record,
            {
                "title": title,
                "original_text": text,
                "edited_text": text,
                "person_ids": [people[person_name].id],
                "source_type": "personal_memory",
                "verification_status": "unverified_information",
            },
            "record",
        )
        record.tags = [tags[name] for name in tag_names]
        db.commit()
    return counts(db, already_loaded=False)


def counts(db: Session, already_loaded: bool):
    return {
        "already_loaded": already_loaded,
        "persons": len(db.scalars(select(Person).where(Person.source_text == DEMO_MARKER, Person.deleted_at.is_(None))).all()),
        "relationships": len(db.scalars(select(Relationship).where(Relationship.source_text == DEMO_MARKER, Relationship.deleted_at.is_(None))).all()),
        "records": 5,
        "tags": 3,
    }


@router.delete("")
def clear_demo(db: Session = Depends(get_db)):
    records = db.scalars(select(Record).where(Record.title.in_(["1988 年春节团圆", "第一次上小学", "搬到新家", "雨桐出生", "周末包饺子"]), Record.deleted_at.is_(None))).unique().all()
    relationships = db.scalars(select(Relationship).where(Relationship.source_text == DEMO_MARKER, Relationship.deleted_at.is_(None))).all()
    persons = db.scalars(select(Person).where(Person.source_text == DEMO_MARKER, Person.deleted_at.is_(None))).all()
    for item in records:
        delete_entity(db, item, "record")
    for item in relationships:
        delete_entity(db, item, "relationship")
    for item in persons:
        delete_entity(db, item, "person")
    return {"cleared": True}
