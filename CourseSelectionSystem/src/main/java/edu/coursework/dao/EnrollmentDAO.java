package edu.coursework.dao;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 选课相关 SQL；事务由 EnrollmentService 管理。 */
public final class EnrollmentDAO {
    private final Connection db;
    public EnrollmentDAO(Connection db){this.db=db;}
    public record ClassInfo(String course,String year,int term,LocalDate start,int capacity,int active) {}
    public int count(String sql,Object...args)throws SQLException{
        try(PreparedStatement p=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}
    }
    public ClassInfo findClassForUpdate(String no)throws SQLException{
        String sql="SELECT c.course_no,c.academic_year,c.term_no,t.start_date,c.capacity,c.active_count FROM teaching_class c JOIN academic_term t ON t.academic_year=c.academic_year AND t.term_no=c.term_no WHERE c.class_no=? FOR UPDATE";
        try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,no);try(ResultSet r=p.executeQuery()){
            if(!r.next())throw new IllegalArgumentException("教学班不存在");
            return new ClassInfo(r.getString(1),r.getString(2),r.getInt(3),r.getDate(4).toLocalDate(),r.getInt(5),r.getInt(6));
        }}
    }
    public void insert(String student,String no,LocalDateTime selected)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("INSERT INTO enrollment(student_no,class_no,selected_at) VALUES(?,?,?)")){p.setString(1,student);p.setString(2,no);p.setObject(3,selected);p.executeUpdate();}
    }
    public void incrementCount(String no)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("UPDATE teaching_class SET active_count=active_count+1 WHERE class_no=? AND active_count<capacity")){p.setString(1,no);if(p.executeUpdate()!=1)throw new IllegalStateException("人数更新失败");}
    }
    public long findActiveForUpdate(String student,String no)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("SELECT enrollment_id FROM enrollment WHERE student_no=? AND class_no=? AND withdrawn_at IS NULL FOR UPDATE")){
            p.setString(1,student);p.setString(2,no);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("没有有效选课记录");long id=r.getLong(1);
                if(r.next())throw new IllegalStateException("存在重复有效选课记录");return id;
            }
        }
    }
    public void markWithdrawn(long id,LocalDateTime time)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("UPDATE enrollment SET withdrawn_at=? WHERE enrollment_id=? AND withdrawn_at IS NULL")){p.setObject(1,time);p.setLong(2,id);if(p.executeUpdate()!=1)throw new IllegalStateException("退课记录更新失败");}
    }
    public void decrementCount(String no)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("UPDATE teaching_class SET active_count=active_count-1 WHERE class_no=? AND active_count>0")){p.setString(1,no);if(p.executeUpdate()!=1)throw new IllegalStateException("人数更新失败");}
    }
}
