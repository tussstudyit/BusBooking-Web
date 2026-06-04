package com.example.busbooking.user.service;

import com.example.busbooking.user.model.UserWebModels.UserSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserAuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserAuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthUser register(String name, String email, String phone, String password) {
        String normalizedName = requiredText(name, "Vui lòng nhập họ tên");
        String normalizedEmail = requiredText(email, "Vui lòng nhập email").toLowerCase(Locale.ROOT);
        String normalizedPhone = requiredText(phone, "Vui lòng nhập số điện thoại");
        String normalizedPassword = requiredText(password, "Vui lòng nhập mật khẩu");
        if (normalizedPassword.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự");
        }
        if (existsByEmailOrPhone(normalizedEmail, normalizedPhone)) {
            throw new DuplicateKeyException("Email hoặc số điện thoại đã tồn tại");
        }

        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO users(uid,name,email,password,phone,role,is_blocked,created_at) VALUES (?,?,?,?,?,'USER',false,?)",
                UUID.randomUUID().toString(),
                normalizedName,
                normalizedEmail,
                passwordEncoder.encode(normalizedPassword),
                normalizedPhone,
                now
        );
        return findUserByPhone(normalizedPhone);
    }

    public AuthUser login(String phone, String password) {
        String normalizedPhone = text(phone);
        String normalizedPassword = text(password);
        if (!StringUtils.hasText(normalizedPhone) || !StringUtils.hasText(normalizedPassword)) {
            return null;
        }

        AuthUser user = findUserByPhone(normalizedPhone);
        if (user == null || user.blocked() || !"USER".equalsIgnoreCase(user.role())) {
            return null;
        }
        String hash = jdbcTemplate.queryForObject("SELECT password FROM users WHERE id=?", String.class, user.id());
        return hash != null && passwordEncoder.matches(normalizedPassword, hash) ? user : null;
    }

    public AuthUser findUserById(long id) {
        var users = jdbcTemplate.query(
                "SELECT id,name,email,phone,role,is_blocked,created_at FROM users WHERE id=? AND role='USER' LIMIT 1",
                this::mapUser,
                id
        );
        return users.isEmpty() ? null : users.get(0);
    }

    public AuthUser findUserByPhone(String phone) {
        var users = jdbcTemplate.query(
                "SELECT id,name,email,phone,role,is_blocked,created_at FROM users WHERE phone=? AND role='USER' LIMIT 1",
                this::mapUser,
                text(phone)
        );
        return users.isEmpty() ? null : users.get(0);
    }

    public AuthUser updateUser(long userId, String name, String email, String phone) {
        String normalizedName = requiredText(name, "Vui lòng nhập họ tên");
        String normalizedEmail = requiredText(email, "Vui lòng nhập email").toLowerCase(Locale.ROOT);
        String normalizedPhone = requiredText(phone, "Vui lòng nhập số điện thoại");
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "UPDATE users SET name=?, email=?, phone=?, updated_at=? WHERE id=? AND role='USER'",
                normalizedName,
                normalizedEmail,
                normalizedPhone,
                now,
                userId
        );
        return findUserById(userId);
    }

    private boolean existsByEmailOrPhone(String email, String phone) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email=? OR phone=?",
                Integer.class,
                email,
                phone
        );
        return count != null && count > 0;
    }

    private AuthUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AuthUser(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("role"),
                rs.getBoolean("is_blocked"),
                rs.getLong("created_at")
        );
    }

    private String requiredText(String value, String message) {
        String normalized = text(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record AuthUser(
            Long id,
            String name,
            String email,
            String phone,
            String role,
            boolean blocked,
            Long createdAt
    ) {
        public UserSession toSession() {
            return new UserSession(id, name, email, phone, role);
        }
    }
}
