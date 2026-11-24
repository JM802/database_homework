package edu.coursework.dao;

import java.sql.*;

/** 学生或教师基本资料的数据访问；表名由子类固定。 */
public abstract class PersonDAO {
    private final Connection db;
    private final String table, numberColumn, nameColumn;
    protected PersonDAO(Connection db, String table, String numberColumn, String nameColumn) {
        this.db=db;this.table=table;this.numberColumn=numberColumn;this.nameColumn=nameColumn;
    }
    public void insert(String number,String name,String account)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("INSERT INTO "+table+"("+numberColumn+","+nameColumn+",account_name) VALUES(?,?,?)")){
            p.setString(1,number);p.setString(2,name);p.setString(3,account);p.executeUpdate();
        }
    }
    public void list()throws SQLException{
        try(PreparedStatement p=db.prepareStatement("SELECT "+numberColumn+","+nameColumn+",account_name FROM "+table+" ORDER BY "+numberColumn);ResultSet r=p.executeQuery()){
            while(r.next())System.out.printf("%s | %s | %s%n",r.getString(1),r.getString(2),r.getString(3));
        }
    }
    public int rename(String number,String name)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("UPDATE "+table+" SET "+nameColumn+"=? WHERE "+numberColumn+"=?")){
            p.setString(1,name);p.setString(2,number);return p.executeUpdate();
        }
    }
    public String accountFor(String number)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("SELECT account_name FROM "+table+" WHERE "+numberColumn+"=?")){
            p.setString(1,number);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("人员不存在");return r.getString(1);}
        }
    }
    public int delete(String number)throws SQLException{
        try(PreparedStatement p=db.prepareStatement("DELETE FROM "+table+" WHERE "+numberColumn+"=?")){p.setString(1,number);return p.executeUpdate();}
    }
}
