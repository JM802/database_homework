import argparse
import re
import subprocess
from pathlib import Path

TABLE_ROWS = {
    "account": 27, "student": 14, "teacher": 12, "course": 10, "academic_term": 12,
    "class_number_counter": 12, "teaching_class": 13, "weekly_time": 14, "classroom": 2430,
    "class_schedule": 14, "teaching_assignment": 14, "enrollment": 19, "grade": 14,
    "grade_revision": 10,
}
NEGATIVE_CASES = [
    ("N01 duplicate primary key", 1062, "INSERT INTO course VALUES ('C001','Duplicate')"),
    ("N02 orphan foreign key", 1452, "INSERT INTO class_schedule VALUES ('2025-2026-2-001',3,999999)"),
    ("N03 duplicate natural time", 1062, "INSERT INTO weekly_time (weekday_no,start_period,end_period) VALUES (1,1,1)"),
    ("N04 grade out of range", 3819, "UPDATE grade SET score=101 WHERE enrollment_id=1"),
    ("N05 missing name", 1048, "INSERT INTO course VALUES ('C999',NULL)"),
    ("N06 invalid classroom", 3819, "INSERT INTO classroom (room_name) VALUES ('3区4教101')"),
    ("N07 backward withdrawal", 3819, "UPDATE enrollment SET withdrawn_at='2026-03-01' WHERE enrollment_id=1"),
    ("N08 over capacity", 3819, "UPDATE teaching_class SET active_count=13 WHERE class_no='2025-2026-2-001'"),
    ("N09 referenced room deletion", 1451, "DELETE FROM classroom WHERE room_id=1"),
    ("N10 invalid weekday", 3819, "INSERT INTO weekly_time (weekday_no,start_period,end_period) VALUES (8,1,2)"),
    ("N11 role mismatch", 3819, "INSERT INTO account VALUES ('s999999','T',REPEAT('x',32))"),
    ("N12 duplicate classroom", 1062, "INSERT INTO classroom (room_name) VALUES ('1区1教101')"),
    ("N13 invalid class prefix", 3819, "INSERT INTO teaching_class VALUES ('2025-2026-1-099','C001','2025-2026',2,10,0)"),
]
DATABASE = "university_coursework"


