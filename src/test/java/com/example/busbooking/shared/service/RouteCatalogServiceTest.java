package com.example.busbooking.shared.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RouteCatalogServiceTest {
    private final RouteCatalogService routeCatalogService = new RouteCatalogService();

    @ParameterizedTest
    @CsvSource({
            "Bến xe Miền Đông, ho_chi_minh, TP. Hồ Chí Minh",
            "TP. Ho Chi Minh, ho_chi_minh, TP. Hồ Chí Minh",
            "Da Lat, lam_dong, Lâm Đồng",
            "Bến xe Đà Lạt, lam_dong, Lâm Đồng",
            "Nha Trang, khanh_hoa, Khánh Hòa",
            "Bến xe Quy Nhơn, binh_dinh, Bình Định",
            "Buôn Ma Thuột, dak_lak, Đắk Lắk",
            "stop_hcm_mien_dong_cu, ho_chi_minh, TP. Hồ Chí Minh",
            "stop_lam_dong_da_lat, lam_dong, Lâm Đồng"
    })
    void canonicalLocationUsesParentProvince(String value, String expectedId, String expectedName) {
        RouteCatalogService.ProvinceLocation location = routeCatalogService.canonicalLocation(value, value);

        assertThat(location.id()).isEqualTo(expectedId);
        assertThat(location.name()).isEqualTo(expectedName);
    }
}
