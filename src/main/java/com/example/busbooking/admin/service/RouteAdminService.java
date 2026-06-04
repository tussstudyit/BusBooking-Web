package com.example.busbooking.admin.service;

import com.example.busbooking.shared.model.RouteCatalogEntry;
import com.example.busbooking.admin.model.RouteDto;
import com.example.busbooking.admin.model.RouteForm;
import com.example.busbooking.shared.service.RouteCatalogService;
import com.example.busbooking.shared.service.RouteCatalogService.ProvinceLocation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RouteAdminService {
    private final JdbcTemplate jdbcTemplate;
    private final RouteCatalogService routeCatalogService;

    public RouteAdminService(JdbcTemplate jdbcTemplate, RouteCatalogService routeCatalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.routeCatalogService = routeCatalogService;
    }

    public List<RouteDto> findAll() {
        return jdbcTemplate.query("SELECT * FROM routes ORDER BY origin, destination, seat_count, created_at DESC, id DESC", this::mapRoute);
    }

    public RouteDto findByDocumentId(String documentId) {
        return jdbcTemplate.query("SELECT * FROM routes WHERE id = ?", rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy tuyến");
            return mapRoute(rs, 1);
        }, SqlRows.parseDocumentId(documentId));
    }

    public void create(RouteForm form) {
        RouteFields f = normalize(form);
        jdbcTemplate.update("INSERT INTO routes(origin_id,destination_id,origin,destination,distance,seat_count,suggested_price,duration_ms,is_active,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                f.originId(), f.destinationId(), f.origin(), f.destination(), f.distance(), f.seatCount(), f.suggestedPrice(), f.durationMs(), !Boolean.FALSE.equals(form.getIsActive()), System.currentTimeMillis());
    }

    public void update(String documentId, RouteForm form) {
        RouteFields f = normalize(form);
        jdbcTemplate.update("UPDATE routes SET origin_id=?,destination_id=?,origin=?,destination=?,distance=?,seat_count=?,suggested_price=?,duration_ms=?,is_active=?,updated_at=? WHERE id=?",
                f.originId(), f.destinationId(), f.origin(), f.destination(), f.distance(), f.seatCount(), f.suggestedPrice(), f.durationMs(), !Boolean.FALSE.equals(form.getIsActive()), System.currentTimeMillis(), SqlRows.parseDocumentId(documentId));
    }

    public void setActive(String documentId, boolean active) {
        jdbcTemplate.update("UPDATE routes SET is_active=?, updated_at=? WHERE id=?", active, System.currentTimeMillis(), SqlRows.parseDocumentId(documentId));
    }

    private RouteFields normalize(RouteForm form) {
        ProvinceLocation originLocation = routeCatalogService.canonicalLocation(form.getOriginId(), form.getOrigin());
        ProvinceLocation destinationLocation = routeCatalogService.canonicalLocation(form.getDestinationId(), form.getDestination());
        String originId = originLocation.id();
        String destinationId = destinationLocation.id();
        RouteCatalogEntry entry = routeCatalogService.findRoute(originId, destinationId).orElse(null);
        String origin = entry == null ? originLocation.name() : entry.originName();
        String destination = entry == null ? destinationLocation.name() : entry.destinationName();
        int distance = form.getDistance() == null || form.getDistance() <= 0 ? entry == null ? 1 : entry.distanceKm() : form.getDistance();
        int seatCount = supportedSeatCount(form.getSeatCount());
        long basePrice = Math.max(80000L, Math.round(distance * 850.0));
        long suggestedPrice = form.getSuggestedPrice() != null && form.getSuggestedPrice() > 0
                ? form.getSuggestedPrice()
                : defaultPrice(basePrice, seatCount);
        long durationMs = Math.max(3600000L, Math.round(distance / 48.0 * 3600000));
        return new RouteFields(originId, destinationId, origin, destination, distance, seatCount, suggestedPrice, durationMs);
    }

    private RouteDto mapRoute(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        ProvinceLocation origin = routeCatalogService.canonicalLocation(rs.getString("origin_id"), rs.getString("origin"));
        ProvinceLocation destination = routeCatalogService.canonicalLocation(rs.getString("destination_id"), rs.getString("destination"));
        return new RouteDto(SqlRows.documentId(id), id, origin.id(), destination.id(), origin.name(), destination.name(), rs.getInt("distance"), rs.getInt("seat_count"), rs.getLong("suggested_price"), rs.getLong("duration_ms"), rs.getBoolean("is_active"), rs.getLong("created_at"));
    }

    private int supportedSeatCount(Integer seatCount) {
        return seatCount != null && seatCount == 24 ? 24 : 34;
    }

    private long defaultPrice(long basePrice, int seatCount) {
        if (seatCount == 24) {
            return Math.round(basePrice * 1.25 / 1000.0) * 1000L;
        }
        return basePrice;
    }

    private record RouteFields(String originId, String destinationId, String origin, String destination, int distance, int seatCount, long suggestedPrice, long durationMs) {}
}

