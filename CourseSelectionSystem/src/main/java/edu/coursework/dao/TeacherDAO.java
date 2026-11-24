package edu.coursework.dao;

import java.sql.Connection;

public final class TeacherDAO extends PersonDAO {
    public TeacherDAO(Connection db) {super(db,"teacher","teacher_no","teacher_name");}
}
