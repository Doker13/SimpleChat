package com.project;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;

public class DatabaseManager {

    private static final HikariDataSource dataSource;
    private static final DSLContext dsl;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv("DB_URL"));
        config.setUsername(System.getenv("DB_USERNAME"));
        config.setPassword(System.getenv("DB_PASSWORD"));

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public static DSLContext dsl() {
        return dsl;
    }

    public static DataSource dataSource() {
        return dataSource;
    }
}