package com.example.busbooking.shared.payment;

public record VnpayCreatePaymentResponse(
        String code,
        String message,
        String paymentId,
        String paymentUrl,
        String qrContent,
        String qrImageBase64,
        String qrMimeType,
        Double amount,
        Long expiresAt
) {
    public VnpayCreatePaymentResponse(String code, String message, String paymentUrl) {
        this(code, message, null, paymentUrl, paymentUrl, null, null, null, null);
    }
}
