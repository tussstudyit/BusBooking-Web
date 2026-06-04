package com.example.busbooking.admin.service;

import java.sql.ResultSet;
import java.sql.SQLException;

final class SqlRows {
    private SqlRows() {}

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static String documentId(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    static long parseDocumentId(String documentId) {
        try {
            return Long.parseLong(documentId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mã không hợp lệ: " + documentId, e);
        }
    }
}