def verify(connection, folder, initialize):
    base = ["mysql", *connection, "--default-character-set=utf8mb4"]
    evidence = folder / "运行证据"

    def call(sql, *flags):
        return subprocess.run([*base, *flags], input=sql,
                              capture_output=True, encoding="utf-8")

    def execute(sql, *flags):
        result = call(sql, *flags)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout

    chunks = {}
    if initialize:
        for file in ("01_create.sql", "02_seed.sql"):
            execute((folder.parent / "CourseSelectionSystem" / "sql" / file).read_text(encoding="utf-8"))
            chunks[file] = "exit status: 0\n"
            print(file + " OK", flush=True)
    header = execute("SELECT VERSION() AS mysql_version, DATABASE() AS db, @@sql_mode AS sql_mode;",
                     "--database=" + DATABASE, "--table")
    chunks["MySQL server"] = header

    raw_queries = (folder.parent / "CourseSelectionSystem" / "sql" / "03_queries.sql").read_text(encoding="utf-8")
    query_blocks = re.split(r"(?m)(?=^-- Q[0-9]{2} )", raw_queries)
    csv_results = {}
    for block in query_blocks[1:]:
        title, sql = block.split("\n", 1)
        key = title.split()[1]
        actual = execute(query_blocks[0] + sql, "--batch", "--raw").splitlines()
        csv_results[key] = [line.split("\t") for line in actual[1:]]
        chunks[title[3:]] = execute(query_blocks[0] + sql, "--table")
    assert len(csv_results) == 8
    assert [row[1] for row in csv_results["Q01"]] == [f"3区3教10{i}" for i in range(1, 6)]
    assert len(csv_results["Q02"]) == 10 and csv_results["Q02"][-1][-1] == "17"
    assert len(csv_results["Q03"]) == 2 and len({row[-1] for row in csv_results["Q03"]}) == 2
    assert len(csv_results["Q04"]) == 2 and all(row[-1] == "暂无成绩" for row in csv_results["Q04"])
    assert csv_results["Q05"][0][1:] == ["13", "12", "1"]
    assert {row[0] for row in csv_results["Q06"]} == {"2025-2026-2-001", "2025-2026-3-001"}
    assert [row[0] for row in csv_results["Q07"]] == ["S014"]
    first = csv_results["Q08"][0]
    assert len(csv_results["Q08"]) == 10 and first[4:8] == ["15", "2", "3", "13"]
    assert first[8:] == ["72.33", "2.25", "10", "3"], first

    audit = (folder.parent / "CourseSelectionSystem" / "sql" / "04_audit.sql").read_text(encoding="utf-8")
    outputs = execute(audit, "--batch", "--raw").splitlines()
    split = outputs.index("audit_rule\tviolations")
    counts = {name: int(count) for name, count in [row.split("\t") for row in outputs[1:split]]}
    violations = {name: int(count) for name, count in [row.split("\t") for row in outputs[split+1:]]}
    assert counts == TABLE_ROWS, counts
    assert len(violations) == 18 and all(count == 0 for count in violations.values()), violations
    chunks["04_audit.sql"] = execute(audit, "--table")

    chunk = []
    for label, expected_code, statement in NEGATIVE_CASES:
        result = call(f"START TRANSACTION; {statement}; ROLLBACK;", "--database=" + DATABASE)
        error = re.search(r"ERROR (\d+)", result.stderr)
        assert result.returncode != 0 and error and int(error.group(1)) == expected_code, (label, result.stderr)
        chunk.append(label + "\n" + result.stderr.strip())
    chunks["Negative tests"] = "\n\n".join(chunk) + "\n"

    audit_after = execute(audit, "--batch", "--raw").splitlines()
    assert audit_after == outputs
    chunks["Post-test audit"] = "All fixture counts and business audit results unchanged.\n"

    delete_preview = """SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
START TRANSACTION;
SET @target = '2025-2026-2-001';
DELETE v FROM grade_revision AS v JOIN enrollment AS e USING(enrollment_id) WHERE e.class_no=@target;
DELETE g FROM grade AS g JOIN enrollment AS e USING(enrollment_id) WHERE e.class_no=@target;
DELETE FROM enrollment WHERE class_no=@target;
DELETE FROM class_schedule WHERE class_no=@target;
DELETE FROM teaching_assignment WHERE class_no=@target;
DELETE FROM teaching_class WHERE class_no=@target;
SELECT
    (SELECT COUNT(*) FROM teaching_class WHERE class_no=@target) AS deleted_class,
    (SELECT COUNT(*) FROM enrollment WHERE class_no=@target) AS remaining_class_events,
    (SELECT COUNT(*) FROM classroom) AS preserved_rooms,
    (SELECT COUNT(*) FROM weekly_time) AS preserved_times,
    (SELECT last_serial FROM class_number_counter WHERE academic_year='2025-2026' AND term_no=2) AS preserved_serial;
ROLLBACK;
SELECT COUNT(*) AS restored_class FROM teaching_class WHERE class_no=@target;
"""
    lines = execute(delete_preview, "--database=" + DATABASE, "--batch", "--raw").splitlines()
    assert lines[1] == "0\t0\t2430\t14\t11" and lines[3] == "1", lines
    assert execute(audit, "--batch", "--raw").splitlines() == outputs
    chunks["Delete transaction and rollback"] = execute(delete_preview, "--database=" + DATABASE, "--table")
    evidence.mkdir(exist_ok=True)
    content = "\n".join(f"=== {title} ===\n{output.rstrip()}\n" for title, output in chunks.items())
    (evidence / "MySQL实际运行.txt").write_text(content, encoding="utf-8")
    print("PASS: 8 business queries, 14 table counts, 18 business audit rules, 13 negative tests, and class deletion rollback.", flush=True)


if __name__ == "__main__":
    args = argparse.ArgumentParser(description="Run and verify Task 4 with MySQL.")
    args.add_argument("--initialize", action="store_true", help="Create and seed a new coursework database first.")
    args.add_argument("connection", nargs=argparse.REMAINDER, help="MySQL connection arguments after --.")
    options = args.parse_args()
    connection = options.connection[1:] if options.connection[:1] == ["--"] else options.connection
    verify(connection, Path(__file__).resolve().parent, options.initialize)
