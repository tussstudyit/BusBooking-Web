package com.example.busbooking.admin.service;

import java.util.Locale;
import org.springframework.stereotype.Service;

@Service("statusLabels")
public class StatusLabelService {
    public String label(String value) {
        if (value == null || value.isBlank()) {
            return "Chưa xác định";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CREATED" -> "Đã tạo";
            case "PENDING" -> "Đang chờ";
            case "PENDING_PAYMENT" -> "Chờ thanh toán";
            case "SUCCESS" -> "Thành công";
            case "FAILED" -> "Thất bại";
            case "PAYMENT_FAILED" -> "Thanh toán thất bại";
            case "EXPIRED" -> "Hết hạn";
            case "CANCELLED" -> "Đã hủy";
            case "CONFIRMED", "PAID" -> "Đã thanh toán";
            case "CHECKED_IN" -> "Đã lên xe";
            case "NOT_CHECKED_IN" -> "Chưa lên xe";
            case "SCHEDULED" -> "Chưa khởi hành";
            case "DEPARTED" -> "Đã khởi hành";
            case "RUNNING" -> "Đang chạy";
            case "COMPLETED" -> "Hoàn thành";
            case "AVAILABLE" -> "Còn trống";
            case "BOOKED" -> "Đã đặt";
            case "FULL" -> "Hết chỗ";
            case "ACTIVE" -> "Đang hoạt động";
            case "INACTIVE" -> "Ngừng hoạt động";
            case "ADMIN" -> "Quản trị viên";
            case "STAFF" -> "Nhân viên";
            case "USER" -> "Khách hàng";
            case "NONE" -> "Không có";
            default -> "Chưa xác định";
        };
    }

    public String active(boolean value) {
        return value ? "Đang hoạt động" : "Ngừng hoạt động";
    }

    public String blocked(boolean value) {
        return value ? "Đã khóa" : "Đang hoạt động";
    }

    public String seatType(String value) {
        if (value == null || value.isBlank()) {
            return "Thường";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NORMAL", "STANDARD" -> "Thường";
            case "VIP" -> "VIP";
            case "SLEEPER" -> "Giường nằm";
            default -> value;
        };
    }
}
