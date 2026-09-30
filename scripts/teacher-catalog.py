#!/usr/bin/env python3
"""Prepare an atomic, tenant-scoped catalog release or verify the public result."""
import argparse
import base64
import json
from pathlib import Path
from urllib.request import urlopen


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--sql", type=Path)
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--base", default="https://droos.com.co")
    args = parser.parse_args()
    catalog = json.loads(args.manifest.read_text(encoding="utf-8"))
    courses = catalog["courses"]
    if len(courses) != 9 or len({c["title"] for c in courses}) != 9:
        raise ValueError("Expected nine distinct grade-specific courses")
    aid, tenant, teacher = (int(catalog[k]) for k in ("academyId", "tenantId", "teacherId"))
    slug = literal(catalog["slug"])
    if args.verify:
        def get(path):
            with urlopen(args.base.rstrip("/") + path, timeout=30) as response:
                return json.load(response)
        page = get("/api/public/academies/" + catalog["slug"])
        assert page["profile"]["demoContent"] is False
        assert page["profile"]["published"] is True
        visible = {c["title"]: c for c in page["courses"]}
        assert set(visible) == {c["title"] for c in courses}, "Public catalog differs"
        for expected in courses:
            actual = visible[expected["title"]]
            assert actual["description"] == expected["description"]
            assert actual["year"] == expected["grade"]
            with urlopen(args.base.rstrip("/") + actual["coverUrl"], timeout=30) as response:
                assert response.headers.get_content_type() in ("image/jpeg", "image/png")
                assert response.read(8).startswith((b"\x89PNG", b"\xff\xd8\xff"))
        card = next(c for c in get("/api/public/academies") if c["slug"] == catalog["slug"])
        assert card["courseCount"] == 9
        print(json.dumps({"courses": 9, "descriptions": "verified", "images": 9,
                          "demoContent": False, "directoryCount": card["courseCount"]}))
        return
    if not args.sql:
        parser.error("Choose --sql PATH or --verify")
    statements = ["BEGIN;", "SET LOCAL lock_timeout = '10s';", "DO $catalog$",
        "DECLARE branch bigint; cover bigint; course_id bigint; BEGIN",
        f"PERFORM 1 FROM teacher_academies WHERE id={aid} AND tenant_id={tenant} "
        f"AND teacher_id={teacher} AND slug={slug} FOR UPDATE;",
        "IF NOT FOUND THEN RAISE EXCEPTION 'Academy identity does not match'; END IF;",
        f"SELECT branch_id INTO branch FROM users WHERE id={teacher} AND tenant_id={tenant} AND role='TEACHER';",
        "IF branch IS NULL THEN RAISE EXCEPTION 'Teacher identity does not match'; END IF;"]
    titles = ",".join(literal(c["title"]) for c in courses)
    statements += [f"UPDATE courses SET status='HIDDEN' WHERE tenant_id={tenant} AND teacher_id={teacher} "
                   f"AND status='ACTIVE' AND title NOT IN ({titles});"]
    for c in courses:
        raw = (args.manifest.parent / c["image"]).read_bytes()
        mime = "image/png" if raw.startswith(b"\x89PNG\r\n\x1a\n") else "image/jpeg" if raw.startswith(b"\xff\xd8\xff") else None
        if not mime or len(raw) > 3 * 1024 * 1024:
            raise ValueError("Invalid or oversized image: " + c["image"])
        data = literal(base64.b64encode(raw).decode("ascii"))
        statements += [f"SELECT id INTO cover FROM public_images WHERE tenant_id={tenant} AND content_type={literal(mime)} AND md5(data)=md5({data}) LIMIT 1;",
            "IF cover IS NULL THEN",
            f"INSERT INTO public_images (tenant_id,content_type,data,created_by,created_at) VALUES ({tenant},{literal(mime)},{data},NULL,now()::text) RETURNING id INTO cover;",
            "END IF;",
            f"IF (SELECT count(*) FROM courses WHERE tenant_id={tenant} AND teacher_id={teacher} AND status <> 'DELETED' AND title={literal(c['title'])}) > 1 THEN RAISE EXCEPTION 'Duplicate course title'; END IF;",
            f"SELECT id INTO course_id FROM courses WHERE tenant_id={tenant} AND teacher_id={teacher} AND status <> 'DELETED' AND title={literal(c['title'])};",
            "IF course_id IS NULL THEN",
            f"INSERT INTO courses (tenant_id,branch_id,teacher_id,title,subject,grade_level,grade,description,price,status,cover_url,created_at) VALUES ({tenant},branch,{teacher},{literal(c['title'])},{literal(c['subject'])},{literal(c['gradeLevel'])},{literal(c['grade'])},{literal(c['description'])},1,'ACTIVE','/api/public/images/' || cover,now()::text);",
            "ELSE",
            f"UPDATE courses SET subject={literal(c['subject'])},grade_level={literal(c['gradeLevel'])},grade={literal(c['grade'])},description={literal(c['description'])},cover_url='/api/public/images/' || cover,status='ACTIVE' WHERE id=course_id AND tenant_id={tenant} AND teacher_id={teacher};",
            "END IF;",
            "cover := NULL; course_id := NULL;"]
    statements += [f"UPDATE teacher_academies SET demo_content=false WHERE id={aid} AND tenant_id={tenant} AND teacher_id={teacher} AND slug={slug};",
        f"IF (SELECT count(*) FROM courses WHERE tenant_id={tenant} AND teacher_id={teacher} AND status='ACTIVE') <> 9 THEN RAISE EXCEPTION 'Expected exactly nine active courses'; END IF;",
        f"INSERT INTO audit_logs (tenant_id,actor_name,action,entity_type,entity_id,new_value,created_at) VALUES ({tenant},'Authorized catalog release','ACADEMY_CATALOG_RELEASED','TeacherAcademy',{aid},'Nine grade-specific courses; demo disabled; previous active courses hidden',now()::text);",
        "END $catalog$;", "COMMIT;"]
    args.sql.write_text("\n".join(statements) + "\n", encoding="utf-8")
    print("Prepared atomic catalog release:", args.sql)


if __name__ == "__main__":
    main()
