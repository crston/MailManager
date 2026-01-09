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

            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            HikariConfig hc = new HikariConfig();
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hc.setPoolName("MailManager-SQLite");

            // SQLite는 파일 락 문제 방지를 위해 연결 풀 크기를 1로 제한하는 것을 권장함
            hc.setMaximumPoolSize(1);
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
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            hc.setUsername(user);
            hc.setPassword(pass);
            hc.setPoolName("MailManager-MySQL");

            hc.setMaximumPoolSize(c.getInt("database.mysql.pool.maximumPoolSize", 10));
            hc.setMinimumIdle(c.getInt("database.mysql.pool.minimumIdle", 2));
            hc.setConnectionTimeout(c.getLong("database.mysql.pool.connectionTimeoutMs", 10000));
            hc.setIdleTimeout(c.getLong("database.mysql.pool.idleTimeoutMs", 60000));
            hc.setMaxLifetime(c.getLong("database.mysql.pool.maxLifetimeMs", 1800000));

            // MySQL 성능 최적화 설정
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hc.addDataSourceProperty("useServerPrepStmts", "true");
            hc.addDataSourceProperty("useLocalSessionState", "true");
            hc.addDataSourceProperty("rewriteBatchedStatements", "true");
            hc.addDataSourceProperty("cacheResultSetMetadata", "true");
            hc.addDataSourceProperty("cacheServerConfiguration", "true");
            hc.addDataSourceProperty("elideSetAutoCommits", "true");
            hc.addDataSourceProperty("maintainTimeStats", "false");

            return new HikariDataSource(hc);
        }

        return null;
    }
}