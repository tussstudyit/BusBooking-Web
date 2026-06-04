package com.example.busbooking.admin.controller;

import com.example.busbooking.admin.service.TicketAdminService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TicketAdminController {
    private final TicketAdminService ticketService;

    public TicketAdminController(TicketAdminService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/tickets")
    public String list(Model model) {
        try {
            model.addAttribute("tickets", ticketService.findAll());
        } catch (IllegalStateException e) {
            model.addAttribute("tickets", List.of());
            model.addAttribute("loadError", "KhÃƒÂ´ng tÃ¡ÂºÂ£i Ã„â€˜Ã†Â°Ã¡Â»Â£c dÃ¡Â»Â¯ liÃ¡Â»â€¡u vÃƒÂ©. MySQL/XAMPP dang tam thoi khong phan hoi.");
        }
        model.addAttribute("pageTitle", "Vé");
        return "tickets/list";
    }
}


