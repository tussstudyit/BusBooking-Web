package com.example.busbooking.admin.service;

import com.example.busbooking.admin.model.UserDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserAdminService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isActiveAdmin(String uid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE uid=? AND role='ADMIN' AND is_blocked=false",
                Integer.class,
                uid
        );
        return count != null && count > 0;
    }

    public List<UserDto> findAll(String query) {
        if (!StringUtils.hasText(query)) {
            return jdbcTemplate.query("SELECT * FROM users ORDER BY created_at DESC, id DESC LIMIT 200", this::mapUser);
        }
        String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return jdbcTemplate.query(
                "SELECT * FROM users WHERE LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR phone LIKE ? OR LOWER(role) LIKE ? ORDER BY created_at DESC, id DESC LIMIT 200",
                this::mapUser,
                like,
                like,
                like,
                like
        );
    }

    public void createStaffAccount(String name, String email, String phone, String rawPassword) {
        String normalizedEmail = normalizeGmail(email);
        String normalizedPhone = normalizePhone(phone);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("Vui lòng nhập email Gmail hợp lệ cho nhân viên");
        }
        if (!StringUtils.hasText(normalizedPhone)) {
            throw new IllegalArgumentException("Vui lòng nhập số điện thoại");
        }
        String password = StringUtils.hasText(rawPassword) ? rawPassword.trim() : "123";
        long now = System.currentTimeMillis();
        List<Long> existing = jdbcTemplate.query(
                "SELECT id FROM users WHERE phone=? OR email=? LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                normalizedPhone,
                normalizedEmail
        );
        Long userId;
        String uid;
        if (existing.isEmpty()) {
            uid = newStaffUid();
            String staffCode = staffCodeFromUid(uid);
            String displayName = normalizeStaffName(name, staffCode);
            jdbcTemplate.update(
                    "INSERT INTO users(uid,name,email,password,phone,role,is_blocked,created_at) VALUES (?,?,?,?,?,'STAFF',false,?)",
                    uid,
                    displayName,
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    normalizedPhone,
                    now
            );
            userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE uid=? LIMIT 1", Long.class, uid);
        } else {
            userId = existing.get(0);
            uid = jdbcTemplate.queryForObject("SELECT uid FROM users WHERE id=?", String.class, userId);
            if (!StringUtils.hasText(uid)) {
                uid = newStaffUid();
            }
            String staffCode = staffCodeFromUid(uid);
            String displayName = normalizeStaffName(name, staffCode);
            jdbcTemplate.update(
                    "UPDATE users SET uid=?,name=?,email=?,password=?,phone=?,role='STAFF',is_blocked=false,updated_at=? WHERE id=?",
                    uid,
                    displayName,
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    normalizedPhone,
                    now,
                    userId
            );
        }
        ensureStaffProfile(userId, defaultCompanyId(), staffCodeFromUid(uid), now);
    }

    public void setBlocked(String uid, boolean blocked) {
        jdbcTemplate.update(
                "UPDATE users SET is_blocked=?, updated_at=? WHERE uid=? OR id=?",
                blocked,
                System.currentTimeMillis(),
                uid,
                parseLongOrNegative(uid)
        );
    }

    public String findAuthEmailByPhone(String phone) {
        List<String> emails = jdbcTemplate.query("SELECT email FROM users WHERE phone=? LIMIT 1", (rs, rowNum) -> rs.getString("email"), phone);
        return emails.isEmpty() ? null : emails.get(0);
    }

    private UserDto mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserDto(
                rs.getString("uid"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("role"),
                rs.getBoolean("is_blocked"),
                rs.getLong("created_at")
        );
    }

    private void ensureStaffProfile(Long userId, Long companyId, String staffCode, long now) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO staff_profiles(user_id,company_id,staff_code,position,status,created_at)
                VALUES (?,?,?,?, 'ACTIVE', ?)
                ON DUPLICATE KEY UPDATE company_id=VALUES(company_id),staff_code=VALUES(staff_code),position='STAFF',status='ACTIVE',updated_at=VALUES(created_at)
                """, userId, companyId, staffCode, "STAFF", now);
    }

    private String normalizeStaffName(String value, String staffCode) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return "Nhân viên " + staffCode;
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeGmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[a-z0-9._%+-]+@gmail\\.com$")) {
            return "";
        }
        return email;
    }

    private Long defaultCompanyId() {
        List<Long> companyIds = jdbcTemplate.query("SELECT id FROM bus_companies ORDER BY id LIMIT 1", (rs, rowNum) -> rs.getLong("id"));
        return companyIds.isEmpty() ? null : companyIds.get(0);
    }

    private String newStaffUid() {
        while (true) {
            String uid = "staff-" + UUID.randomUUID().toString().substring(0, 8);
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE uid=?", Integer.class, uid);
            if (count == null || count == 0) {
                return uid;
            }
        }
    }

    private String staffCodeFromUid(String uid) {
        String suffix = uid == null ? UUID.randomUUID().toString().substring(0, 8) : uid.replaceFirst("(?i)^staff-", "");
        return "STAFF-" + suffix.toUpperCase(Locale.ROOT);
    }

    private long parseLongOrNegative(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return -1L;
        }
    }
}
