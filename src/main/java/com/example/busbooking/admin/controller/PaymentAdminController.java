package com.example.busbooking.admin.controller;

import com.example.busbooking.admin.service.PaymentAdminService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaymentAdminController {
    private final PaymentAdminService paymentService;

    public PaymentAdminController(PaymentAdminService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments")
    public String list(Model model) {
        try {
            model.addAttribute("payments", paymentService.findAll());
        } catch (IllegalStateException e) {
            model.addAttribute("payments", List.of());
            model.addAttribute("loadError", "KhÃƒÂ´ng tÃ¡ÂºÂ£i Ã„â€˜Ã†Â°Ã¡Â»Â£c dÃ¡Â»Â¯ liÃ¡Â»â€¡u thanh toÃƒÂ¡n. MySQL/XAMPP dang tam thoi khong phan hoi.");
        }
        model.addAttribute("pageTitle", "Thanh toán");
        return "payments/list";
    }
}


