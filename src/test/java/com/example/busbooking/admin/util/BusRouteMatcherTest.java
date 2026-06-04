package com.example.busbooking.admin.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusRouteMatcherTest {
    @Test
    void busMustBelongToRouteEndpoint() {
        assertThat(BusRouteMatcher.canServeRoute(
                BusRouteMatcher.serviceProvinceId("Da Nang Sai Gon 34", "43E-2401"),
                "da_nang",
                "ho_chi_minh"
        )).isTrue();

        assertThat(BusRouteMatcher.canServeRoute(
                BusRouteMatcher.serviceProvinceId("Nha Trang Limousine 34", "79B-13579"),
                "da_nang",
                "ho_chi_minh"
        )).isFalse();
    }
}
