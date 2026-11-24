# 学校选课系统 JDBC 应用初版

> 本文根据 Git 提交 `bef7dc2` 的 Java 源码和运行说明整理初版结果。修改前的表现也可对照[任务五修改建议 1](修改文档/修改建议/修改建议1.md)。现行程序见[说明与功能测试](说明与功能测试.md)。

## 1. JDBC 连接和菜单

`DBUtil` 从项目目录的 `.env` 读取 MySQL 连接配置，用 `DriverManager.getConnection` 建立连接。`Main` 负责登录和命令行菜单，根据账号角色进入管理员、教师或学生菜单。SQL 参数用 `PreparedStatement` 绑定，查询用 `ResultSet` 读出结果。

| 类 | 初版内容 |
|---|---|
| `CourseDAO` | 新增课程、按课程号修改和删除课程，查询时按课程号排序列出全部课程。 |
| `QueryService` | 按教师姓名查教学班，按班号查有效选课学生和成绩，按班号或课程及学期做统计。教师查名单和统计前要核对是否任教。 |
| `Main` | 学生可查本人选课记录和个人课表，课表只列有效选课。 |

## 2. 选退课与成绩事务

- `EnrollmentService` 选课时检查时间窗口、余量、同一学期是否已选同一门课，以及上课时间冲突。写入选课记录和增加有效人数用同一条连接，在同一个事务中完成。
- 退课时检查时间窗口、是否已有成绩及本学期的期中退课次数。保留选课记录，填上退课时间，同时减少有效人数。
- `GradeService` 检查教师是否任课、选课是否有效。修改成绩时在同一事务中更新分数和写入修改历史。

初版 `EnrollmentService` 中的事务方法如下。

```java
private void transaction(SqlAction action) throws SQLException {
    if (!db.getAutoCommit()) throw new IllegalStateException("连接已处于事务中");
    db.setAutoCommit(false);
    try { action.run(); db.commit(); }
    catch (SQLException | RuntimeException e) { try { db.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); } throw e; }
    finally { db.setAutoCommit(true); }
}
```

## 3. 初版的查询输出与运行方式

| 操作 | 初版表现 |
|---|---|
| 学生查选课记录 | 有效记录显示分数或“暂无成绩”，退课记录显示“已退课”。输出当时没有绩点、退课时间和退课类型。 |
| 学生筛选有余量的班 | 逐条跳过不在窗口内或没有余量的班。筛完没有符合条件的班时，列表为空。 |
| 按班号查名单、统计 | 名单直接遍历查询结果。管理员查不存在的班号时，名单为空，班级统计仍会输出零计数。 |
| 按教师姓名查教学班 | 根据任课关系筛选班级，无匹配时没有单独提示。 |
| 运行集成测试 | `run_integration_test.py` 在随机命名的隔离库中执行测试，结束后清理测试库。当时 `java` 和 `javac` 的位置写成了 F 盘的固定路径。 |
