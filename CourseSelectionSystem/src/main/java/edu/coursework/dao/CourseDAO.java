package edu.coursework.dao;
import java.sql.*;

/** 课程表操作，调用者必须在角色鉴权之后执行。 */
public final class CourseDAO {
    private final Connection db;
    public CourseDAO(Connection db){this.db=db;}
    public void add(String number,String name) throws SQLException { try(PreparedStatement p=db.prepareStatement("INSERT INTO course(course_no,course_name) VALUES(?,?)")){p.setString(1,number);p.setString(2,name);p.executeUpdate();} }
    public int update(String number,String name) throws SQLException { try(PreparedStatement p=db.prepareStatement("UPDATE course SET course_name=? WHERE course_no=?")){p.setString(1,name);p.setString(2,number);return p.executeUpdate();} }
    public int delete(String number) throws SQLException { try(PreparedStatement p=db.prepareStatement("DELETE FROM course WHERE course_no=?")){p.setString(1,number);return p.executeUpdate();} }
    public void list() throws SQLException { try(PreparedStatement p=db.prepareStatement("SELECT course_no,course_name FROM course ORDER BY course_no");ResultSet r=p.executeQuery()){while(r.next()) System.out.println(r.getString(1)+" | "+r.getString(2));} }
}
