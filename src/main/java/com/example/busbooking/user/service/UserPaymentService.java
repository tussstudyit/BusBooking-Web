package com.example.busbooking.user.service;

import com.example.busbooking.shared.payment.VnpayCreatePaymentResponse;
import com.example.busbooking.shared.payment.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class UserPaymentService {
    private final VnpayPaymentService vnpayPaymentService;

    public UserPaymentService(VnpayPaymentService vnpayPaymentService) {
        this.vnpayPaymentService = vnpayPaymentService;
    }

    public VnpayCreatePaymentResponse createPaymentQr(String paymentId, HttpServletRequest request) {
        return vnpayPaymentService.createPaymentUrl(paymentId, request);
    }

    public void cancelPayment(String paymentId) {
        vnpayPaymentService.cancelPayment(paymentId);
    }
}
