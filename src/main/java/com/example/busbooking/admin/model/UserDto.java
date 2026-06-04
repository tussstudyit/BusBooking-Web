package com.example.busbooking.admin.model;

public record UserDto(
        String uid,
        String name,
        String email,
        String phone,
        String role,
        Boolean isBlocked,
        Long createdAt
) {
}
