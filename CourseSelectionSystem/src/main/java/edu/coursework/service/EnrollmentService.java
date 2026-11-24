package edu.coursework.service;

import edu.coursework.util.Rules;
import edu.coursework.dao.EnrollmentDAO;
import edu.coursework.dao.EnrollmentDAO.ClassInfo;

import java.sql.*;
import java.time.*;

/** 单用户顺序操作；方法均使用同一连接提交或回滚。 */
public final class EnrollmentService {
    private final Connection db;
    private final EnrollmentDAO dao;
    public EnrollmentService(Connection db) { this.db=db;this.dao=new EnrollmentDAO(db); }
    private int count(String sql,Object...args)throws SQLException{return dao.count(sql,args);}
    private ClassInfo info(String no)throws SQLException{return dao.findClassForUpdate(no);}
    private void transaction(SqlAction action) throws SQLException {
        if (!db.getAutoCommit()) throw new IllegalStateException("连接已处于事务中");
        db.setAutoCommit(false);
        try { action.run(); db.commit(); }
        catch (SQLException | RuntimeException e) { try { db.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); } throw e; }
        finally { db.setAutoCommit(true); }
    }
    @FunctionalInterface private interface SqlAction { void run() throws SQLException; }
    public void select(String student, String classNo, Instant now) throws SQLException {
        transaction(() -> {
            ClassInfo c=info(classNo);
            if(count("SELECT COUNT(*) FROM student WHERE student_no=?",student)==0) throw new IllegalArgumentException("学生不存在");
            if(!Rules.regularWindow(c.start(),c.term(),now)) throw new IllegalArgumentException("不在选课时间内");
            if(c.active()>=c.capacity()) throw new IllegalArgumentException("教学班已满");
            if(count("SELECT COUNT(*) FROM enrollment e JOIN teaching_class c ON e.class_no=c.class_no WHERE e.student_no=? AND e.withdrawn_at IS NULL AND c.academic_year=? AND c.term_no=? AND c.course_no=?",student,c.year(),c.term(),c.course())>0) throw new IllegalArgumentException("本学期已选该课程");
            String clash="SELECT COUNT(*) FROM enrollment e JOIN teaching_class old ON old.class_no=e.class_no JOIN class_schedule os ON os.class_no=old.class_no JOIN weekly_time ot ON ot.time_id=os.time_id JOIN class_schedule ns ON ns.class_no=? JOIN weekly_time nt ON nt.time_id=ns.time_id WHERE e.student_no=? AND e.withdrawn_at IS NULL AND old.academic_year=? AND old.term_no=? AND ot.weekday_no=nt.weekday_no AND ot.start_period<=nt.end_period AND nt.start_period<=ot.end_period";
            if(count(clash,classNo,student,c.year(),c.term())>0) throw new IllegalArgumentException("课程时间冲突");
            dao.insert(student,classNo,LocalDateTime.ofInstant(now,ZoneOffset.UTC));
            dao.incrementCount(classNo);
        });
    }
    public void withdraw(String student, String classNo, Instant now) throws SQLException {
        transaction(() -> {
            ClassInfo c=info(classNo);
            boolean regular=Rules.regularWindow(c.start(),c.term(),now), mid=Rules.midtermWindow(c.start(),c.term(),now);
            if(!regular&&!mid) throw new IllegalArgumentException("不在退课时间内");
            long id=dao.findActiveForUpdate(student,classNo);
            if(count("SELECT COUNT(*) FROM grade WHERE enrollment_id=?",id)>0) throw new IllegalArgumentException("已有成绩，不能退课");
            if(mid) {
                LocalDateTime from=c.start().plusWeeks(9).atStartOfDay(Rules.BEIJING).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                if(count("SELECT COUNT(*) FROM enrollment e JOIN teaching_class c ON c.class_no=e.class_no WHERE e.student_no=? AND c.academic_year=? AND c.term_no=? AND e.withdrawn_at>=? AND e.withdrawn_at<?",student,c.year(),c.term(),from,from.plusDays(4))>0) throw new IllegalArgumentException("本学期期中退课名额已使用");
            }
            dao.markWithdrawn(id,LocalDateTime.ofInstant(now,ZoneOffset.UTC));
            dao.decrementCount(classNo);
        });
    }
}
