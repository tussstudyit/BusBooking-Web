package com.example.busbooking.admin.model;

public record StaffOption(
        Long id,
        String name,
        String email,
        String staffCode
) {
    public String label() {
        String code = staffCode == null || staffCode.isBlank() ? "STAFF-" + id : staffCode;
        return code + " - " + name + " - " + email;
    }
}
