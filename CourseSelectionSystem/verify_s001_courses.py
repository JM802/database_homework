"""仅在随机隔离 MySQL 数据库中验证 S001 额外选课脚本。"""
from pathlib import Path
import re, secrets, subprocess

root = Path(__file__).resolve().parent
name = 'coursework_test_' + secrets.token_hex(6)
assert re.fullmatch(r'coursework_test_[a-f0-9]{12}', name)
mysql = ['mysql', '--login-path=coursework', '--default-character-set=utf8mb4', '--batch', '--skip-column-names']
created = False

def sql(text):
    r = subprocess.run(mysql, input=text, encoding='utf-8', capture_output=True)
    if r.returncode:
        raise RuntimeError(r.stderr.strip())
    return r.stdout

try:
    sql(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;')
    created = True
    schema = (root/'sql/01_create.sql').read_text(encoding='utf-8')
    schema = re.sub(r'(?im)^CREATE DATABASE university_coursework[^;]*;', f'USE `{name}`;', schema)
    schema = re.sub(r'(?im)^USE university_coursework;', f'USE `{name}`;', schema)
    sql(schema)
    seed = (root/'sql/02_seed.sql').read_text(encoding='utf-8').replace('USE university_coursework;', f'USE `{name}`;')
    sql(seed)
    extra = (root/'sql/05_s001_extra_courses.sql').read_text(encoding='utf-8').replace('USE university_coursework;', f'USE `{name}`;')
    sql(extra)
    sql(extra)
    result = sql(f"""USE `{name}`;
        SELECT COUNT(*) FROM enrollment WHERE student_no='S001' AND withdrawn_at IS NULL
        AND class_no IN ('2025-2026-2-003','2025-2026-2-004','2025-2026-2-005');
        SELECT COUNT(*) FROM teaching_class c WHERE c.active_count<>(
          SELECT COUNT(*) FROM enrollment e WHERE e.class_no=c.class_no AND e.withdrawn_at IS NULL);
        SELECT COUNT(*) FROM enrollment WHERE student_no='S001' AND class_no='2025-2026-2-001';""")
    values = result.strip().splitlines()
    if values != ['3', '0', '2']:
        raise AssertionError(f'检查失败: {values}')
    audit = (root/'sql/04_audit.sql').read_text(encoding='utf-8').replace('USE university_coursework;', f'USE `{name}`;')
    audit_lines = sql(audit).strip().splitlines()
    checks = audit_lines[-18:]
    if len(checks) != 18 or any(not line.endswith('\t0') for line in checks):
        raise AssertionError(f'业务一致性检查未通过: {checks}')
    print('PASS: 增加三门课程、重复执行不重复、人数缓存一致、原有退选和重选记录保留、18项一致性检查通过')
finally:
    if created:
        try:
            sql(f'DROP DATABASE `{name}`;')
            print('本次隔离测试库已清理。')
        except Exception as error:
            print(f'隔离测试库 {name} 清理失败: {error}')
