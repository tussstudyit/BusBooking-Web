package com.example.busbooking.admin.service;

import com.example.busbooking.admin.model.TicketDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketAdminService {
    private final JdbcTemplate jdbcTemplate;

    public TicketAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TicketDto> findAll() {
        return jdbcTemplate.query("SELECT * FROM tickets ORDER BY booking_time DESC LIMIT 200", this::mapTicket);
    }

    private TicketDto mapTicket(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        return new TicketDto(SqlRows.documentId(id), id, String.valueOf(rs.getLong("user_id")), rs.getLong("trip_id"), rs.getLong("seat_id"), SqlRows.nullableLong(rs, "bus_id"), rs.getString("payment_id"), rs.getLong("booking_time"), rs.getString("status"), rs.getString("refund_status"));
    }
}
