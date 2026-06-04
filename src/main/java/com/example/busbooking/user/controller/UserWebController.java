package com.example.busbooking.user.controller;

import com.example.busbooking.shared.payment.VnpayCreatePaymentResponse;
import com.example.busbooking.user.model.UserWebModels.CheckoutResult;
import com.example.busbooking.user.model.UserWebModels.PaymentView;
import com.example.busbooking.user.model.UserWebModels.TicketView;
import com.example.busbooking.user.model.UserWebModels.TripView;
import com.example.busbooking.user.model.UserWebModels.UserSession;
import com.example.busbooking.user.service.UserAuthService;
import com.example.busbooking.user.service.UserAuthService.AuthUser;
import com.example.busbooking.user.service.UserPaymentService;
import com.example.busbooking.user.service.UserWebService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
public class UserWebController {
    private static final String WEB_USER_ID = "WEB_USER_ID";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final UserAuthService userAuthService;
    private final UserWebService userWebService;
    private final UserPaymentService userPaymentService;

    public UserWebController(UserAuthService userAuthService, UserWebService userWebService, UserPaymentService userPaymentService) {
        this.userAuthService = userAuthService;
        this.userWebService = userWebService;
        this.userPaymentService = userPaymentService;
    }

    @ModelAttribute("webUser")
    public UserSession webUser(HttpSession session) {
        return currentUser(session);
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/user/home";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        return currentUser(session) == null ? "user/login" : "redirect:/user/home";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String phone,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AuthUser user = userAuthService.login(phone, password);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Số điện thoại hoặc mật khẩu không hợp lệ");
            return "redirect:/user/login";
        }
        session.setAttribute(WEB_USER_ID, user.id());
        return "redirect:/user/home";
    }

    @GetMapping("/register")
    public String registerPage(HttpSession session) {
        return currentUser(session) == null ? "user/register" : "redirect:/user/home";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(email) || !StringUtils.hasText(phone) || !StringUtils.hasText(password)) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập đầy đủ thông tin");
            return "redirect:/user/register";
        }
        if (password.trim().length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu phải có ít nhất 6 ký tự");
            return "redirect:/user/register";
        }
        try {
            AuthUser user = userAuthService.register(name, email, phone, password);
            session.setAttribute(WEB_USER_ID, user.id());
            return "redirect:/user/home";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/user/register";
        } catch (DuplicateKeyException e) {
            redirectAttributes.addFlashAttribute("error", "Email hoặc số điện thoại đã tồn tại");
            return "redirect:/user/register";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(WEB_USER_ID);
        return "redirect:/user/login";
    }

    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        LocalDate searchDate = userWebService.defaultSearchDate();
        model.addAttribute("pageTitle", "Trang chu");
        model.addAttribute("origins", userWebService.origins());
        model.addAttribute("destinations", userWebService.destinations());
        model.addAttribute("searchDate", searchDate);
        model.addAttribute("searchDateLabel", displayDate(searchDate));
        model.addAttribute("routeCards", userWebService.routeCards().stream().limit(4).toList());
        model.addAttribute("trips", userWebService.upcomingTrips(6));
        UserSession user = currentUser(session);
        model.addAttribute("upcomingTickets", user == null ? List.of() : userWebService.upcomingTicketsForUser(user.id(), 3));
        return "user/home";
    }

    @GetMapping("/routes")
    public String routes(Model model) {
        model.addAttribute("pageTitle", "Tuyen xe");
        model.addAttribute("routeCards", userWebService.routeCards());
        return "user/routes";
    }

    @GetMapping("/trips")
    public String trips(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tripDate,
            @RequestParam(required = false) Integer totalSeats,
            Model model
    ) {
        LocalDate date = tripDate == null ? userWebService.defaultSearchDate() : tripDate;
        List<TripView> trips = StringUtils.hasText(origin) && StringUtils.hasText(destination)
                ? userWebService.searchTrips(origin, destination, date, totalSeats)
                : userWebService.upcomingTrips(30);
        model.addAttribute("pageTitle", "Chuyen xe");
        model.addAttribute("origins", userWebService.origins());
        model.addAttribute("destinations", userWebService.destinations());
        model.addAttribute("origin", origin);
        model.addAttribute("destination", destination);
        model.addAttribute("tripDate", date);
        model.addAttribute("tripDateLabel", displayDate(date));
        model.addAttribute("totalSeats", totalSeats);
        model.addAttribute("trips", trips);
        return "user/trips";
    }

    @GetMapping("/trips/{tripId}")
    public String tripDetails(@PathVariable long tripId, Model model) {
        TripView trip = userWebService.findTrip(tripId);
        if (trip == null) {
            return "redirect:/user/trips";
        }
        model.addAttribute("pageTitle", "Chi tiet chuyen");
        model.addAttribute("trip", trip);
        return "user/trip-detail";
    }

    @GetMapping("/trips/{tripId}/seats")
    public String seats(@PathVariable long tripId, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để chọn ghế");
            return "redirect:/user/login";
        }
        TripView trip = userWebService.findTrip(tripId);
        if (trip == null) {
            return "redirect:/user/trips";
        }
        model.addAttribute("pageTitle", "Chon ghe");
        model.addAttribute("trip", trip);
        model.addAttribute("seats", userWebService.seatsForTrip(tripId));
        return "user/seats";
    }

    @PostMapping("/trips/{tripId}/seats/book")
    public String bookSeats(
            @PathVariable long tripId,
            @RequestParam(required = false) List<Long> seatIds,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để đặt vé");
            return "redirect:/user/login";
        }
        try {
            CheckoutResult checkout = userWebService.bookSeats(user.id(), tripId, seatIds);
            return "redirect:/user/payments/" + checkout.paymentId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/user/trips/" + tripId + "/seats";
        }
    }

    @GetMapping("/payments/{paymentId}")
    public String payment(
            @PathVariable String paymentId,
            Model model,
            HttpSession session,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để thanh toán");
            return "redirect:/user/login";
        }
        PaymentView payment = userWebService.paymentForUser(paymentId, user.id());
        if (payment == null) {
            return "redirect:/user/tickets";
        }
        if ("CREATED".equalsIgnoreCase(payment.status()) || "PENDING".equalsIgnoreCase(payment.status())) {
            try {
                VnpayCreatePaymentResponse response = userPaymentService.createPaymentQr(paymentId, request);
                model.addAttribute("vnpayResponse", response);
            } catch (RuntimeException e) {
                model.addAttribute("error", e.getMessage());
            }
            payment = userWebService.paymentForUser(paymentId, user.id());
        }
        model.addAttribute("pageTitle", "Thanh toan");
        model.addAttribute("payment", payment);
        return "user/payment";
    }

    @GetMapping("/payments/{paymentId}/status")
    @ResponseBody
    public Map<String, Object> paymentStatus(
            @PathVariable String paymentId,
            HttpSession session
    ) {
        UserSession user = currentUser(session);
        if (user == null) {
            return Map.of("authenticated", false, "found", false, "status", "UNAUTHORIZED", "terminal", true, "paid", false);
        }
        PaymentView payment = userWebService.paymentForUser(paymentId, user.id());
        if (payment == null) {
            return Map.of("authenticated", true, "found", false, "status", "NOT_FOUND", "terminal", true, "paid", false);
        }
        String status = payment.status() == null ? "" : payment.status();
        boolean paid = "SUCCESS".equalsIgnoreCase(status);
        boolean terminal = paid
                || "FAILED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status)
                || "EXPIRED".equalsIgnoreCase(status);
        return Map.of("authenticated", true, "found", true, "status", status, "terminal", terminal, "paid", paid);
    }

    @PostMapping("/payments/{paymentId}/cancel")
    public String cancelPayment(
            @PathVariable String paymentId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập");
            return "redirect:/user/login";
        }
        PaymentView payment = userWebService.paymentForUser(paymentId, user.id());
        if (payment == null || !payment.cancellable()) {
            redirectAttributes.addFlashAttribute("error", "Giao dịch không thể hủy");
            return "redirect:/user/tickets";
        }
        try {
            userPaymentService.cancelPayment(paymentId);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/user/tickets";
    }

    @GetMapping("/tickets")
    public String tickets(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để xem vé");
            return "redirect:/user/login";
        }
        model.addAttribute("pageTitle", "Ve cua toi");
        model.addAttribute("tickets", userWebService.ticketsForUser(user.id()));
        return "user/tickets";
    }

    @GetMapping("/tickets/{ticketId}")
    public String ticketDetails(@PathVariable long ticketId, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập");
            return "redirect:/user/login";
        }
        TicketView ticket = userWebService.ticketForUser(ticketId, user.id());
        if (ticket == null) {
            return "redirect:/user/tickets";
        }
        model.addAttribute("pageTitle", "Chi tiet ve");
        model.addAttribute("ticket", ticket);
        return "user/ticket-detail";
    }

    @PostMapping("/tickets/{ticketId}/cancel")
    public String cancelTicket(@PathVariable long ticketId, HttpSession session, RedirectAttributes redirectAttributes) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập");
            return "redirect:/user/login";
        }
        try {
            userWebService.cancelTicket(ticketId, user.id());
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/user/tickets/" + ticketId;
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập");
            return "redirect:/user/login";
        }
        model.addAttribute("pageTitle", "Tai khoan");
        model.addAttribute("profile", user);
        return "user/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        UserSession user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập");
            return "redirect:/user/login";
        }
        try {
            userAuthService.updateUser(user.id(), name, email, phone);
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật thông tin");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (DuplicateKeyException e) {
            redirectAttributes.addFlashAttribute("error", "Email hoặc số điện thoại đã được sử dụng");
        }
        return "redirect:/user/profile";
    }

    private UserSession currentUser(HttpSession session) {
        Object userId = session.getAttribute(WEB_USER_ID);
        if (userId instanceof Number number) {
            AuthUser user = userAuthService.findUserById(number.longValue());
            return user == null ? null : user.toSession();
        }
        return null;
    }

    private String displayDate(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }
}
