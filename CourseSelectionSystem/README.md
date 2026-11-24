# 高校教学管理系统：JDBC 程序

项目使用 MySQL、JDBC 和 Maven，读取当前目录的 `.env`。用户已在本机隔离 MySQL 数据库运行集成测试。功能状态及测试记录见 [业务测试记录](业务测试记录.md) 和 [运行说明与功能测试](../JDBC应用编程/说明与功能测试.md)。

`.env` 保存数据库连接和 `APP_SECRET`；该密钥用于派生账号密码盐，更换或丢失后已有账号密码无法用原方式验证。`.env` 已被 Git 忽略，不要提交或截图。

新环境可以参考可提交的 `.env.example` 创建 `.env`，填写本机数据库密码，并生成至少 32 个字符的随机 `APP_SECRET`。已有 `.env` 时请保留原密钥，不要覆盖；示例中的占位值不能直接用于登录。

## 项目目录和数据库脚本

```text
CourseSelectionSystem/
├── pom.xml
├── sql/
│   ├── 01_create.sql
│   ├── 02_seed.sql
│   ├── 03_queries.sql
│   ├── 04_audit.sql
│   └── 05_s001_extra_courses.sql
├── src/main/java/edu/coursework/
│   ├── Main.java
│   ├── util/DBUtil.java、Rules.java
│   ├── dao/CourseDAO.java、StudentDAO.java、TeacherDAO.java、EnrollmentDAO.java、PersonDAO.java
│   └── service/AdminService.java、AuthService.java、EnrollmentService.java、GradeService.java、QueryService.java
├── src/test/java/edu/coursework/
├── run_integration_test.py
├── verify_s001_courses.py
└── README.md
```

项目使用 Maven 标准的 `src/main/java` 目录。课程、学生、教师和选退课的数据访问分别位于 `CourseDAO`、`StudentDAO`、`TeacherDAO` 和 `EnrollmentDAO`。`PersonDAO` 提供学生与教师的公共数据访问；业务规则与事务仍由 `service` 层处理。

在全新数据库环境中，从仓库根目录先进入 `CourseSelectionSystem` 并启动 MySQL，然后在 `mysql>` 中依次执行；`SOURCE` 路径相对启动 MySQL 时的当前目录：

```sql
SOURCE sql/01_create.sql;
SOURCE sql/02_seed.sql;
```

已有 `university_coursework` 数据库时不重复执行建库和初始化脚本。`03_queries.sql` 是查询，`04_audit.sql` 是一致性检查；`05_s001_extra_courses.sql` 是可选的样例扩充脚本，执行后原始样例的固定行数测试会改变。

## 账号和密码

系统使用两类不同的账号，不要混淆：

| 用途 | 账号 | 密码及使用位置 |
|---|---|---|
| 连接 MySQL 数据库 | `root` | 程序自动读取项目根目录 `.env` 中的 `DB_PASSWORD`，不在系统登录界面输入。 |
| 登录本系统的管理员 | `admin` | 固定密码为 `Q7!mV2#rK9@pL4$xN8%t`；在程序提示“账号”和“密码”时输入。首次使用任务四数据前，需要先按下方步骤初始化。 |
| 登录本系统的学生 | 例如 `s000001` | 由管理员设置初始密码；任务四的占位密码不能用于登录，没有统一默认密码。 |
| 登录本系统的教师 | 例如 `t000001` | 由管理员设置初始密码；任务四的占位密码不能用于登录，没有统一默认密码。 |

以下 PowerShell 命令均从仓库根目录开始；若终端已在 `CourseSelectionSystem` 内，跳过相应的 `cd` 行。

## 第一步：首次初始化管理员

任务四的账号密码哈希仅用于 SQL 测试，不是可登录密码。初始化会修改当前 `.env` 指定的数据库中 `admin` 账号的密码哈希；该命令仅处理仍为任务四占位哈希的管理员，不会覆盖已初始化的密码。已初始化过时无需重复执行。

初始化程序需要先编译，因此下列命令会先执行 `mvn compile`，再初始化账号。在 PowerShell 中依次执行：

```powershell
cd .\CourseSelectionSystem
chcp 65001
mvn compile
$connector = "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar"
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
& $java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp "target/classes;$connector" edu.coursework.Main --initialize-admin
```

程序提示输入管理员初始密码时，输入上表的管理员固定密码并回车；成功后显示“管理员测试占位密码已初始化”。密码输入时可能不显示字符。然后按下面的“编译并运行程序”步骤启动程序，在系统登录提示处输入账号 `admin` 和该固定密码。

## 第二步：编译并运行程序

```powershell
cd .\CourseSelectionSystem
chcp 65001
mvn compile
$connector = "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar"
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
& $java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp "target/classes;$connector" edu.coursework.Main
```

### 设置学生和教师密码

1. 使用 `admin` 登录后，在管理员菜单输入 `5`，选择“重置密码”。
2. 输入已有的学生或教师**账号**，如 `s000001` 或 `t000001`；它不同于学生编号 `S001` 或教师编号 `T001`。
3. 按提示设置密码。退出后使用该账号和刚设置的密码登录；学生和教师登录后可以自行修改密码。

菜单中的“6 人员管理”可查看学生、教师编号与账号的对应关系。管理员密码固定，不能通过重置密码功能修改。

## 隔离数据库测试

```powershell
cd .\CourseSelectionSystem
python .\run_integration_test.py
python .\verify_task4_fixtures.py
```

两个测试脚本分别覆盖 JDBC 业务及任务四原始／S001 完整扩充样本，均使用 `coursework` MySQL 登录路径，创建随机命名的 `coursework_test_...` 数据库并在结束后删除，只操作本次创建的测试库。脚本覆盖部分主要事务及其失败场景；具体覆盖项目见测试源码。其余角色菜单、显示和边界场景仍需补充，不应将部分测试等同于全部需求验收。
