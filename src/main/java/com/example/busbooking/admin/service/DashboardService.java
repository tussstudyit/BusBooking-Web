package com.example.busbooking.admin.service;

import com.example.busbooking.admin.model.DashboardStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardStats getStats() {
        return stats();
    }

    public DashboardStats stats() {
        return new DashboardStats(
                count("SELECT COUNT(*) FROM users"),
                count("SELECT COUNT(*) FROM routes WHERE is_active=true"),
                count("SELECT COUNT(*) FROM buses WHERE is_active=true"),
                count("SELECT COUNT(*) FROM trips WHERE status='SCHEDULED'"),
                count("SELECT COUNT(*) FROM tickets"),
                count("SELECT COUNT(*) FROM payments WHERE status='SUCCESS'")
        );
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}