package com.example.busbooking.admin.controller;

import com.example.busbooking.admin.model.TripDto;
import com.example.busbooking.admin.model.TripForm;
import com.example.busbooking.admin.service.AdminDemoData;
import com.example.busbooking.admin.service.BusAdminService;
import com.example.busbooking.admin.service.RouteAdminService;
import com.example.busbooking.admin.service.TripAdminService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/trips")
public class TripAdminController {
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL_DATE_LABEL = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final TripAdminService tripService;
    private final RouteAdminService routeService;
    private final BusAdminService busService;

    public TripAdminController(TripAdminService tripService, RouteAdminService routeService, BusAdminService busService) {
        this.tripService = tripService;
        this.routeService = routeService;
        this.busService = busService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model
    ) {
        LocalDate selectedDate = tripService.normalizeAdminDate(date);
        model.addAttribute("demoMode", false);
        try {
            model.addAttribute("trips", tripService.findByDate(selectedDate));
        } catch (IllegalStateException e) {
            model.addAttribute("trips", AdminDemoData.trips());
            model.addAttribute("demoMode", true);
            model.addAttribute("loadError", "Khong tai duoc du lieu chuyen tu MySQL: " + rootMessage(e));
        }
        addDatePaging(model, selectedDate);
        model.addAttribute("pageTitle", "Chuyen xe");
        return "trips/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        addFormLists(model);
        model.addAttribute("tripForm", new TripForm());
        model.addAttribute("mode", "create");
        model.addAttribute("pageTitle", "Them chuyen");
        return "trips/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute TripForm tripForm, BindingResult result, Model model) {
        if (result.hasErrors()) {
            addFormLists(model);
            model.addAttribute("mode", "create");
            return "trips/form";
        }
        try {
            tripService.create(tripForm);
            return "redirect:/trips";
        } catch (IllegalArgumentException e) {
            addFormLists(model);
            model.addAttribute("mode", "create");
            model.addAttribute("saveError", e.getMessage());
            return "trips/form";
        }
    }

    @GetMapping("/{documentId}/edit")
    public String editForm(@PathVariable String documentId, Model model) {
        TripDto trip = tripService.findByDocumentId(documentId);
        TripForm form = new TripForm();
        form.setRouteId(trip.routeId());
        form.setBusId(trip.busId());
        form.setDepartureTime(trip.departureTime());
        form.setArrivalTime(trip.arrivalTime());
        form.setPrice(trip.price());
        form.setTripDate(trip.tripDate());
        form.setStatus(trip.status());
        form.setStaffId(tripService.findAssignedStaffId(documentId));
        addFormLists(model);
        model.addAttribute("trip", trip);
        model.addAttribute("seatViews", tripService.findSeatViews(documentId));
        model.addAttribute("tripForm", form);
        model.addAttribute("documentId", documentId);
        model.addAttribute("mode", "edit");
        model.addAttribute("pageTitle", "Sua chuyen");
        return "trips/form";
    }

    @PostMapping("/{documentId}")
    public String update(@PathVariable String documentId, @Valid @ModelAttribute TripForm tripForm, BindingResult result, Model model) {
        if (result.hasErrors()) {
            addEditContext(model, documentId, tripForm);
            model.addAttribute("mode", "edit");
            model.addAttribute("documentId", documentId);
            return "trips/form";
        }
        try {
            tripService.update(documentId, tripForm);
            return "redirect:/trips";
        } catch (IllegalArgumentException e) {
            addEditContext(model, documentId, tripForm);
            model.addAttribute("mode", "edit");
            model.addAttribute("documentId", documentId);
            model.addAttribute("saveError", e.getMessage());
            return "trips/form";
        }
    }

    @PostMapping("/{documentId}/cancel")
    public String cancel(@PathVariable String documentId) {
        tripService.cancel(documentId);
        return "redirect:/trips";
    }

    private void addDatePaging(Model model, LocalDate selectedDate) {
        LocalDate today = tripService.today();
        LocalDate last = tripService.lastVisibleDate();
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedDateLabel", selectedDate.format(FULL_DATE_LABEL));
        model.addAttribute("dateOptions", dateOptions(today, last, selectedDate));
        model.addAttribute("previousDate", selectedDate.isAfter(today) ? selectedDate.minusDays(1) : null);
        model.addAttribute("nextDate", selectedDate.isBefore(last) ? selectedDate.plusDays(1) : null);
    }

    private List<TripDateOption> dateOptions(LocalDate today, LocalDate last, LocalDate selectedDate) {
        List<TripDateOption> options = new ArrayList<>();
        LocalDate cursor = today;
        while (!cursor.isAfter(last)) {
            options.add(new TripDateOption(cursor, cursor.format(DAY_LABEL), cursor.equals(selectedDate)));
            cursor = cursor.plusDays(1);
        }
        return options;
    }

    private void addEditContext(Model model, String documentId, TripForm tripForm) {
        TripDto trip = tripService.findByDocumentId(documentId);
        addFormLists(model);
        model.addAttribute("trip", trip);
        model.addAttribute("seatViews", tripService.findSeatViews(documentId));
        model.addAttribute("tripForm", tripForm);
    }

    private void addFormLists(Model model) {
        model.addAttribute("routes", routeService.findAll());
        model.addAttribute("buses", busService.findTripOptions());
        model.addAttribute("staffOptions", tripService.findStaffOptions());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }

    public record TripDateOption(LocalDate date, String label, boolean selected) {}
}
