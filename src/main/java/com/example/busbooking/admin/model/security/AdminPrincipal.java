package com.example.busbooking.admin.model.security;

public record AdminPrincipal(
        String uid,
        String email,
        String idToken
) {
}
