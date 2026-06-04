package com.example.busbooking.admin.service;

import com.example.busbooking.shared.payment.VnpayPaymentService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupMaintenanceService {
    private final VnpayPaymentService vnpayPaymentService;

    public StartupMaintenanceService(VnpayPaymentService vnpayPaymentService) {
        this.vnpayPaymentService = vnpayPaymentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcilePayments() {
        vnpayPaymentService.reconcileSuccessfulPayments();
    }
}