package com.example.busbooking.admin.service;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbcTemplate;

    public AdminUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String raw = username == null ? "" : username.trim();
        String login = raw.toLowerCase(Locale.ROOT);
        return jdbcTemplate.query("""
                        SELECT email, password, role, is_blocked
                        FROM users
                        WHERE LOWER(email) = ?
                        LIMIT 1
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new UsernameNotFoundException("Không tìm thấy tài khoản quản trị");
                    }
                    if (!"ADMIN".equalsIgnoreCase(rs.getString("role")) || rs.getBoolean("is_blocked")) {
                        throw new UsernameNotFoundException("Admin account is not active");
                    }
                    return new User(rs.getString("email"), rs.getString("password"), AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
                }, login);
    }
}
