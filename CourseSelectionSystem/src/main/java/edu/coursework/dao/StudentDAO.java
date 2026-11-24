package edu.coursework.dao;

import java.sql.Connection;

public final class StudentDAO extends PersonDAO {
    public StudentDAO(Connection db) {super(db,"student","student_no","student_name");}
}
