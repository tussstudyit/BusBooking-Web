package com.example.busbooking.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.busbooking.user.model.UserWebModels.RouteCard;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserWebServiceRouteCardTest {
    @Test
    void collapseBidirectionalRouteCardsGroupsDirectionsAndSeatCounts() {
        List<RouteCard> collapsed = UserWebService.collapseBidirectionalRouteCards(List.of(
                new RouteCard(1L, "TP. Hồ Chí Minh", "Lâm Đồng", 300, 34, BigDecimal.valueOf(320_000), 1_000L, 2),
                new RouteCard(2L, "Lâm Đồng", "TP. Hồ Chí Minh", 300, 34, BigDecimal.valueOf(300_000), 900L, 3),
                new RouteCard(3L, "Lâm Đồng", "TP. Hồ Chí Minh", 300, 24, BigDecimal.valueOf(420_000), 1_100L, 1),
                new RouteCard(4L, "Đà Nẵng", "Hà Nội", 700, 34, BigDecimal.valueOf(500_000), 1_200L, 4)
        ));

        assertEquals(2, collapsed.size());

        RouteCard lamDongHcm = collapsed.stream()
                .filter(route -> route.origin().contains("Lâm Đồng") || route.destination().contains("Lâm Đồng"))
                .findFirst()
                .orElseThrow();

        assertEquals(6, lamDongHcm.tripCount());
        assertEquals(BigDecimal.valueOf(300_000), lamDongHcm.suggestedPrice());
        assertEquals(900L, lamDongHcm.nextDepartureTime());
        assertEquals(2, lamDongHcm.seatOptions().size());
        assertTrue(lamDongHcm.seatOptions().stream().anyMatch(option -> option.seatCount() == 34 && option.tripCount() == 5));
        assertTrue(lamDongHcm.seatOptions().stream().anyMatch(option -> option.seatCount() == 24 && option.tripCount() == 1));
    }
}
