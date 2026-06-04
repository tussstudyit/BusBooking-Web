package com.example.busbooking.admin.controller;

import com.example.busbooking.admin.model.BusDto;
import com.example.busbooking.admin.model.BusForm;
import com.example.busbooking.admin.service.AdminDemoData;
import com.example.busbooking.admin.service.BusAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/buses")
public class BusAdminController {
    private final BusAdminService busService;

    public BusAdminController(BusAdminService busService) {
        this.busService = busService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("demoMode", false);
        try {
            model.addAttribute("buses", busService.findAll());
        } catch (IllegalStateException e) {
            model.addAttribute("buses", AdminDemoData.buses());
            model.addAttribute("demoMode", true);
            model.addAttribute("loadError", "Không tải được dữ liệu xe từ MySQL: " + rootMessage(e));
        }
        model.addAttribute("pageTitle", "Phương tiện");
        return "buses/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        BusForm form = new BusForm();
        form.setTotalSeats(34);
        model.addAttribute("busForm", form);
        model.addAttribute("mode", "create");
        model.addAttribute("pageTitle", "Thêm xe");
        return "buses/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute BusForm busForm, BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("mode", "create");
            return "buses/form";
        }
        busService.create(busForm);
        return "redirect:/buses";
    }

    @GetMapping("/{documentId}/edit")
    public String editForm(@PathVariable String documentId, Model model) {
        BusDto bus = busService.findByDocumentId(documentId);
        BusForm form = new BusForm();
        form.setBusName(bus.busName());
        form.setTotalSeats(bus.totalSeats());
        form.setLicensePlate(bus.licensePlate());
        form.setIsActive(!Boolean.FALSE.equals(bus.isActive()));
        model.addAttribute("busForm", form);
        model.addAttribute("documentId", documentId);
        model.addAttribute("mode", "edit");
        model.addAttribute("pageTitle", "Sửa xe");
        return "buses/form";
    }

    @PostMapping("/{documentId}")
    public String update(
            @PathVariable String documentId,
            @Valid @ModelAttribute BusForm busForm,
            BindingResult result,
            Model model
    ) {
        if (result.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("documentId", documentId);
            return "buses/form";
        }
        busService.update(documentId, busForm);
        return "redirect:/buses";
    }

    @PostMapping("/{documentId}/active/{active}")
    public String setActive(@PathVariable String documentId, @PathVariable boolean active) {
        busService.setActive(documentId, active);
        return "redirect:/buses";
    }

    @GetMapping("/{documentId}/seats")
    public String seats(@PathVariable String documentId, Model model) {
        try {
            model.addAttribute("bus", busService.findByDocumentId(documentId));
            model.addAttribute("seats", busService.findSeats(documentId));
        } catch (IllegalStateException e) {
            model.addAttribute("seats", List.of());
            model.addAttribute("loadError", "KhÃƒÂ´ng tÃ¡ÂºÂ£i Ã„â€˜Ã†Â°Ã¡Â»Â£c dÃ¡Â»Â¯ liÃ¡Â»â€¡u ghÃ¡ÂºÂ¿. MySQL/XAMPP dang tam thoi khong phan hoi.");
        }
        model.addAttribute("pageTitle", "Ghế xe");
        return "buses/seats";
    }

    @PostMapping("/{documentId}/seats/generate")
    public String generateSeats(@PathVariable String documentId) {
        busService.generateSimpleSeats(documentId);
        return "redirect:/buses/" + documentId + "/seats";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }
}


