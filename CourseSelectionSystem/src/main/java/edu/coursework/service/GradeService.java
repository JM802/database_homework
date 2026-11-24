package edu.coursework.service;

import edu.coursework.util.Rules;

import java.sql.*;
import java.time.*;

public final class GradeService {
    private final Connection db;
    public GradeService(Connection db) { this.db=db; }
    /** 只允许当前任课教师操作有效选课，修改和历史记录属于同一事务。 */
    public void record(String teacher, long enrollment, int score, Instant now) throws SQLException {
        Rules.gpa(score);
        if(!db.getAutoCommit()) throw new IllegalStateException("连接已处于事务中");
        db.setAutoCommit(false);
        try {
            try(PreparedStatement p=db.prepareStatement("SELECT COUNT(*) FROM enrollment e JOIN teaching_assignment a ON a.class_no=e.class_no WHERE e.enrollment_id=? AND e.withdrawn_at IS NULL AND a.teacher_no=?")) {
                p.setLong(1,enrollment);p.setString(2,teacher);
                try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)!=1) throw new IllegalArgumentException("无权限或不是有效选课");}
            }
            Integer old=null; LocalDateTime first=null;
            try(PreparedStatement p=db.prepareStatement("SELECT score,first_recorded_at FROM grade WHERE enrollment_id=? FOR UPDATE")) {
                p.setLong(1,enrollment);
                try(ResultSet r=p.executeQuery()){if(r.next()){old=r.getInt(1);first=r.getObject(2,LocalDateTime.class);}}
            }
            LocalDateTime current=LocalDateTime.ofInstant(now,ZoneOffset.UTC);
            if(old==null) {
                try(PreparedStatement p=db.prepareStatement("INSERT INTO grade(enrollment_id,score,first_recorded_at) VALUES(?,?,?)")) {
                    p.setLong(1,enrollment);p.setInt(2,score);p.setObject(3,current);p.executeUpdate();
                }
            } else {
                if(!Rules.canRevise(first.toInstant(ZoneOffset.UTC),now)) throw new IllegalArgumentException("已超过成绩修改期限");
                try(PreparedStatement p=db.prepareStatement("UPDATE grade SET score=? WHERE enrollment_id=?")) {p.setInt(1,score);p.setLong(2,enrollment);p.executeUpdate();}
                try(PreparedStatement p=db.prepareStatement("INSERT INTO grade_revision(enrollment_id,editor_no,old_score,new_score,changed_at) VALUES(?,?,?,?,?)")) {
                    p.setLong(1,enrollment);p.setString(2,teacher);p.setInt(3,old);p.setInt(4,score);p.setObject(5,current);p.executeUpdate();
                }
            }
            db.commit();
        } catch(SQLException | RuntimeException e) {
            try { db.rollback(); } catch(SQLException rollback) {e.addSuppressed(rollback);} throw e;
        } finally {db.setAutoCommit(true);}
    }
}
