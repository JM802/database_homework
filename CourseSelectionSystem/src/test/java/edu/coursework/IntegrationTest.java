package edu.coursework;

import edu.coursework.util.*;
import edu.coursework.dao.*;
import edu.coursework.service.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** 仅由隔离测试脚本运行，目标库名必须具有专用前缀。 */
public class IntegrationTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS | "+message);}
    static void execute(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement()){s.executeUpdate(sql);}}
    static int scalar(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
    @FunctionalInterface interface CheckedAction {void run() throws Exception;}
    static String capture(CheckedAction action)throws Exception{
        java.io.PrintStream original=System.out;java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(java.io.PrintStream out=new java.io.PrintStream(bytes,true,java.nio.charset.StandardCharsets.UTF_8)){System.setOut(out);action.run();}finally{System.setOut(original);}
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
    static String runMenu(java.nio.file.Path dir,String input)throws Exception{
        String javaExe=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        Process process=new ProcessBuilder(javaExe,"-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",System.getProperty("java.class.path"),"edu.coursework.Main").directory(dir.toFile()).redirectErrorStream(true).start();
        try(java.io.OutputStream out=process.getOutputStream()){out.write(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        if(!process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("菜单测试超时");}
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(process.exitValue()!=0)throw new AssertionError("菜单测试失败: "+output);
        return output;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1||!args[0].matches("coursework_test_[a-f0-9]{12}"))throw new IllegalArgumentException("仅允许隔离测试数据库");
        Map<String,String> cfg=DBUtil.settings();String url="jdbc:mysql://"+cfg.get("DB_HOST")+":"+cfg.get("DB_PORT")+"/"+args[0]+"?characterEncoding=UTF-8";
        try(Connection c=DriverManager.getConnection(url,cfg.get("DB_USER"),cfg.get("DB_PASSWORD"))){
            execute(c,"INSERT INTO account VALUES ('s000001','S',UNHEX(SHA2('fixture',256))),('t000001','T',UNHEX(SHA2('fixture',256)))");
            execute(c,"INSERT INTO student VALUES ('S_TEST','测试学生','s000001')");
            execute(c,"INSERT INTO teacher VALUES ('T_TEST','测试教师','t000001')");
            execute(c,"INSERT INTO course VALUES ('C_TEST','测试课程'),('C_OTHER','测试课程二')");
            execute(c,"INSERT INTO academic_term VALUES ('2025-2026',2,'2026-03-01')");
            execute(c,"INSERT INTO class_number_counter VALUES ('2025-2026',2,0)");
            execute(c,"INSERT INTO weekly_time(time_id,weekday_no,start_period,end_period) VALUES (1,1,1,2),(2,2,1,2)");
            execute(c,"INSERT INTO classroom(room_id,room_name) VALUES (1,'1区1教101'),(2,'1区1教102')");
            AdminService admin=new AdminService(c);
            admin.addTerm("2026-2027",2,LocalDate.of(2027,3,7));
            check(scalar(c,"SELECT COUNT(*) FROM academic_term WHERE academic_year='2026-2027' AND term_no=3 AND start_date='2027-07-11'")==1,"春季创建小学期");
            try{admin.addTerm("2027-2028",1,LocalDate.of(2027,3,7));throw new AssertionError("不同学年重叠应拒绝");}catch(IllegalArgumentException expected){}
            check(scalar(c,"SELECT COUNT(*) FROM academic_term WHERE academic_year='2027-2028'")==0,"跨学年重叠后事务回滚");
            admin.addTime(3,3,4);admin.addRoom("1区1教103");admin.renameRoom(3,"1区1教104");
            check(scalar(c,"SELECT COUNT(*) FROM classroom WHERE room_name='1区1教104'")==1,"教室新增和修改");
            admin.deleteRoom(3);
            AuthService auth=new AuthService(c,"0123456789abcdef0123456789abcdef");
            auth.createPerson("S","S_EXTRA","新学生","s000002","initial".toCharArray());
            check(auth.login("s000002","initial".toCharArray()).equals("S"),"新增账号登录");
            auth.changePassword("s000002","changed".toCharArray());
            try{auth.login("s000002","initial".toCharArray());throw new AssertionError("旧密码应失效");}catch(IllegalArgumentException expected){}
            check(auth.login("s000002","changed".toCharArray()).equals("S"),"新密码登录");
            admin.renamePerson("S","S_EXTRA","修改姓名");
            check(scalar(c,"SELECT COUNT(*) FROM student WHERE student_no='S_EXTRA' AND student_name='修改姓名'")==1,"修改学生");
            admin.deletePerson("S","S_EXTRA");check(scalar(c,"SELECT COUNT(*) FROM account WHERE account_name='s000002'")==0,"删除人员与账号");
            CourseDAO courses=new CourseDAO(c);courses.add("C_EXTRA","测试新增");
            try {courses.add("C_EXTRA","重复课程");throw new AssertionError("重复课程编号应被拒绝");} catch(SQLException expected) {check(expected.getErrorCode()==1062,"重复课程编号被唯一约束拒绝");}
            check(courses.update("C_MISSING","不存在的课程")==0,"修改不存在课程影响零行");
            courses.update("C_EXTRA","测试修改");
            check(scalar(c,"SELECT COUNT(*) FROM course WHERE course_no='C_EXTRA' AND course_name='测试修改'")==1,"课程新增与修改");
            courses.delete("C_EXTRA");check(scalar(c,"SELECT COUNT(*) FROM course WHERE course_no='C_EXTRA'")==0,"课程删除");
            String first=admin.addClass("C_TEST","2025-2026",2,1,List.of("T_TEST"),List.of(new AdminService.Slot(1,1)));
            check(first.equals("2025-2026-2-001"),"教学班号生成");
            try {admin.addClass("C_OTHER","2025-2026",2,1,List.of("T_TEST"),List.of(new AdminService.Slot(1,2)));throw new AssertionError("应拒绝教师冲突");} catch(IllegalArgumentException expected) {}
            check(scalar(c,"SELECT COUNT(*) FROM teaching_class")==1,"创建失败应回滚教学班");
            check(scalar(c,"SELECT last_serial FROM class_number_counter")==1,"创建失败应回滚流水号");
            Instant normal=LocalDateTime.of(2026,3,2,10,0).atZone(Rules.BEIJING).toInstant();
            QueryService visibility=new QueryService(c);
            check(capture(()->visibility.roster("A","",first)).contains("暂无有效选课学生"),"存在空班提示名单为空");
            try{visibility.roster("A","","不存在的班");throw new AssertionError("不存在的班必须提示");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("不存在"),"不存在班号的名单提示");}
            try{visibility.statistics("A","","不存在的班");throw new AssertionError("不存在的班不能显示全零统计");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("不存在"),"不存在班号的统计提示");}
            check(capture(()->visibility.classes("A","","不存在的教师",false,normal)).contains("无匹配教学班"),"教师姓名无匹配提示");
            String visible=capture(()->visibility.classes("S","S_TEST","测试教师",false,normal));
            check(visible.contains(first)&&visible.contains("容量 1")&&visible.contains("1区1教101"),"学生窗口内显示班级、教师排课和容量");
            Instant after=LocalDateTime.of(2026,4,1,10,0).atZone(Rules.BEIJING).toInstant();
            check(capture(()->visibility.classes("S","S_TEST","",true,normal)).contains(first),"窗口内有余量筛选");
            check(capture(()->visibility.classes("S","S_TEST","",true,after)).contains("不能使用余量筛选"),"窗口外余量筛选提示");
            String hidden=capture(()->visibility.classes("S","S_TEST","",false,after));
            check(hidden.contains(first)&&!hidden.contains("容量")&&!hidden.contains("余量"),"学生窗口外隐藏名额");
            check(capture(()->visibility.classes("T","T_TEST","",false,after)).contains("容量 1"),"任课教师始终看到容量");
            check(!capture(()->visibility.classes("T","T_OTHER","",false,normal)).contains(first),"非任课教师不能查看他人班级");
            EnrollmentService enrollment=new EnrollmentService(c);enrollment.select("S_TEST",first,normal);
            check(scalar(c,"SELECT active_count FROM teaching_class WHERE class_no='"+first+"'")==1,"选课人数");
            check(capture(()->visibility.classes("S","S_TEST","",true,normal)).contains("没有符合条件的有余量教学班"),"窗口内满员筛选提示");
            check(capture(()->Main.myRecords(c,"S_TEST")).contains("暂无绩点"),"未录成绩不显示零绩点");
            try{enrollment.select("S_TEST",first,normal);throw new AssertionError("应拒绝重复选课");}catch(IllegalArgumentException expected){}
            check(scalar(c,"SELECT COUNT(*) FROM enrollment")==1,"重复选课不得留下记录");
            long id;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT enrollment_id FROM enrollment")){r.next();id=r.getLong(1);}
            GradeService grade=new GradeService(c);grade.record("T_TEST",id,60,normal);grade.record("T_TEST",id,90,normal.plusSeconds(3600));
            try {grade.record("T_TEST",id,70,normal.plusSeconds(169*3600));throw new AssertionError("应拒绝超期修改");}catch(IllegalArgumentException expected){}
            check(capture(()->Main.myRecords(c,"S_TEST")).contains("绩点: 4.0"),"已录成绩显示对应绩点");
            check(scalar(c,"SELECT score FROM grade")==90,"成绩修改");check(scalar(c,"SELECT COUNT(*) FROM grade_revision")==1,"成绩历史");
            QueryService queries=new QueryService(c);
            String roster=capture(()->queries.roster("T","T_TEST",first));
            check(roster.contains("S_TEST")&&roster.contains("90"),"教师名单显示有效学生和成绩");
            String stats=capture(()->queries.statistics("T","T_TEST",first));
            check(stats.contains("有效人数 1")&&stats.contains("平均分 90")&&stats.contains("满绩 1"),"教学班统计");
            String courseStats=capture(()->queries.courseStatistics("C_TEST","2025-2026",2));
            check(courseStats.contains("有效学生 1")&&courseStats.contains("平均绩点 4"),"指定课程学期统计");
            try{queries.roster("T","T_OTHER",first);throw new AssertionError("非任课教师不得查询名单");}catch(IllegalArgumentException expected){}
            try{grade.record("T_OTHER",id,50,normal);throw new AssertionError("非任课教师不得修改成绩");}catch(IllegalArgumentException expected){}
            check(scalar(c,"SELECT score FROM grade")==90,"越权成绩修改不得生效");
            execute(c,"CREATE TRIGGER reject_grade_history BEFORE INSERT ON grade_revision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced grade rollback'");
            try{grade.record("T_TEST",id,80,normal.plusSeconds(7200));throw new AssertionError("应触发成绩历史写入失败");}catch(SQLException expected){}
            execute(c,"DROP TRIGGER reject_grade_history");
            check(scalar(c,"SELECT score FROM grade")==90,"写历史失败应回滚成绩");
            check(scalar(c,"SELECT COUNT(*) FROM grade_revision")==1,"写历史失败不产生新记录");
            try{admin.updateClass(first,"C_TEST",List.of(new AdminService.Slot(2,2)));throw new AssertionError("有选课历史不得修改排课");}catch(IllegalArgumentException expected){}
            try{enrollment.withdraw("S_TEST",first,normal);throw new AssertionError("已有成绩应拒绝退课");}catch(IllegalArgumentException expected){}
            admin.deleteClass(first);check(scalar(c,"SELECT COUNT(*) FROM enrollment")==0,"删除班级关联数据");
            check(scalar(c,"SELECT COUNT(*) FROM classroom")==2,"删除班级保留教室");
            String second=admin.addClass("C_OTHER","2025-2026",2,1,List.of("T_TEST"),List.of(new AdminService.Slot(2,2)));
            check(second.endsWith("002"),"旧班号不可重用");
            enrollment.select("S_TEST",second,normal);enrollment.withdraw("S_TEST",second,normal.plusSeconds(60));
            check(scalar(c,"SELECT active_count FROM teaching_class")==0,"退课人数");
            check(scalar(c,"SELECT COUNT(*) FROM enrollment WHERE withdrawn_at IS NOT NULL")==1,"退课保留历史");
            String withdrawnRecords=capture(()->Main.myRecords(c,"S_TEST"));
            check(withdrawnRecords.contains("已退课")&&!withdrawnRecords.contains("成绩:"),"退课历史不显示成绩");
            check(withdrawnRecords.contains("常规退课")&&withdrawnRecords.contains("2026-03-02T10:01"),"常规退课类型和北京时间");
            String withdrawnTimetable=capture(()->Main.myTimetable(c,"S_TEST"));
            check(!withdrawnTimetable.contains("C_OTHER")&&!withdrawnTimetable.contains("1区1教102"),"退课班级不出现在学生课表");
            String withdrawnCatalog=capture(()->new QueryService(c).classes("S","S_TEST","",false,normal));
            check(withdrawnCatalog.contains("已退课：不显示该班排课和教室")&&!withdrawnCatalog.contains("1区1教102"),"学生目录隐藏已退班级教室");
            String teacherCatalog=capture(()->new QueryService(c).classes("T","T_TEST","",false,normal));
            check(teacherCatalog.contains("1区1教102"),"学生退课不影响教师查看教学班教室");
            execute(c,"CREATE TRIGGER reject_test_update BEFORE UPDATE ON teaching_class FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced rollback test'");
            try{enrollment.select("S_TEST",second,normal.plusSeconds(120));throw new AssertionError("应触发事务失败");}catch(SQLException expected){}
            execute(c,"DROP TRIGGER reject_test_update");
            check(scalar(c,"SELECT COUNT(*) FROM enrollment")==1,"中途失败必须回滚选课记录");
            check(scalar(c,"SELECT active_count FROM teaching_class")==0,"中途失败必须保持人数");
            
            Instant before=LocalDateTime.of(2026,2,28,23,59).atZone(Rules.BEIJING).toInstant();
            try{enrollment.select("S_TEST",second,before);throw new AssertionError("选课窗口之前应拒绝");}catch(IllegalArgumentException expected){}
            String third=admin.addClass("C_TEST","2025-2026",2,2,List.of("T_TEST"),List.of(new AdminService.Slot(1,1)));
            enrollment.select("S_TEST",second,normal.plusSeconds(180));
            enrollment.select("S_TEST",third,normal.plusSeconds(180));
            int activeId=scalar(c,"SELECT enrollment_id FROM enrollment WHERE student_no='S_TEST' AND class_no='"+second+"' AND withdrawn_at IS NULL");
            int withdrawnId=scalar(c,"SELECT enrollment_id FROM enrollment WHERE student_no='S_TEST' AND class_no='"+second+"' AND withdrawn_at IS NOT NULL");
            String restoredTimetable=capture(()->Main.myTimetable(c,"S_TEST"));
            check(restoredTimetable.contains("选课记录号 "+activeId+" |")&&!restoredTimetable.contains("选课记录号 "+withdrawnId+" |"),"课表显示当前有效选课记录号且排除退课记录号");
            check(restoredTimetable.contains("| "+second+" |")&&!restoredTimetable.contains("教学班号")&&!restoredTimetable.contains("课程号")&&restoredTimetable.contains("1区1教102"),"重新选课后课表展示教学班号和教室");
            String restoredCatalog=capture(()->new QueryService(c).classes("S","S_TEST","",false,normal));
            check(restoredCatalog.contains("1区1教102"),"重新选课后目录恢复教室");
            String teacherList=capture(()->Main.teacherClasses(c,"T_TEST"));
            check(teacherList.contains("课程号 C_OTHER"),"教师教学班列表展示课程号");
            Instant mid=LocalDateTime.of(2026,5,3,10,0).atZone(Rules.BEIJING).toInstant();
            enrollment.withdraw("S_TEST",second,mid);
            String afterMid=capture(()->Main.myRecords(c,"S_TEST"));
            check(afterMid.contains("期中退课")&&afterMid.contains("2026-05-03T10:00"),"期中退课类型和北京时间");
            try{enrollment.withdraw("S_TEST",third,mid.plusSeconds(60));throw new AssertionError("每学期期中退课只能一次");}catch(IllegalArgumentException expected){}
            try{enrollment.select("S_TEST",second,mid);throw new AssertionError("期中不得选课");}catch(IllegalArgumentException expected){}
            check(scalar(c,"SELECT COUNT(*) FROM enrollment WHERE withdrawn_at IS NULL")==1,"期中退课次数限制后数据一致");
            try{courses.delete("C_TEST");throw new AssertionError("被引用课程不得删除");}catch(SQLException expected){}
            try{admin.deleteRoom(1);throw new AssertionError("被引用教室不得删除");}catch(SQLException expected){}
            try{admin.deletePerson("T","T_TEST");throw new AssertionError("有任课安排的教师不得删除");}catch(SQLException expected){}
            check(scalar(c,"SELECT COUNT(*) FROM teacher WHERE teacher_no='T_TEST'")==1,"拒绝删除后教师保留");
            
            auth.createPerson("T","T_OTHER","教师二","t000002","teacher2".toCharArray());
            auth.createPerson("S","S_TWO","学生二","s000002","student2".toCharArray());
            auth.createPerson("S","S_THREE","学生三","s000003","student3".toCharArray());
            courses.add("C_NEW","冲突课程");
            String fourth=admin.addClass("C_TEST","2025-2026",2,3,List.of("T_TEST","T_OTHER"),List.of(new AdminService.Slot(3,1)));
            String team=capture(()->new QueryService(c).classes("A","","教师二",false,normal));
            check(team.contains(fourth)&&team.contains("测试教师")&&team.contains("教师二"),"任一合授教师筛选并展示全部教师");
            try{enrollment.select("S_TEST",fourth,normal.plusSeconds(200));throw new AssertionError("同学期同课程不同班应拒绝");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("已选"),"同学期同课不同班拒绝");}
            enrollment.select("S_TWO",third,normal.plusSeconds(200));
            try{enrollment.select("S_THREE",third,normal.plusSeconds(200));throw new AssertionError("满员应拒绝");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("已满"),"容量满员拒绝");}
            try{admin.addClass("C_NEW","2025-2026",2,2,List.of("T_OTHER"),List.of(new AdminService.Slot(1,1)));throw new AssertionError("教室冲突应拒绝");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("教室"),"教室时段冲突拒绝");}
            String conflicting=admin.addClass("C_NEW","2025-2026",2,2,List.of("T_OTHER"),List.of(new AdminService.Slot(1,2)));
            try{enrollment.select("S_TWO",conflicting,normal.plusSeconds(300));throw new AssertionError("学生上课时间冲突应拒绝");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("冲突"),"学生选课时间冲突拒绝");}
            long zeroId;try(Statement q=c.createStatement();ResultSet row=q.executeQuery("SELECT enrollment_id FROM enrollment WHERE student_no='S_TWO' AND withdrawn_at IS NULL")){row.next();zeroId=row.getLong(1);}
            grade.record("T_TEST",zeroId,0,normal.plusSeconds(300));
            check(capture(()->Main.myRecords(c,"S_TWO")).contains("成绩: 0 | 绩点: 0.0"),"零分显示零绩点");
            String zeroStats=capture(()->queries.statistics("A","",third));
            check(zeroStats.contains("平均分 0")&&zeroStats.contains("平均绩点 0"),"零分参与统计且未录成绩不计入平均");
            try{grade.record("T_TEST",zeroId,101,normal.plusSeconds(300));throw new AssertionError("越界分数应拒绝");}catch(IllegalArgumentException expected){check(true,"越界分数拒绝");}
            execute(c,"INSERT INTO account VALUES ('admin','A',UNHEX(SHA2('task4-sql-fixture:admin',256)))");
            auth.initializeFixture("admin","Q7!mV2#rK9@pL4$xN8%t".toCharArray());
            auth.changePassword("s000001","studentpass".toCharArray());auth.changePassword("t000001","teacherpass".toCharArray());
            java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("coursework-cli-");
            try {
                String env="DB_HOST="+cfg.get("DB_HOST")+"\nDB_PORT="+cfg.get("DB_PORT")+"\nDB_NAME="+args[0]+"\nDB_USER="+cfg.get("DB_USER")+"\nDB_PASSWORD="+cfg.get("DB_PASSWORD")+"\nAPP_SECRET=0123456789abcdef0123456789abcdef\n";
                java.nio.file.Files.writeString(dir.resolve(".env"),env,java.nio.charset.StandardCharsets.UTF_8);
                String adminOutput=runMenu(dir,"admin\nQ7!mV2#rK9@pL4$xN8%t\n1\n0\n");
                if(!adminOutput.contains("登录成功")||!adminOutput.contains("C_TEST"))throw new AssertionError("管理员菜单输出异常: "+adminOutput.replace("Q7!mV2#rK9@pL4$xN8%t","[已隐藏]"));
                check(true,"管理员登录和课程查询菜单");
                String studentOutput=runMenu(dir,"s000001\nstudentpass\n1\n6\n0\n");
                check(studentOutput.contains("登录成功")&&studentOutput.contains("测试课程")&&studentOutput.contains("1区1教101"),"学生登录、本人记录和课表菜单");
                String teacherOutput=runMenu(dir,"t000001\nteacherpass\n1\n0\n");
                check(teacherOutput.contains("登录成功")&&teacherOutput.contains(third),"教师登录和本人教学班菜单");
            } finally {java.nio.file.Files.deleteIfExists(dir.resolve(".env"));java.nio.file.Files.deleteIfExists(dir);}
            System.out.println("PASS: 账号、学期、人员课程、引用约束、权限、教学班、窗口及期中限额、成绩、事务回滚及角色菜单");
        }
    }
}
