package edu.coursework.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class DBUtil {
    private DBUtil() {}
    public static Map<String, String> settings() throws IOException {
        Map<String, String> settings = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(".env"))) {
            String item = line.trim();
            if (item.isEmpty() || item.startsWith("#")) continue;
            int separator = item.indexOf('=');
            if (separator > 0) settings.put(item.substring(0, separator).trim(), item.substring(separator + 1).trim());
        }
        for (String key : new String[]{"DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"}) {
            if (!settings.containsKey(key)) throw new IOException(".env 缺少配置项: " + key);
        }
        return settings;
    }
    public static Connection connect() throws IOException, SQLException {
        Map<String, String> settings = settings();
        String url = "jdbc:mysql://" + settings.get("DB_HOST") + ":" + settings.get("DB_PORT") + "/" + settings.get("DB_NAME") + "?characterEncoding=UTF-8";
        return DriverManager.getConnection(url, settings.get("DB_USER"), settings.get("DB_PASSWORD"));
    }
}
