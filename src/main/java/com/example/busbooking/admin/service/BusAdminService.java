package com.example.busbooking.admin.service;

import com.example.busbooking.admin.model.BusDto;
import com.example.busbooking.admin.model.BusForm;
import com.example.busbooking.admin.model.SeatDto;
import com.example.busbooking.admin.util.BusRouteMatcher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BusAdminService {
    private static final int DEFAULT_TOTAL_SEATS = 34;
    private static final int TOTAL_SEATS_24 = 24;
    private static final int TOTAL_SEATS_34 = 34;

    private final JdbcTemplate jdbcTemplate;

    public BusAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BusDto> findAll() {
        return jdbcTemplate.query("SELECT * FROM buses ORDER BY created_at DESC, id DESC", this::mapBus);
    }

    public List<BusDto> findTripOptions() {
        return jdbcTemplate.query("""
                        SELECT b.*
                        FROM buses b
                        WHERE b.is_active=true
                        ORDER BY b.bus_name, b.license_plate
                        """, this::mapBus)
                .stream()
                .filter(bus -> hasActiveRouteForBus(bus.serviceProvinceId()))
                .toList();
    }

    public BusDto findByDocumentId(String documentId) {
        return jdbcTemplate.query("SELECT * FROM buses WHERE id = ?", rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy xe");
            return mapBus(rs, 1);
        }, SqlRows.parseDocumentId(documentId));
    }

    public void create(BusForm form) {
        int totalSeats = supportedTotalSeats(form.getTotalSeats());
        long now = System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO buses(bus_name,total_seats,license_plate,seat_layout_json,is_active,created_at) VALUES (?,?,?,'',?,?)",
                form.getBusName(), totalSeats, form.getLicensePlate(), !Boolean.FALSE.equals(form.getIsActive()), now);
        Long busId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (busId != null) {
            insertSeats(busId, totalSeats, now);
        }
    }

    public void update(String documentId, BusForm form) {
        int totalSeats = supportedTotalSeats(form.getTotalSeats());
        jdbcTemplate.update("UPDATE buses SET bus_name=?,total_seats=?,license_plate=?,is_active=?,updated_at=? WHERE id=?",
                form.getBusName(), totalSeats, form.getLicensePlate(), !Boolean.FALSE.equals(form.getIsActive()), System.currentTimeMillis(), SqlRows.parseDocumentId(documentId));
    }

    public void setActive(String documentId, boolean active) {
        jdbcTemplate.update("UPDATE buses SET is_active=?, updated_at=? WHERE id=?", active, System.currentTimeMillis(), SqlRows.parseDocumentId(documentId));
    }

    public List<SeatDto> findSeats(String documentId) {
        return jdbcTemplate.query("SELECT * FROM seats WHERE bus_id = ? ORDER BY floor,row_index,column_index,id", this::mapSeat, SqlRows.parseDocumentId(documentId));
    }

    public void generateSimpleSeats(String documentId) {
        long busId = SqlRows.parseDocumentId(documentId);
        Integer exists = jdbcTemplate.queryForObject("SELECT total_seats FROM buses WHERE id=?", Integer.class, busId);
        if (exists == null) throw new IllegalArgumentException("Khong tim thay xe");
        int totalSeats = supportedTotalSeats(exists);
        jdbcTemplate.update("UPDATE buses SET total_seats=?, updated_at=? WHERE id=?", totalSeats, System.currentTimeMillis(), busId);
        jdbcTemplate.update("DELETE FROM seats WHERE bus_id=?", busId);
        long now = System.currentTimeMillis();
        insertSeats(busId, totalSeats, now);
    }

    private void insertSeats(long busId, int totalSeats, long now) {
        for (int i = 1; i <= totalSeats; i++) {
            SeatPosition seat = seatPosition(i, totalSeats);
            jdbcTemplate.update("INSERT INTO seats(bus_id,seat_number,floor,row_index,column_index,is_window,is_aisle,seat_type,created_at) VALUES (?,?,?,?,?,?,false,'NORMAL',?)",
                    busId, seat.seatNumber(), seat.floor(), seat.rowIndex(), seat.columnIndex(), seat.columnIndex() == 0 || seat.columnIndex() == 2, now);
        }
    }

    private SeatPosition seatPosition(int index, int totalSeats) {
        int seatsPerFloor = totalSeats / 2;
        int zeroBased = index - 1;
        int floor = zeroBased < seatsPerFloor ? 1 : 2;
        int local = zeroBased % seatsPerFloor;
        int number = local + 1;
        char prefix = floor == 1 ? 'A' : 'B';
        String seatNumber = String.format(Locale.ROOT, "%c%02d", prefix, number);
        int rowIndex;
        int columnIndex;
        if (totalSeats == TOTAL_SEATS_24) {
            rowIndex = local / 2;
            columnIndex = local % 2 == 0 ? 0 : 2;
        } else {
            rowIndex = local < 2 ? 0 : 1 + ((local - 2) / 3);
            columnIndex = local == 0 ? 0 : (local == 1 ? 2 : (local - 2) % 3);
        }
        return new SeatPosition(seatNumber, floor, rowIndex, columnIndex);
    }

    private int supportedTotalSeats(Integer totalSeats) {
        if (totalSeats != null && (totalSeats == TOTAL_SEATS_24 || totalSeats == TOTAL_SEATS_34)) {
            return totalSeats;
        }
        return DEFAULT_TOTAL_SEATS;
    }
    private BusDto mapBus(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        String busName = rs.getString("bus_name");
        String licensePlate = rs.getString("license_plate");
        return new BusDto(SqlRows.documentId(id), id, busName, rs.getInt("total_seats"), licensePlate, BusRouteMatcher.serviceProvinceId(busName, licensePlate), rs.getString("seat_layout_json"), rs.getBoolean("is_active"), rs.getLong("created_at"));
    }

    private boolean hasActiveRouteForBus(String serviceProvinceId) {
        if (serviceProvinceId == null || serviceProvinceId.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM routes
                        WHERE is_active=true
                          AND (origin_id=? OR destination_id=?)
                        """, Integer.class, serviceProvinceId, serviceProvinceId);
        return count != null && count > 0;
    }

    private SeatDto mapSeat(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        return new SeatDto(SqlRows.documentId(id), id, rs.getLong("bus_id"), rs.getString("seat_number"), rs.getInt("floor"), rs.getInt("row_index"), rs.getInt("column_index"), rs.getBoolean("is_window"), rs.getBoolean("is_aisle"), rs.getString("seat_type"), rs.getLong("created_at"));
    }

    private record SeatPosition(String seatNumber, int floor, int rowIndex, int columnIndex) {}
}
