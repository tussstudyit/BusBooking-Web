package com.example.busbooking.admin.service;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service("timeLabels")
public class TimeLabelService {
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
}
