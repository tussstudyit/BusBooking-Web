package com.example.busbooking.shared.payment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VnpayPaymentService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long PAYMENT_TIMEOUT_MILLIS = 300000L;
    private final JdbcTemplate jdbcTemplate;
    private final VnpayProperties properties;

    public VnpayPaymentService(JdbcTemplate jdbcTemplate, VnpayProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public VnpayCreatePaymentResponse createPaymentUrl(String paymentId, HttpServletRequest request) {
        validateConfig();
        PaymentRow payment = findPayment(paymentId);
        if (payment == null) throw new IllegalArgumentException("Không tìm thấy giao dịch thanh toán");
        if (payment.amount() <= 0) throw new IllegalArgumentException("Số tiền thanh toán không hợp lệ");
        if (!"CREATED".equals(payment.status()) && !"PENDING".equals(payment.status())) throw new IllegalArgumentException("Giao dịch không còn có thể thanh toán");
        long nowMillis = System.currentTimeMillis();
        long expiresAtMillis = payment.createdAt() + PAYMENT_TIMEOUT_MILLIS;
        if (nowMillis > expiresAtMillis) {
            expirePayment(payment.id());
            throw new IllegalArgumentException("Giao dịch thanh toán đã hết hạn");
        }
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        LocalDateTime expiresAtDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAtMillis), VN_ZONE);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.tmnCode());
        params.put("vnp_Amount", toVnpayAmount(payment.amount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.id());
        params.put("vnp_OrderInfo", "Thanh toan ve xe BusBooking " + payment.id());
        params.put("vnp_OrderType", "billpayment");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.returnUrl());
        params.put("vnp_IpAddr", clientIp(request));
        params.put("vnp_CreateDate", now.format(VNPAY_DATE));
        params.put("vnp_ExpireDate", expiresAtDate.format(VNPAY_DATE));
        String query = buildQuery(params);
        String paymentUrl = properties.payUrl() + "?" + query + "&vnp_SecureHash=" + hmacSha512(properties.hashSecret(), buildHashData(params));
        String qrImageBase64 = createQrPngBase64(paymentUrl);
        jdbcTemplate.update("UPDATE payments SET status='PENDING',vnp_txn_ref=?,payment_url=?,qr_content=?,qr_image_base64=?,qr_mime_type='image/png',expires_at=?,updated_at=? WHERE id=?",
                payment.id(), paymentUrl, paymentUrl, qrImageBase64, expiresAtMillis, nowMillis, payment.id());
        return new VnpayCreatePaymentResponse("00", "Thành công", payment.id(), paymentUrl, paymentUrl, qrImageBase64, "image/png", payment.amount(), expiresAtMillis);
    }

    public Map<String, String> handleCallback(Map<String, String> params) {
        try {
            if (!verifySecureHash(params)) return Map.of("RspCode", "97", "Message", "Chữ ký không hợp lệ");
            String paymentId = params.get("vnp_TxnRef");
            PaymentRow payment = findPayment(paymentId);
            if (payment == null) return Map.of("RspCode", "01", "Message", "Không tìm thấy đơn thanh toán");
            if (!toVnpayAmount(payment.amount()).equals(params.get("vnp_Amount"))) return Map.of("RspCode", "04", "Message", "Số tiền không hợp lệ");
            boolean success = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
            if (success) {
                confirmPayment(payment.id(), params.get("vnp_TransactionNo"));
                return Map.of("RspCode", "00", "Message", "Xác nhận thành công");
            }
            failPayment(payment.id());
            return Map.of("RspCode", "00", "Message", "Thanh toán thất bại");
        } catch (Exception e) {
            return Map.of("RspCode", "99", "Message", "Lỗi không xác định");
        }
    }

    public void cancelPayment(String paymentId) {
        PaymentRow payment = findPayment(paymentId);
        if (payment == null) throw new IllegalArgumentException("Không tìm thấy giao dịch thanh toán");
        if ("SUCCESS".equals(payment.status())) throw new IllegalStateException("Giao dịch đã thanh toán thành công");
        if ("CANCELLED".equals(payment.status())) throw new IllegalStateException("Giao dịch đã hủy");
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE payments SET status='CANCELLED', updated_at=? WHERE id=?", now, paymentId);
        jdbcTemplate.update("""
                UPDATE tickets
                SET status='CANCELLED',
                    cancellation_reason='Người dùng hủy thanh toán',
                    refund_status='NONE',
                    updated_at=?
                WHERE payment_id=?
                  AND status IN ('PENDING','PENDING_PAYMENT','PAYMENT_FAILED','FAILED','EXPIRED')
                """, now, paymentId);
    }

    public int reconcileSuccessfulPayments() {
        return jdbcTemplate.update("UPDATE tickets t JOIN payments p ON p.id=t.payment_id SET t.status='CONFIRMED', t.updated_at=? WHERE p.status='SUCCESS' AND t.status IN ('PENDING','PENDING_PAYMENT')", System.currentTimeMillis());
    }

    public boolean verifySecureHash(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (!StringUtils.hasText(receivedHash)) return false;
        Map<String, String> signedParams = new TreeMap<>();
        params.forEach((key, value) -> {
            if (value != null && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) signedParams.put(key, value);
        });
        return hmacSha512(properties.hashSecret(), buildHashData(signedParams)).equalsIgnoreCase(receivedHash);
    }

    private void confirmPayment(String paymentId, String transactionNo) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE payments SET status='SUCCESS', vnp_transaction_no=?, updated_at=? WHERE id=?", transactionNo, now, paymentId);
        jdbcTemplate.update("UPDATE tickets SET status='CONFIRMED', updated_at=? WHERE payment_id=?", now, paymentId);
        jdbcTemplate.update("INSERT INTO trip_seats(trip_id,seat_id,ticket_id,user_id,status,created_at,updated_at) SELECT trip_id,seat_id,id,user_id,'CONFIRMED',?,? FROM tickets WHERE payment_id=? ON DUPLICATE KEY UPDATE ticket_id=VALUES(ticket_id),user_id=VALUES(user_id),status='CONFIRMED',updated_at=VALUES(updated_at)", now, now, paymentId);
    }

    private void failPayment(String paymentId) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE payments SET status='FAILED', updated_at=? WHERE id=?", now, paymentId);
        jdbcTemplate.update("UPDATE tickets SET status='PAYMENT_FAILED', updated_at=? WHERE payment_id=?", now, paymentId);
    }

    private void expirePayment(String paymentId) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE payments SET status='EXPIRED', updated_at=? WHERE id=?", now, paymentId);
        jdbcTemplate.update("UPDATE tickets SET status='PAYMENT_FAILED', cancellation_reason='Phiên thanh toán quá 5 phút', updated_at=? WHERE payment_id=?", now, paymentId);
    }

    private PaymentRow findPayment(String paymentId) {
        if (!StringUtils.hasText(paymentId)) return null;
        return jdbcTemplate.query("SELECT id, amount, status, created_at FROM payments WHERE id=?", rs -> {
            if (!rs.next()) return null;
            return new PaymentRow(rs.getString("id"), rs.getDouble("amount"), rs.getString("status"), rs.getLong("created_at"));
        }, paymentId);
    }

    private String toVnpayAmount(Double amount) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream().filter(e -> StringUtils.hasText(e.getValue())).sorted(Comparator.comparing(Map.Entry::getKey)).map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).reduce((l, r) -> l + "&" + r).orElse("");
    }

    private String buildHashData(Map<String, String> params) {
        return params.entrySet().stream().filter(e -> StringUtils.hasText(e.getValue())).sorted(Comparator.comparing(Map.Entry::getKey)).map(e -> e.getKey() + "=" + encode(e.getValue())).reduce((l, r) -> l + "&" + r).orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được dữ liệu VNPAY", e);
        }
    }

    private String createQrPngBase64(String content) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 720, 720, hints);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            int black = Color.BLACK.getRGB();
            int white = Color.WHITE.getRGB();
            for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++) image.setRGB(x, y, matrix.get(x, y) ? black : white);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                return Base64.getEncoder().encodeToString(output.toByteArray());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được mã QR VNPAY", e);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return StringUtils.hasText(forwardedFor) ? forwardedFor.split(",")[0].trim() : request.getRemoteAddr();
    }

    private void validateConfig() {
        if (!StringUtils.hasText(properties.payUrl()) || !StringUtils.hasText(properties.tmnCode()) || !StringUtils.hasText(properties.hashSecret()) || !StringUtils.hasText(properties.returnUrl())) {
            throw new IllegalStateException("Thiếu cấu hình VNPAY");
        }
    }

    private record PaymentRow(String id, Double amount, String status, long createdAt) {}
}
