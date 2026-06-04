package com.example.busbooking.admin.controller;

import com.example.busbooking.admin.model.RouteDto;
import com.example.busbooking.admin.model.RouteForm;
import com.example.busbooking.admin.service.AdminDemoData;
import com.example.busbooking.shared.service.RouteCatalogService;
import com.example.busbooking.admin.service.RouteAdminService;
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
@RequestMapping("/routes")
public class RouteAdminController {
    private final RouteAdminService routeService;
    private final RouteCatalogService routeCatalogService;

    public RouteAdminController(RouteAdminService routeService, RouteCatalogService routeCatalogService) {
        this.routeService = routeService;
        this.routeCatalogService = routeCatalogService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("demoMode", false);
        try {
            model.addAttribute("routes", routeService.findAll());
        } catch (IllegalStateException e) {
            model.addAttribute("routes", AdminDemoData.routes());
            model.addAttribute("demoMode", true);
            model.addAttribute("loadError", "Không tải được dữ liệu tuyến từ MySQL: " + rootMessage(e));
        }
        model.addAttribute("pageTitle", "Tuyến xe");
        return "routes/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        addFormOptions(model);
        model.addAttribute("routeForm", new RouteForm());
        model.addAttribute("pageTitle", "Thêm tuyến");
        model.addAttribute("mode", "create");
        return "routes/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute RouteForm routeForm, BindingResult result, Model model) {
        if (result.hasErrors()) {
            addFormOptions(model);
            model.addAttribute("mode", "create");
            return "routes/form";
        }
        routeService.create(routeForm);
        return "redirect:/routes";
    }

    @GetMapping("/{documentId}/edit")
    public String editForm(@PathVariable String documentId, Model model) {
        RouteDto route = routeService.findByDocumentId(documentId);
        RouteForm form = new RouteForm();
        form.setOriginId(route.originId());
        form.setDestinationId(route.destinationId());
        form.setOrigin(route.origin());
        form.setDestination(route.destination());
        form.setDistance(route.distance());
        form.setSeatCount(route.seatCount());
        form.setSuggestedPrice(route.suggestedPrice());
        form.setIsActive(!Boolean.FALSE.equals(route.isActive()));
        addFormOptions(model);
        model.addAttribute("routeForm", form);
        model.addAttribute("documentId", documentId);
        model.addAttribute("mode", "edit");
        model.addAttribute("pageTitle", "Sửa tuyến");
        return "routes/form";
    }

    @PostMapping("/{documentId}")
    public String update(
            @PathVariable String documentId,
            @Valid @ModelAttribute RouteForm routeForm,
            BindingResult result,
            Model model
    ) {
        if (result.hasErrors()) {
            addFormOptions(model);
            model.addAttribute("mode", "edit");
            model.addAttribute("documentId", documentId);
            return "routes/form";
        }
        routeService.update(documentId, routeForm);
        return "redirect:/routes";
    }

    @PostMapping("/{documentId}/active/{active}")
    public String setActive(@PathVariable String documentId, @PathVariable boolean active) {
        routeService.setActive(documentId, active);
        return "redirect:/routes";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("provinces", routeCatalogService.provinces());
        model.addAttribute("catalogRoutes", routeCatalogService.routes());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }
}


