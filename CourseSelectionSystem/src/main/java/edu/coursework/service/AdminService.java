package edu.coursework.service;

import edu.coursework.util.Rules;
import edu.coursework.dao.PersonDAO;
import edu.coursework.dao.StudentDAO;
import edu.coursework.dao.TeacherDAO;

import java.sql.*;
import java.time.*;
import java.util.*;

/** 管理员业务；调用前由界面完成角色校验。 */
public final class AdminService {
    private final Connection db;
    public AdminService(Connection db){this.db=db;}
    private int count(String sql,Object... values)throws SQLException{
        try(PreparedStatement p=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}
    }
    private int exec(String sql,Object...values)throws SQLException{
        try(PreparedStatement p=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p.executeUpdate();}
    }
    @FunctionalInterface interface Action {void run() throws SQLException;}
    private void transaction(Action action)throws SQLException{
        if(!db.getAutoCommit())throw new IllegalStateException("连接已有事务");
        db.setAutoCommit(false);
        try{action.run();db.commit();}catch(SQLException|RuntimeException e){try{db.rollback();}catch(SQLException x){e.addSuppressed(x);}throw e;}finally{db.setAutoCommit(true);}
    }
    public void addTerm(String year,int term,LocalDate start)throws SQLException{
        if(term!=1&&term!=2)throw new IllegalArgumentException("只需创建秋季或春季学期；小学期随春季创建");
        if(start.getDayOfWeek()!=DayOfWeek.SUNDAY)throw new IllegalArgumentException("开学日必须为周日");
        transaction(()->{
            insertTerm(year,term,start);
            if(term==2)insertTerm(year,3,start.plusDays(126));
            if(count("SELECT COUNT(*) FROM academic_term a JOIN academic_term b ON (a.academic_year,a.term_no)<(b.academic_year,b.term_no) AND a.start_date <= DATE_ADD(b.start_date,INTERVAL IF(b.term_no=3,27,125) DAY) AND b.start_date <= DATE_ADD(a.start_date,INTERVAL IF(a.term_no=3,27,125) DAY) WHERE a.academic_year=? OR b.academic_year=?",year,year)>0)throw new IllegalArgumentException("学期日期重叠");
        });
    }
    private void insertTerm(String y,int t,LocalDate date)throws SQLException{
        exec("INSERT INTO academic_term(academic_year,term_no,start_date) VALUES(?,?,?)",y,t,date);
        exec("INSERT INTO class_number_counter(academic_year,term_no,last_serial) VALUES(?,?,0)",y,t);
    }
    private PersonDAO people(String role){
        if(role.equals("S"))return new StudentDAO(db);
        if(role.equals("T"))return new TeacherDAO(db);
        throw new IllegalArgumentException("角色无效");
    }
    public void listPeople(String role)throws SQLException{people(role).list();}
    public int renamePerson(String role,String no,String name)throws SQLException{return people(role).rename(no,name);}
    public void deletePerson(String role,String no)throws SQLException{
        transaction(()->{
            PersonDAO dao=people(role);String account=dao.accountFor(no);
            dao.delete(no);exec("DELETE FROM account WHERE account_name=?",account);
        });
    }
    public int addTime(int day,int start,int end)throws SQLException{return exec("INSERT INTO weekly_time(weekday_no,start_period,end_period) VALUES(?,?,?)",day,start,end);}
    public void listTimes()throws SQLException{try(PreparedStatement p=db.prepareStatement("SELECT time_id,weekday_no,start_period,end_period FROM weekly_time ORDER BY time_id");ResultSet r=p.executeQuery()){while(r.next())System.out.printf("%d | 周%d | 第%d-%d节%n",r.getLong(1),r.getInt(2),r.getInt(3),r.getInt(4));}}
    public void listRooms()throws SQLException{try(PreparedStatement p=db.prepareStatement("SELECT room_id,room_name FROM classroom ORDER BY room_id");ResultSet r=p.executeQuery()){while(r.next())System.out.println(r.getInt(1)+" | "+r.getString(2));}}
    public int addRoom(String name)throws SQLException{return exec("INSERT INTO classroom(room_name) VALUES(?)",name);}
    public int renameRoom(int id,String name)throws SQLException{return exec("UPDATE classroom SET room_name=? WHERE room_id=?",name,id);}
    public int deleteRoom(int id)throws SQLException{return exec("DELETE FROM classroom WHERE room_id=?",id);}
    public record Slot(long timeId,int roomId){}
    /** 创建班级，按学期计数器产生永不重用的流水号；最少一名教师和一条排课。 */
    public String addClass(String course,String year,int term,int capacity,List<String> teachers,List<Slot> slots)throws SQLException{
        if(capacity<1||teachers.isEmpty()||slots.isEmpty())throw new IllegalArgumentException("容量须大于零且至少指定一位教师和一条排课");
        if(new HashSet<>(teachers).size()!=teachers.size())throw new IllegalArgumentException("教师重复");
        final String[] result={null};
        transaction(()->{
            if(count("SELECT COUNT(*) FROM class_number_counter WHERE academic_year=? AND term_no=?",year,term)!=1)throw new IllegalArgumentException("学期不存在");
            exec("UPDATE class_number_counter SET last_serial=last_serial+1 WHERE academic_year=? AND term_no=?",year,term);
            long serial;
            try(PreparedStatement p=db.prepareStatement("SELECT last_serial FROM class_number_counter WHERE academic_year=? AND term_no=?")){p.setString(1,year);p.setInt(2,term);try(ResultSet r=p.executeQuery()){r.next();serial=r.getLong(1);}}
            String no=year+"-"+term+"-"+String.format("%03d",serial);result[0]=no;
            exec("INSERT INTO teaching_class(class_no,course_no,academic_year,term_no,capacity,active_count) VALUES(?,?,?,?,?,0)",no,course,year,term,capacity);
            for(String teacher:teachers)exec("INSERT INTO teaching_assignment(class_no,teacher_no) VALUES(?,?)",no,teacher);
            for(Slot slot:slots)exec("INSERT INTO class_schedule(class_no,time_id,room_id) VALUES(?,?,?)",no,slot.timeId,slot.roomId);
            validateClass(no);
        });return result[0];
    }
    private void validateClass(String no)throws SQLException{
        if(count("SELECT COUNT(*) FROM class_schedule s JOIN weekly_time a ON a.time_id=s.time_id JOIN class_schedule t ON t.class_no=s.class_no AND t.time_id>s.time_id JOIN weekly_time b ON b.time_id=t.time_id WHERE s.class_no=? AND a.weekday_no=b.weekday_no AND a.start_period<=b.end_period AND b.start_period<=a.end_period",no)>0)throw new IllegalArgumentException("本班排课时间重叠");
        if(count("SELECT COUNT(*) FROM teaching_class a JOIN class_schedule x ON x.class_no=a.class_no JOIN weekly_time wx ON wx.time_id=x.time_id JOIN teaching_class b ON b.academic_year=a.academic_year AND b.term_no=a.term_no AND b.class_no<>a.class_no JOIN class_schedule y ON y.class_no=b.class_no JOIN weekly_time wy ON wy.time_id=y.time_id WHERE a.class_no=? AND x.room_id=y.room_id AND wx.weekday_no=wy.weekday_no AND wx.start_period<=wy.end_period AND wy.start_period<=wx.end_period",no)>0)throw new IllegalArgumentException("教室时间冲突");
        if(count("SELECT COUNT(*) FROM teaching_class a JOIN teaching_assignment aa ON aa.class_no=a.class_no JOIN class_schedule x ON x.class_no=a.class_no JOIN weekly_time wx ON wx.time_id=x.time_id JOIN teaching_class b ON b.academic_year=a.academic_year AND b.term_no=a.term_no AND b.class_no<>a.class_no JOIN teaching_assignment ba ON ba.class_no=b.class_no AND ba.teacher_no=aa.teacher_no JOIN class_schedule y ON y.class_no=b.class_no JOIN weekly_time wy ON wy.time_id=y.time_id WHERE a.class_no=? AND wx.weekday_no=wy.weekday_no AND wx.start_period<=wy.end_period AND wy.start_period<=wx.end_period",no)>0)throw new IllegalArgumentException("教师时间冲突");
        if(count("SELECT COUNT(*) FROM teaching_class a JOIN teaching_class b ON b.course_no=a.course_no AND b.academic_year=a.academic_year AND b.term_no=a.term_no AND b.class_no<>a.class_no WHERE a.class_no=? AND (SELECT COALESCE(SUM(w.end_period-w.start_period+1),0) FROM class_schedule s JOIN weekly_time w ON w.time_id=s.time_id WHERE s.class_no=a.class_no)<>(SELECT COALESCE(SUM(w.end_period-w.start_period+1),0) FROM class_schedule s JOIN weekly_time w ON w.time_id=s.time_id WHERE s.class_no=b.class_no)",no)>0)throw new IllegalArgumentException("同课程教学班周课时数不一致");
    }
    public void updateClass(String no,String course,List<Slot> slots)throws SQLException{
        if(slots.isEmpty())throw new IllegalArgumentException("教学班至少需要一条排课");
        transaction(()->{
            if(count("SELECT COUNT(*) FROM teaching_class WHERE class_no=?",no)!=1)throw new IllegalArgumentException("教学班不存在");
            if(count("SELECT COUNT(*) FROM enrollment WHERE class_no=?",no)>0)throw new IllegalArgumentException("已有选课历史，不允许修改课程和排课");
            exec("UPDATE teaching_class SET course_no=? WHERE class_no=?",course,no);
            exec("DELETE FROM class_schedule WHERE class_no=?",no);
            for(Slot slot:slots)exec("INSERT INTO class_schedule(class_no,time_id,room_id) VALUES(?,?,?)",no,slot.timeId,slot.roomId);
            validateClass(no);
        });
    }
    public void deleteClass(String no)throws SQLException{
        transaction(()->{
            if(count("SELECT COUNT(*) FROM teaching_class WHERE class_no=?",no)!=1)throw new IllegalArgumentException("教学班不存在");
            exec("DELETE gr FROM grade_revision gr JOIN enrollment e ON gr.enrollment_id=e.enrollment_id WHERE e.class_no=?",no);
            exec("DELETE g FROM grade g JOIN enrollment e ON g.enrollment_id=e.enrollment_id WHERE e.class_no=?",no);
            exec("DELETE FROM enrollment WHERE class_no=?",no);
            exec("DELETE FROM teaching_assignment WHERE class_no=?",no);
            exec("DELETE FROM class_schedule WHERE class_no=?",no);
            exec("DELETE FROM teaching_class WHERE class_no=?",no);
        });
    }
}
