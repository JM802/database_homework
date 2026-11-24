"""在随机命名的临时数据库中运行集成测试，不使用或修改正式业务库。"""
from pathlib import Path
import os, re, secrets, subprocess, sys

root = Path(__file__).resolve().parent
name = "coursework_test_" + secrets.token_hex(6)
assert re.fullmatch(r"coursework_test_[a-f0-9]{12}", name)
mysql = ["mysql", "--login-path=coursework", "--default-character-set=utf8mb4"]
created = False

def run_sql(sql):
    return subprocess.run(mysql, input=sql, encoding="utf-8", capture_output=True)

try:
    javac = r"F:\java\bin\javac.exe"
    main_sources = [str(p) for p in (root/"src/main/java").rglob("*.java")]
    test_sources = [str(p) for p in (root/"src/test/java").rglob("*.java")]
    (root/"target/classes").mkdir(parents=True, exist_ok=True)
    (root/"target/test-classes").mkdir(parents=True, exist_ok=True)
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(root/"target/classes"), *main_sources], check=True)
    subprocess.run([javac, "-encoding", "UTF-8", "-cp", str(root/"target/classes"), "-d", str(root/"target/test-classes"), *test_sources], check=True)
    rule_cp = os.pathsep.join([str(root/"target/classes"), str(root/"target/test-classes")])
    subprocess.run([r"F:\java\bin\java.exe", "-cp", rule_cp, "edu.coursework.RulesTest"], cwd=root, check=True)
    result = run_sql(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;")
    if result.returncode:
        raise RuntimeError("创建隔离数据库失败: " + result.stderr.strip())
    created = True
    schema = (root / "sql" / "01_create.sql").read_text(encoding="utf-8")
    schema = re.sub(r"(?im)^CREATE DATABASE university_coursework[^;]*;", f"USE `{name}`;", schema)
    schema = re.sub(r"(?im)^USE university_coursework;", f"USE `{name}`;", schema)
    result = run_sql(schema)
    if result.returncode:
        raise RuntimeError("创建测试表失败: " + result.stderr.strip())
    jars = list((Path.home()/".m2/repository/com/mysql/mysql-connector-j/9.7.0").glob("*.jar"))
    if len(jars)!=1:
        raise RuntimeError("找不到 MySQL Connector/J 9.7.0 的 Maven JAR")
    cp = os.pathsep.join([str(root/"target/classes"),str(root/"target/test-classes"),str(jars[0])])
    cmd = [r"F:\java\bin\java.exe", "-cp", cp, "edu.coursework.IntegrationTest", name]
    test = subprocess.run(cmd, cwd=root)
    if test.returncode:
        raise RuntimeError(f"集成测试失败，进程退出码 {test.returncode}")
finally:
    if created:
        result = run_sql(f"DROP DATABASE `{name}`;")
        if result.returncode:
            print(f"无法删除本次隔离测试库 {name}：{result.stderr.strip()}", file=sys.stderr)
        else:
            print("本次隔离测试库已清理。")
