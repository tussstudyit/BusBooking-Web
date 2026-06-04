package com.example.busbooking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAuthServiceTest {
    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    private final UserAuthService service = new UserAuthService(jdbcTemplate, passwordEncoder);

    @Test
    void registerCreatesSharedUserAccountForAppAndWebLogin() {
        UserAuthService.AuthUser user = user();
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM users WHERE email=? OR phone=?"),
                eq(Integer.class),
                eq("user@example.com"),
                eq("0900000001")
        )).thenReturn(0);
        when(passwordEncoder.encode("secret1")).thenReturn("bcrypt-secret");
        when(jdbcTemplate.query(
                eq("SELECT id,name,email,phone,role,is_blocked,created_at FROM users WHERE phone=? AND role='USER' LIMIT 1"),
                ArgumentMatchers.<RowMapper<UserAuthService.AuthUser>>any(),
                eq("0900000001")
        )).thenReturn(List.of(user));

        UserAuthService.AuthUser registered = service.register(
                " Nguyen Van A ",
                " USER@EXAMPLE.COM ",
                " 0900000001 ",
                " secret1 "
        );

        assertThat(registered).isEqualTo(user);
        verify(jdbcTemplate).update(
                eq("INSERT INTO users(uid,name,email,password,phone,role,is_blocked,created_at) VALUES (?,?,?,?,?,'USER',false,?)"),
                anyString(),
                eq("Nguyen Van A"),
                eq("user@example.com"),
                eq("bcrypt-secret"),
                eq("0900000001"),
                any(Long.class)
        );
    }

    @Test
    void loginAcceptsSharedUserAccountWhenPasswordMatches() {
        UserAuthService.AuthUser user = user();
        when(jdbcTemplate.query(
                eq("SELECT id,name,email,phone,role,is_blocked,created_at FROM users WHERE phone=? AND role='USER' LIMIT 1"),
                ArgumentMatchers.<RowMapper<UserAuthService.AuthUser>>any(),
                eq("0900000001")
        )).thenReturn(List.of(user));
        when(jdbcTemplate.queryForObject("SELECT password FROM users WHERE id=?", String.class, 1L)).thenReturn("bcrypt-secret");
        when(passwordEncoder.matches("secret1", "bcrypt-secret")).thenReturn(true);

        UserAuthService.AuthUser loggedIn = service.login(" 0900000001 ", " secret1 ");

        assertThat(loggedIn).isEqualTo(user);
        assertThat(loggedIn.toSession().phone()).isEqualTo("0900000001");
    }

    @Test
    void blockedUserCannotLogin() {
        UserAuthService.AuthUser blocked = new UserAuthService.AuthUser(
                1L,
                "Nguyen Van A",
                "user@example.com",
                "0900000001",
                "USER",
                true,
                123L
        );
        when(jdbcTemplate.query(
                eq("SELECT id,name,email,phone,role,is_blocked,created_at FROM users WHERE phone=? AND role='USER' LIMIT 1"),
                ArgumentMatchers.<RowMapper<UserAuthService.AuthUser>>any(),
                eq("0900000001")
        )).thenReturn(List.of(blocked));

        assertThat(service.login("0900000001", "secret1")).isNull();
    }

    @Test
    void duplicateEmailOrPhoneCannotRegister() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM users WHERE email=? OR phone=?"),
                eq(Integer.class),
                eq("user@example.com"),
                eq("0900000001")
        )).thenReturn(1);

        assertThatThrownBy(() -> service.register("Nguyen Van A", "user@example.com", "0900000001", "secret1"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("Email hoặc số điện thoại");
    }

    @Test
    void passwordMustHaveAtLeastSixCharacters() {
        assertThatThrownBy(() -> service.register("Nguyen Van A", "user@example.com", "0900000001", "12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mật khẩu");
    }

    private UserAuthService.AuthUser user() {
        return new UserAuthService.AuthUser(
                1L,
                "Nguyen Van A",
                "user@example.com",
                "0900000001",
                "USER",
                false,
                123L
        );
    }
}
