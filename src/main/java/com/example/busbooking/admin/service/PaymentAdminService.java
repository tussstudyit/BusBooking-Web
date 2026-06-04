package com.example.busbooking.admin.service;

import com.example.busbooking.admin.model.PaymentDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentAdminService {
    private final JdbcTemplate jdbcTemplate;

    public PaymentAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PaymentDto> findAll() {
        return jdbcTemplate.query("SELECT * FROM payments ORDER BY created_at DESC LIMIT 200", this::mapPayment);
    }

    private PaymentDto mapPayment(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentDto(rs.getString("id"), rs.getString("ticket_id"), rs.getString("user_id"), SqlRows.nullableLong(rs, "trip_id"), SqlRows.nullableLong(rs, "seat_id"), rs.getDouble("amount"), rs.getString("provider"), rs.getString("status"), rs.getString("vnp_txn_ref"), rs.getString("vnp_transaction_no"), rs.getLong("created_at"), SqlRows.nullableLong(rs, "updated_at"));
    }
}
