package com.example.busbooking.user.service;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service("userLabels")
public class UserLabelService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Bangkok");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yy").withZone(VN_ZONE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(VN_ZONE);
    private static final NumberFormat VND = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public String date(Long millis) {
        if (millis == null || millis <= 0) {
            return "Chưa xác định";
        }
        return DATE.format(Instant.ofEpochMilli(millis));
    }

    public String dateTime(Long millis) {
        if (millis == null || millis <= 0) {
            return "Chưa xác định";
        }
        return DATE_TIME.format(Instant.ofEpochMilli(millis));
    }

    public String money(Number amount) {
        if (amount == null) {
            return "0 đ";
        }
        return VND.format(amount.longValue()) + " đ";
    }

    public String status(String value) {
        if (value == null || value.isBlank()) {
            return "Chưa xác định";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CREATED" -> "Đã tạo";
            case "PENDING" -> "Đang chờ";
            case "PENDING_PAYMENT" -> "Chờ thanh toán";
            case "SUCCESS" -> "Thành công";
            case "FAILED", "PAYMENT_FAILED" -> "Thanh toán thất bại";
            case "EXPIRED" -> "Hết hạn";
            case "CANCELLED" -> "Đã hủy";
            case "CONFIRMED", "PAID" -> "Đã thanh toán";
            case "CHECKED_IN" -> "Đã lên xe";
            case "SCHEDULED" -> "Chưa khởi hành";
            case "RUNNING", "DEPARTED" -> "Đang chạy";
            case "COMPLETED" -> "Hoàn thành";
            default -> value;
        };
    }
}
