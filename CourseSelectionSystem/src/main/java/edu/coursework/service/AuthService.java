package edu.coursework.service;

import edu.coursework.dao.PersonDAO;
import edu.coursework.dao.StudentDAO;
import edu.coursework.dao.TeacherDAO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

public final class AuthService {
    private final Connection db; private final byte[] key;
    public AuthService(Connection db, String key) { if(key==null||key.length()<32) throw new IllegalArgumentException("请在 .env 设置至少 32 字符的 APP_SECRET"); this.db=db;this.key=key.getBytes(StandardCharsets.UTF_8); }
    private byte[] hash(String name, char[] password) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
        byte[] salt=Arrays.copyOf(mac.doFinal(name.getBytes(StandardCharsets.UTF_8)),16);
        PBEKeySpec spec=new PBEKeySpec(password,salt,600000,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();} finally {spec.clearPassword();}
    }
    public String login(String name,char[] password) throws Exception {
        try(PreparedStatement p=db.prepareStatement("SELECT role_code,password_hash FROM account WHERE account_name=?")) {
            p.setString(1,name); try(ResultSet r=p.executeQuery()){ if(!r.next()||!MessageDigest.isEqual(hash(name,password),r.getBytes(2))) throw new IllegalArgumentException("账号或密码错误"); return r.getString(1); }
        } finally { Arrays.fill(password,'\0'); }
    }
    public void createPerson(String role,String number,String name,String account,char[] password) throws Exception {
        if(!role.equals("S")&&!role.equals("T")) throw new IllegalArgumentException("角色无效");
        if(!db.getAutoCommit()) throw new IllegalStateException("连接已有事务");
        db.setAutoCommit(false);
        try {
            try(PreparedStatement p=db.prepareStatement("INSERT INTO account(account_name,role_code,password_hash) VALUES(?,?,?)")){p.setString(1,account);p.setString(2,role);p.setBytes(3,hash(account,password));p.executeUpdate();}
            PersonDAO dao=role.equals("S")?new StudentDAO(db):new TeacherDAO(db);
            dao.insert(number,name,account);
            db.commit();
        } catch(Exception e) {try {db.rollback();} catch(SQLException x){e.addSuppressed(x);}throw e;} finally {Arrays.fill(password,'\0');db.setAutoCommit(true);}
    }
    public void initializeFixture(String name, char[] password) throws Exception {
        byte[] fixture=MessageDigest.getInstance("SHA-256").digest(("task4-sql-fixture:"+name).getBytes(StandardCharsets.UTF_8));
        try(PreparedStatement p=db.prepareStatement("UPDATE account SET password_hash=? WHERE account_name=? AND password_hash=?")) {
            p.setBytes(1,hash(name,password));p.setString(2,name);p.setBytes(3,fixture);
            if(p.executeUpdate()!=1) throw new IllegalArgumentException("账号不存在或密码已初始化；未修改");
        } finally {Arrays.fill(password,'\0');}
    }
    public void changePassword(String name,char[] password) throws Exception {
        if("admin".equals(name)) throw new IllegalArgumentException("管理员密码不可更改");
        try(PreparedStatement p=db.prepareStatement("UPDATE account SET password_hash=? WHERE account_name=?")) {p.setBytes(1,hash(name,password));p.setString(2,name);if(p.executeUpdate()!=1) throw new IllegalArgumentException("账号不存在");} finally {Arrays.fill(password,'\0');}
    }
}
