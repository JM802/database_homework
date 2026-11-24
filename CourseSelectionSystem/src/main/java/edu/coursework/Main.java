package edu.coursework;

import edu.coursework.util.DBUtil;
import edu.coursework.util.Rules;
import edu.coursework.dao.CourseDAO;
import edu.coursework.service.*;

import java.io.Console;
import java.sql.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Scanner;

/** 控制台入口。 */
public final class Main {
    private static final Scanner IN=new Scanner(System.in);
    static String input(String text){
        Console c=System.console();
        if(c!=null){String value=c.readLine("%s",text);if(value!=null)return value.trim();}
        System.out.print(text);
        if(!IN.hasNextLine())throw new IllegalArgumentException("没有提供输入");
        return IN.nextLine().trim();
    }
    static char[] password(String text){
        Console c=System.console();
        if(c!=null){char[] value=c.readPassword("%s",text);if(value!=null)return value;}
        System.out.print(text);if(!IN.hasNextLine())throw new IllegalArgumentException("没有提供密码");return IN.nextLine().toCharArray();
    }
    static String identity(Connection db,String table,String column,String account) throws SQLException {
        String sql="SELECT "+column+" FROM "+table+" WHERE account_name=?";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,account);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("账号未绑定人员");return r.getString(1);}}
    }
    static void myRecords(Connection db,String student) throws SQLException {
        String sql="SELECT e.enrollment_id,c.course_name,t.class_no,e.selected_at,e.withdrawn_at,g.score FROM enrollment e JOIN teaching_class t ON t.class_no=e.class_no JOIN course c ON c.course_no=t.course_no LEFT JOIN grade g ON g.enrollment_id=e.enrollment_id WHERE e.student_no=? ORDER BY e.selected_at";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,student);try(ResultSet r=p.executeQuery()){boolean found=false;while(r.next()){found=true;Object score=r.getObject(6);boolean active=r.getObject(5)==null;System.out.printf("选课记录号 %d | %s | %s | %s | %s",r.getLong(1),r.getString(2),r.getString(3),r.getObject(4,java.time.LocalDateTime.class).atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(Rules.BEIJING).toLocalDateTime(),active?"有效":"已退课");if(active)System.out.print(" | 成绩: "+(score==null?"暂无成绩":score));System.out.println();}if(!found)System.out.println("无选课记录");}}
    }
    static void myTimetable(Connection db,String student) throws SQLException {
        String sql="SELECT c.academic_year,c.term_no,c.class_no,k.course_name,w.weekday_no,w.start_period,w.end_period,r.room_name,e.enrollment_id FROM enrollment e JOIN teaching_class c ON c.class_no=e.class_no JOIN course k ON k.course_no=c.course_no JOIN class_schedule x ON x.class_no=c.class_no JOIN weekly_time w ON w.time_id=x.time_id JOIN classroom r ON r.room_id=x.room_id WHERE e.student_no=? AND e.withdrawn_at IS NULL ORDER BY c.academic_year,c.term_no,w.weekday_no,w.start_period";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,student);try(ResultSet r=p.executeQuery()){boolean found=false;while(r.next()){found=true;System.out.printf("选课记录号 %d | %s 第%d学期 | %s | %s | 周%d 第%d-%d节 | %s%n",r.getLong(9),r.getString(1),r.getInt(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6),r.getInt(7),r.getString(8));}if(!found)System.out.println("暂无课表");}}
    }
    static void teacherClasses(Connection db,String teacher) throws SQLException {
        String sql="SELECT c.class_no,k.course_name,c.capacity,c.active_count,k.course_no FROM teaching_assignment a JOIN teaching_class c ON c.class_no=a.class_no JOIN course k ON k.course_no=c.course_no WHERE a.teacher_no=? ORDER BY c.class_no";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,teacher);try(ResultSet r=p.executeQuery()){while(r.next())System.out.printf("%s | 课程号 %s | %s | 容量 %d | 已选 %d | 余量 %d%n",r.getString(1),r.getString(5),r.getString(2),r.getInt(3),r.getInt(4),r.getInt(3)-r.getInt(4));}}
    }
    static String personRole(){String r=input("S 学生 / T 教师: ").toUpperCase();if(!r.equals("S")&&!r.equals("T"))throw new IllegalArgumentException("角色无效");return r;}
    static void peopleMenu(Connection db,AuthService auth)throws Exception{
        AdminService a=new AdminService(db);String r=personRole(),op=input("1列表 2新增 3修改姓名 4删除: ");
        switch(op){case "1"->a.listPeople(r);case "2"->auth.createPerson(r,input("编号: "),input("姓名: "),input("账号: "),password("初始密码: "));case "3"->System.out.println("修改条数: "+a.renamePerson(r,input("编号: "),input("新姓名: ")));case "4"->{a.deletePerson(r,input("编号: "));System.out.println("删除成功");}default->System.out.println("无此选项");}
    }
    static void roomMenu(Connection db)throws Exception{
        AdminService a=new AdminService(db);switch(input("1列表 2新增 3修改 4删除: ")){case "1"->a.listRooms();case "2"->a.addRoom(input("教室名称: "));case "3"->a.renameRoom(Integer.parseInt(input("教室编号: ")),input("新名称: "));case "4"->a.deleteRoom(Integer.parseInt(input("教室编号: ")));default->System.out.println("无此选项");}
    }
    static void updateClass(Connection db)throws Exception{
        String no=input("教学班号: "),course=input("课程号: ");java.util.List<AdminService.Slot> slots=new java.util.ArrayList<>();String id;
        while(!(id=input("时间编号（空行结束）: ")).isEmpty())slots.add(new AdminService.Slot(Long.parseLong(id),Integer.parseInt(input("教室编号: "))));
        new AdminService(db).updateClass(no,course,slots);System.out.println("已修改");
    }
    static void createClass(Connection db)throws Exception{
        String course=input("课程号: "),year=input("学年: ");int term=Integer.parseInt(input("学期: ")),capacity=Integer.parseInt(input("容量: "));
        java.util.List<String> teachers=new java.util.ArrayList<>();String t;while(!(t=input("教师编号（空行结束）: ")).isEmpty())teachers.add(t);
        java.util.List<AdminService.Slot> slots=new java.util.ArrayList<>();String id;while(!(id=input("时间编号（空行结束）: ")).isEmpty())slots.add(new AdminService.Slot(Long.parseLong(id),Integer.parseInt(input("教室编号: "))));
        System.out.println("创建成功: "+new AdminService(db).addClass(course,year,term,capacity,teachers,slots));
    }
    public static void main(String[] args) {
        try(Connection db=DBUtil.connect()) {
            AuthService auth=new AuthService(db,DBUtil.settings().get("APP_SECRET"));
            if(args.length==1&&"--initialize-admin".equals(args[0])) {
                char[] pass=password("输入需求文档中固定的管理员初始密码: ");
                if(!Arrays.equals(pass,"Q7!mV2#rK9@pL4$xN8%t".toCharArray())) {Arrays.fill(pass,'\0');throw new IllegalArgumentException("管理员初始密码不正确");}
                auth.initializeFixture("admin",pass);System.out.println("管理员测试占位密码已初始化");return;
            }
            String account=input("账号: ");String role=auth.login(account,password("密码: "));
            String person=role.equals("S")?identity(db,"student","student_no",account):role.equals("T")?identity(db,"teacher","teacher_no",account):"";
            System.out.println("登录成功。");
            while(true){
                if(role.equals("A"))System.out.println(
                    "\n---- 管理员菜单 ----\n"+
                    " 1 查询课程    2 新增课程    3 修改课程\n"+
                    " 4 删除课程    5 重置密码    6 人员管理\n"+
                    " 7 教室管理    8 创建学期    9 创建教学班\n"+
                    "10 删除教学班 11 查询教学班 12 学生名单\n"+
                    "13 班级统计   14 修改教学班 15 课程学期统计\n"+
                    "16 查询时间   17 新增时间    0 退出");
                else if(role.equals("S"))System.out.println(
                    "\n---- 学生菜单 ----\n"+
                    "1 我的选课与成绩  2 选课      3 退课\n"+
                    "4 修改密码        5 查询教学班\n"+
                    "6 我的课表        0 退出");
                else System.out.println(
                    "\n---- 教师菜单 ----\n"+
                    "1 我的教学班    2 录入或修改成绩\n"+
                    "3 修改密码      4 查询教学班\n"+
                    "5 学生名单      6 班级统计    0 退出");
                String choice=input("选择: ");if(choice.equals("0"))break;
                try {
                    if(role.equals("A")) {CourseDAO dao=new CourseDAO(db);switch(choice){case "1"->dao.list();case "2"->{dao.add(input("课程号: "),input("课程名: "));System.out.println("添加成功");}case "3"->System.out.println("更新记录数: "+dao.update(input("课程号: "),input("新课程名: ")));case "4"->System.out.println("删除记录数: "+dao.delete(input("课程号: ")));case "5"->{auth.changePassword(input("学生或教师账号: "),password("初始或重置密码: "));System.out.println("密码已设置");}
                    case "6"->peopleMenu(db,auth);
                    case "7"->roomMenu(db);
                    case "8"->{new AdminService(db).addTerm(input("学年(例如2026-2027): "),Integer.parseInt(input("学期(1秋季/2春季): ")),java.time.LocalDate.parse(input("开学日(YYYY-MM-DD): ")));System.out.println("学期已创建");}
                    case "9"->createClass(db);
                    case "10"->{new AdminService(db).deleteClass(input("教学班号: "));System.out.println("教学班及专属记录已删除");}
                    case "11"->new QueryService(db).classes(role,person,input("教师姓名(直接回车显示全部): "),false);
                    case "12"->new QueryService(db).roster(role,person,input("教学班号: "));
                    case "13"->new QueryService(db).statistics(role,person,input("教学班号: "));case "14"->updateClass(db);case "15"->new QueryService(db).courseStatistics(input("课程号: "),input("学年: "),Integer.parseInt(input("学期: ")));case "16"->new AdminService(db).listTimes();case "17"->{new AdminService(db).addTime(Integer.parseInt(input("星期(1-7): ")),Integer.parseInt(input("开始节次: ")),Integer.parseInt(input("结束节次: ")));System.out.println("时间已添加");}
                    default->System.out.println("无此选项");}}
                    else if(role.equals("S")) {EnrollmentService svc=new EnrollmentService(db);switch(choice){case "1"->myRecords(db,person);case "2"->{svc.select(person,input("教学班号: "),Instant.now());System.out.println("选课成功");}case "3"->{svc.withdraw(person,input("教学班号: "),Instant.now());System.out.println("退课成功");}case "4"->{auth.changePassword(account,password("新密码: "));System.out.println("密码已修改");}case "5"->new QueryService(db).classes(role,person,input("教师姓名(回车显示全部): "),input("只显示可选余量(y/n): ").equalsIgnoreCase("y"));case "6"->myTimetable(db,person);default->System.out.println("无此选项");}}
                    else {switch(choice){case "1"->teacherClasses(db,person);case "2"->{new GradeService(db).record(person,Long.parseLong(input("选课记录编号: ")),Integer.parseInt(input("成绩: ")),Instant.now());System.out.println("成绩已保存");}case "3"->{auth.changePassword(account,password("新密码: "));System.out.println("密码已修改");}case "4"->new QueryService(db).classes(role,person,input("教师姓名(回车显示全部): "),false);case "5"->new QueryService(db).roster(role,person,input("教学班号: "));case "6"->new QueryService(db).statistics(role,person,input("教学班号: "));default->System.out.println("无此选项");}}
                } catch(Exception e){System.err.println("操作失败: "+e.getMessage());}
            }
        } catch(Exception e){System.err.println("启动或登录失败: "+e.getMessage());System.exit(1);}
    }
}
