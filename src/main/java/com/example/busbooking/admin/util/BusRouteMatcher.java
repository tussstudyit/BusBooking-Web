package com.example.busbooking.admin.util;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BusRouteMatcher {
    private static final Map<String, String> PROVINCE_BY_KEYWORD = new LinkedHashMap<>();
    private static final Map<String, String> PROVINCE_BY_PLATE_PREFIX = Map.of(
            "29", "ha_noi",
            "30", "ha_noi",
            "43", "da_nang",
            "49", "lam_dong",
            "51", "ho_chi_minh",
            "74", "quang_tri",
            "75", "thua_thien_hue",
            "79", "khanh_hoa"
    );

    static {
        PROVINCE_BY_KEYWORD.put("ha noi", "ha_noi");
        PROVINCE_BY_KEYWORD.put("da nang", "da_nang");
        PROVINCE_BY_KEYWORD.put("lam dong", "lam_dong");
        PROVINCE_BY_KEYWORD.put("sai gon", "ho_chi_minh");
        PROVINCE_BY_KEYWORD.put("ho chi minh", "ho_chi_minh");
        PROVINCE_BY_KEYWORD.put("nha trang", "khanh_hoa");
        PROVINCE_BY_KEYWORD.put("khanh hoa", "khanh_hoa");
        PROVINCE_BY_KEYWORD.put("quang tri", "quang_tri");
        PROVINCE_BY_KEYWORD.put("hue", "thua_thien_hue");
        PROVINCE_BY_KEYWORD.put("thua thien hue", "thua_thien_hue");
        PROVINCE_BY_KEYWORD.put("gia lai", "gia_lai");
        PROVINCE_BY_KEYWORD.put("dak lak", "dak_lak");
        PROVINCE_BY_KEYWORD.put("binh dinh", "binh_dinh");
        PROVINCE_BY_KEYWORD.put("quang ngai", "quang_ngai");
        PROVINCE_BY_KEYWORD.put("binh thuan", "binh_thuan");
        PROVINCE_BY_KEYWORD.put("binh duong", "binh_duong");
        PROVINCE_BY_KEYWORD.put("dong nai", "dong_nai");
    }

    private BusRouteMatcher() {
    }

    public static String serviceProvinceId(String busName, String licensePlate) {
        String normalizedName = normalize(busName);
        for (Map.Entry<String, String> entry : PROVINCE_BY_KEYWORD.entrySet()) {
            if (normalizedName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        String plate = text(licensePlate).replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (plate.length() >= 2) {
            return PROVINCE_BY_PLATE_PREFIX.getOrDefault(plate.substring(0, 2), "");
        }
        return "";
    }

    public static boolean canServeRoute(String busProvinceId, String originId, String destinationId) {
        String province = text(busProvinceId);
        return !province.isBlank() && (province.equals(text(originId)) || province.equals(text(destinationId)));
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(text(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
