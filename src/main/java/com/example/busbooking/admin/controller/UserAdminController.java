package com.example.busbooking.admin.controller;

import com.example.busbooking.admin.service.UserAdminService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UserAdminController {
    private final UserAdminService userService;

    public UserAdminController(UserAdminService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public String list(@RequestParam(defaultValue = "") String q, Model model) {
        try {
            model.addAttribute("users", userService.findAll(q));
        } catch (IllegalStateException e) {
            model.addAttribute("users", List.of());
            model.addAttribute("loadError", "KhÃƒÂ´ng tÃ¡ÂºÂ£i Ã„â€˜Ã†Â°Ã¡Â»Â£c dÃ¡Â»Â¯ liÃ¡Â»â€¡u ngÃ†Â°Ã¡Â»Âi dÃƒÂ¹ng. MySQL/XAMPP dang tam thoi khong phan hoi.");
        }
        model.addAttribute("q", q);
        model.addAttribute("pageTitle", "Tài khoản");
        return "users/list";
    }

    @PostMapping("/users/staff")
    public String createStaffAccount(
            @RequestParam(defaultValue = "") String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam(defaultValue = "123") String password,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.createStaffAccount(name, email, phone, password);
            redirectAttributes.addFlashAttribute("staffMessage", "Đã tạo tài khoản nhân viên. Nhân viên đăng nhập bằng email Gmail và mật khẩu.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("staffError", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/users/{uid}/blocked/{blocked}")
    public String setBlocked(@PathVariable String uid, @PathVariable boolean blocked) {
        userService.setBlocked(uid, blocked);
        return "redirect:/users";
    }
}


