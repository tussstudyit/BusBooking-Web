package com.example.busbooking.api.payment;

import com.example.busbooking.shared.payment.VnpayCreatePaymentResponse;
import com.example.busbooking.shared.payment.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VnpayPaymentController {
    private final VnpayPaymentService vnpayPaymentService;

    public VnpayPaymentController(VnpayPaymentService vnpayPaymentService) {
        this.vnpayPaymentService = vnpayPaymentService;
    }

    @PostMapping("/api/payments/vnpay/create")
    public VnpayCreatePaymentResponse create(
            @RequestParam String paymentId,
            HttpServletRequest request
    ) {
        try {
            return vnpayPaymentService.createPaymentUrl(paymentId, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return new VnpayCreatePaymentResponse("99", rootMessage(e), null);
        }
    }

    @PostMapping("/api/payments/vnpay/cancel")
    public Map<String, String> cancel(@RequestParam String paymentId) {
        try {
            vnpayPaymentService.cancelPayment(paymentId);
            return Map.of("code", "00", "message", "Đã hủy thanh toán", "paymentId", paymentId);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Map.of("code", "99", "message", rootMessage(e), "paymentId", paymentId);
        }
    }

    @GetMapping("/api/payments/vnpay/ipn")
    public Map<String, String> ipn(@RequestParam Map<String, String> params) {
        return vnpayPaymentService.handleCallback(params);
    }

    @GetMapping("/api/payments/vnpay/return")
    public ResponseEntity<String> vnpayReturn(@RequestParam Map<String, String> params) {
        Map<String, String> result = vnpayPaymentService.handleCallback(params);
        boolean handled = "00".equals(result.get("RspCode"));
        String paymentId = params.getOrDefault("vnp_TxnRef", "");
        String appReturnUrl = "busbooking://payment-return?paymentId=" + encode(paymentId);
        String webReturnUrl = "/user/payments/" + encode(paymentId);
        String message = handled
                ? "Thanh toán VNPAY đã được xử lý. Bạn có thể quay lại ứng dụng BusBooking."
                : "Thanh toán VNPAY không hợp lệ: " + result.get("Message");
        return ResponseEntity.ok("""
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>BusBooking VNPAY</title>
                </head>
                <body style="font-family:Arial,sans-serif;padding:32px">
                  <h2>%s</h2>
                  <p><a href="%s" style="display:inline-block;margin-top:16px;padding:12px 18px;background:#1565c0;color:white;text-decoration:none;border-radius:6px">Mở lại ứng dụng</a></p>
                  <p><a href="%s" style="display:inline-block;margin-top:8px;color:#1565c0">Quay về trang thanh toán web</a></p>
                  <script>
                    setTimeout(function () { window.location.href = "%s"; }, 1200);
                  </script>
                </body>
                </html>
                """.formatted(escapeHtml(message), appReturnUrl, webReturnUrl, appReturnUrl));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}


