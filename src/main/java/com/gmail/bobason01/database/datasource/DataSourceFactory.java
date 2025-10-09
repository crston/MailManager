package com.gmail.bobason01.database.datasource;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.database.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import javax.sql.DataSource;
import java.io.File;

public class DataSourceFactory {
    public static DataSource build(DatabaseType type) {
        FileConfiguration c = MailManager.getInstance().getConfig();
        if (type == DatabaseType.SQLITE) {
            File dbFile = new File(MailManager.getInstance().getDataFolder(), "mail_data.db");
            dbFile.getParentFile().mkdirs();
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hc.setMaximumPoolSize(c.getInt("database.pool.maximumPoolSize", 10));
            hc.setMinimumIdle(c.getInt("database.pool.minimumIdle", 2));
            hc.setConnectionTimeout(c.getLong("database.pool.connectionTimeoutMs", 10000));
            hc.setIdleTimeout(c.getLong("database.pool.idleTimeoutMs", 60000));
            hc.setMaxLifetime(c.getLong("database.pool.maxLifetimeMs", 1800000));
            return new HikariDataSource(hc);
        } else if (type == DatabaseType.MYSQL) {
            String host = c.getString("database.host", "localhost");
            int port = c.getInt("database.port", 3306);
            String name = c.getString("database.name", "maildb");
            String user = c.getString("database.user", "root");
            String pass = c.getString("database.pass", "password");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl(url);
            hc.setUsername(user);
            hc.setPassword(pass);
            hc.setMaximumPoolSize(c.getInt("database.pool.maximumPoolSize", 10));
            hc.setMinimumIdle(c.getInt("database.pool.minimumIdle", 2));
            hc.setConnectionTimeout(c.getLong("database.pool.connectionTimeoutMs", 10000));
            hc.setIdleTimeout(c.getLong("database.pool.idleTimeoutMs", 60000));
            hc.setMaxLifetime(c.getLong("database.pool.maxLifetimeMs", 1800000));
            return new HikariDataSource(hc);
        } else {
            return null;
        }
    }
}
