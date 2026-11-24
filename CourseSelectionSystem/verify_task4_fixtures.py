"""在隔离库验证任务四原始样本和 S001 扩充样本的两种验证口径。"""
from pathlib import Path
import re, secrets, subprocess, sys

project=Path(__file__).resolve().parent
repo=project.parent
name='coursework_test_'+secrets.token_hex(6)
assert re.fullmatch(r'coursework_test_[a-f0-9]{12}',name)
mysql=['mysql','--login-path=coursework','--default-character-set=utf8mb4']
created=False

def execute(script):
    result=subprocess.run(mysql,input=script,encoding='utf-8',capture_output=True)
    if result.returncode: raise RuntimeError(result.stderr.strip())

try:
    execute(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;')
    created=True
    schema=(project/'sql/01_create.sql').read_text(encoding='utf-8')
    schema=re.sub(r'(?im)^CREATE DATABASE university_coursework[^;]*;',f'USE `{name}`;',schema)
    schema=re.sub(r'(?im)^USE university_coursework;',f'USE `{name}`;',schema)
    execute(schema)
    seed=(project/'sql/02_seed.sql').read_text(encoding='utf-8').replace('USE university_coursework;',f'USE `{name}`;')
    execute(seed)
    checker=repo/'物理设计与SQL实现'/'verify_mysql.py'
    base=[sys.executable,str(checker),'--database',name,'--no-evidence']
    subprocess.run([*base,'--fixture','baseline','--','--login-path=coursework'],check=True)
    extra=(project/'sql/05_s001_extra_courses.sql').read_text(encoding='utf-8').replace('USE university_coursework;',f'USE `{name}`;')
    execute(extra)
    subprocess.run([*base,'--fixture','s001','--','--login-path=coursework'],check=True)
    print('PASS: 原始样本和 S001 完整扩充样本均按各自口径通过验证',flush=True)
finally:
    if created:
        try:
            execute(f'DROP DATABASE `{name}`;')
            print('本次隔离测试库已清理。',flush=True)
        except Exception as error:
            print(f'隔离测试库 {name} 清理失败: {error}',file=sys.stderr,flush=True)
