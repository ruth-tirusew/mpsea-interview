package com.mpsesa.interview.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbLocks {
    private final JdbcTemplate jdbc;

    public DbLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public void boundWaits() {
        jdbc.execute("SET LOCAL lock_timeout = '1500ms'");
    }
}
