package edu.coursework.service;

import edu.coursework.util.Rules;
import java.sql.*;
import java.time.*;

public final class QueryService {
    private final Connection db;
    public QueryService(Connection db){this.db=db;}
    public void classes(String role,String person,String teacherName,boolean onlyAvailable)throws SQLException{
        classes(role,person,teacherName,onlyAvailable,Instant.now());
    }
    public void classes(String role,String person,String teacherName,boolean onlyAvailable,Instant now)throws SQLException{
        String sql="SELECT c.class_no,co.course_name,c.academic_year,c.term_no,t.start_date,c.capacity,c.active_count, GROUP_CONCAT(DISTINCT tc.teacher_name ORDER BY tc.teacher_name SEPARATOR '、') teachers FROM teaching_class c JOIN course co ON co.course_no=c.course_no JOIN academic_term t ON t.academic_year=c.academic_year AND t.term_no=c.term_no LEFT JOIN teaching_assignment a ON a.class_no=c.class_no LEFT JOIN teacher tc ON tc.teacher_no=a.teacher_no WHERE (?='' OR EXISTS(SELECT 1 FROM teaching_assignment aa JOIN teacher tt ON tt.teacher_no=aa.teacher_no WHERE aa.class_no=c.class_no AND tt.teacher_name=?)) AND (?<>'T' OR EXISTS(SELECT 1 FROM teaching_assignment ax WHERE ax.class_no=c.class_no AND ax.teacher_no=?)) GROUP BY c.class_no,co.course_name,c.academic_year,c.term_no,t.start_date,c.capacity,c.active_count ORDER BY c.class_no";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,teacherName);p.setString(2,teacherName);p.setString(3,role);p.setString(4,person);try(ResultSet r=p.executeQuery()){
            while(r.next()){
                boolean visible=!role.equals("S")||Rules.regularWindow(r.getDate(5).toLocalDate(),r.getInt(4),now);
                if(onlyAvailable&&(!visible||r.getInt(6)<=r.getInt(7)))continue;
                String no=r.getString(1);
                System.out.printf("%s | %s | %s 第%d学期 | 教师: %s",no,r.getString(2),r.getString(3),r.getInt(4),r.getString(8));
                if(visible)System.out.printf(" | 容量 %d 已选 %d 余量 %d",r.getInt(6),r.getInt(7),r.getInt(6)-r.getInt(7));
                System.out.println();
                if(role.equals("S") && hasOnlyWithdrawnEnrollment(person,no)) {
                    System.out.println("  已退课：不显示该班排课和教室");
                    continue;
                }
                try(PreparedStatement x=db.prepareStatement("SELECT w.weekday_no,w.start_period,w.end_period,r.room_name FROM class_schedule s JOIN weekly_time w ON w.time_id=s.time_id JOIN classroom r ON r.room_id=s.room_id WHERE s.class_no=? ORDER BY w.weekday_no,w.start_period")){x.setString(1,no);try(ResultSet z=x.executeQuery()){while(z.next())System.out.printf("  周%d 第%d-%d节 %s%n",z.getInt(1),z.getInt(2),z.getInt(3),z.getString(4));}}
            }
        }}
    }
    private boolean hasOnlyWithdrawnEnrollment(String student,String classNo)throws SQLException{
        String sql="SELECT EXISTS(SELECT 1 FROM enrollment WHERE student_no=? AND class_no=? AND withdrawn_at IS NOT NULL) AND NOT EXISTS(SELECT 1 FROM enrollment WHERE student_no=? AND class_no=? AND withdrawn_at IS NULL)";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,student);p.setString(2,classNo);p.setString(3,student);p.setString(4,classNo);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}
    }
    private void verifyTeacher(String teacher,String no)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("SELECT COUNT(*) FROM teaching_assignment WHERE teacher_no=? AND class_no=?")){p.setString(1,teacher);p.setString(2,no);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)==0)throw new IllegalArgumentException("无权查看该教学班");}}
    }
    public void roster(String role,String teacher,String no)throws SQLException{
        if(role.equals("T"))verifyTeacher(teacher,no);else if(!role.equals("A"))throw new IllegalArgumentException("无权限");
        try(PreparedStatement p=db.prepareStatement("SELECT e.enrollment_id,s.student_no,s.student_name,g.score FROM enrollment e JOIN student s ON s.student_no=e.student_no LEFT JOIN grade g ON g.enrollment_id=e.enrollment_id WHERE e.class_no=? AND e.withdrawn_at IS NULL ORDER BY s.student_no")){p.setString(1,no);try(ResultSet r=p.executeQuery()){while(r.next()){Object score=r.getObject(4);System.out.printf("选课记录 %d | %s | %s | %s%n",r.getLong(1),r.getString(2),r.getString(3),score==null?"暂无成绩":score);}}}
    }
    public void statistics(String role,String teacher,String no)throws SQLException{
        if(role.equals("T"))verifyTeacher(teacher,no);else if(!role.equals("A"))throw new IllegalArgumentException("无权限");
        String sql="SELECT COUNT(*),COALESCE(SUM(e.withdrawn_at IS NOT NULL),0),COUNT(DISTINCT CASE WHEN e.withdrawn_at IS NULL THEN e.student_no END),AVG(CASE WHEN e.withdrawn_at IS NULL THEN g.score END),AVG(CASE WHEN e.withdrawn_at IS NULL AND g.score IS NOT NULL THEN CASE WHEN g.score<60 THEN 0 WHEN g.score<70 THEN 1 WHEN g.score<80 THEN 2 WHEN g.score<90 THEN 3 ELSE 4 END END),COALESCE(SUM(e.withdrawn_at IS NULL AND g.score>=60),0),COALESCE(SUM(e.withdrawn_at IS NULL AND g.score>=90),0) FROM enrollment e LEFT JOIN grade g ON g.enrollment_id=e.enrollment_id WHERE e.class_no=?";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,no);try(ResultSet r=p.executeQuery()){r.next();System.out.printf("选课记录 %d | 退课记录 %d | 有效人数 %d | 平均分 %s | 平均绩点 %s | 及格 %d | 满绩 %d%n",r.getInt(1),r.getInt(2),r.getInt(3),r.getObject(4)==null?"暂无成绩":r.getObject(4),r.getObject(5)==null?"暂无成绩":r.getObject(5),r.getInt(6),r.getInt(7));}}
        try(PreparedStatement p=db.prepareStatement("SELECT COUNT(*) FROM teaching_assignment WHERE class_no=?")){p.setString(1,no);try(ResultSet r=p.executeQuery()){r.next();System.out.println("任课安排数: "+r.getInt(1));}}
    }
    public void courseStatistics(String course,String year,int term)throws SQLException{
        String sql="WITH scoped AS (SELECT class_no FROM teaching_class WHERE course_no=? AND academic_year=? AND term_no=?), es AS (SELECT e.* FROM enrollment e JOIN scoped c ON c.class_no=e.class_no) SELECT (SELECT COUNT(*) FROM scoped),(SELECT COUNT(*) FROM teaching_assignment a JOIN scoped c ON c.class_no=a.class_no),COUNT(e.enrollment_id),COALESCE(SUM(e.withdrawn_at IS NOT NULL),0),COUNT(DISTINCT CASE WHEN e.withdrawn_at IS NULL THEN e.student_no END),AVG(CASE WHEN e.withdrawn_at IS NULL THEN g.score END),AVG(CASE WHEN e.withdrawn_at IS NULL AND g.score IS NOT NULL THEN CASE WHEN g.score<60 THEN 0 WHEN g.score<70 THEN 1 WHEN g.score<80 THEN 2 WHEN g.score<90 THEN 3 ELSE 4 END END),COALESCE(SUM(e.withdrawn_at IS NULL AND g.score>=60),0),COALESCE(SUM(e.withdrawn_at IS NULL AND g.score>=90),0) FROM es e LEFT JOIN grade g ON g.enrollment_id=e.enrollment_id";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,course);p.setString(2,year);p.setInt(3,term);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)==0){System.out.println("该课程学期没有教学班");return;}System.out.printf("教学班 %d | 任课安排 %d | 选课记录 %d | 退课记录 %d | 有效学生 %d | 平均分 %s | 平均绩点 %s | 及格 %d | 满绩 %d%n",r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getInt(5),r.getObject(6)==null?"暂无成绩":r.getObject(6),r.getObject(7)==null?"暂无成绩":r.getObject(7),r.getInt(8),r.getInt(9));}}
    }
}
