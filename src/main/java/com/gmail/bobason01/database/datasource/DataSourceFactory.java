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
            File dbFile = new File(
                    MailManager.getInstance().getDataFolder(),
                    c.getString("database.sqlite.file", "mail_data.db")
            );
            dbFile.getParentFile().mkdirs();

            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hc.setMaximumPoolSize(c.getInt("database.sqlite.pool.maximumPoolSize", 10));
            hc.setMinimumIdle(c.getInt("database.sqlite.pool.minimumIdle", 2));
            hc.setConnectionTimeout(c.getLong("database.sqlite.pool.connectionTimeoutMs", 10000));
            hc.setIdleTimeout(c.getLong("database.sqlite.pool.idleTimeoutMs", 60000));
            hc.setMaxLifetime(c.getLong("database.sqlite.pool.maxLifetimeMs", 1800000));
            return new HikariDataSource(hc);
        }

        if (type == DatabaseType.MYSQL) {
            String host = c.getString("database.mysql.host", "localhost");
            int port = c.getInt("database.mysql.port", 3306);
            String name = c.getString("database.mysql.name", "mailmanager");
            String user = c.getString("database.mysql.user", "root");
            String pass = c.getString("database.mysql.pass", "");

            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            hc.setUsername(user);
            hc.setPassword(pass);

            hc.setMaximumPoolSize(c.getInt("database.mysql.pool.maximumPoolSize", 10));
            hc.setMinimumIdle(c.getInt("database.mysql.pool.minimumIdle", 2));
            hc.setConnectionTimeout(c.getLong("database.mysql.pool.connectionTimeoutMs", 10000));
            hc.setIdleTimeout(c.getLong("database.mysql.pool.idleTimeoutMs", 60000));
            hc.setMaxLifetime(c.getLong("database.mysql.pool.maxLifetimeMs", 1800000));

            return new HikariDataSource(hc);
        }

        return null;
    }
}
