package com.example.busbooking.shared.service;

import com.example.busbooking.shared.model.ProvinceOption;
import com.example.busbooking.shared.model.RouteCatalogEntry;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RouteCatalogService {
    private static final long DEFAULT_PRICE = 200_000L;

    private final List<ProvinceOption> provinces = List.of(
            new ProvinceOption("ha_noi", "Hà Nội"),
            new ProvinceOption("ninh_binh", "Ninh Bình"),
            new ProvinceOption("thai_nguyen", "Thái Nguyên"),
            new ProvinceOption("hai_phong", "Hải Phòng"),
            new ProvinceOption("quang_ninh", "Quảng Ninh"),
            new ProvinceOption("thanh_hoa", "Thanh Hóa"),
            new ProvinceOption("nghe_an", "Nghệ An"),
            new ProvinceOption("ha_tinh", "Hà Tĩnh"),
            new ProvinceOption("quang_binh", "Quảng Bình"),
            new ProvinceOption("quang_tri", "Quảng Trị"),
            new ProvinceOption("thua_thien_hue", "Thừa Thiên Huế"),
            new ProvinceOption("da_nang", "Đà Nẵng"),
            new ProvinceOption("hoi_an", "Hội An"),
            new ProvinceOption("quang_ngai", "Quảng Ngãi"),
            new ProvinceOption("binh_dinh", "Bình Định"),
            new ProvinceOption("phu_yen", "Phú Yên"),
            new ProvinceOption("ninh_thuan", "Ninh Thuận"),
            new ProvinceOption("binh_thuan", "Bình Thuận"),
            new ProvinceOption("khanh_hoa", "Khánh Hòa"),
            new ProvinceOption("lam_dong", "Lâm Đồng"),
            new ProvinceOption("gia_lai", "Gia Lai"),
            new ProvinceOption("dak_lak", "Đắk Lắk"),
            new ProvinceOption("ho_chi_minh", "TP. Hồ Chí Minh"),
            new ProvinceOption("binh_duong", "Bình Dương"),
            new ProvinceOption("binh_phuoc", "Bình Phước"),
            new ProvinceOption("dong_nai", "Đồng Nai"),
            new ProvinceOption("ba_ria_vung_tau", "Bà Rịa - Vũng Tàu")
    );

    private final Map<String, ProvinceOption> provinceById = buildProvinceById();
    private final Map<String, String> provinceAliasByKey = buildProvinceAliasByKey();
    private final Map<String, Long> prices = buildPrices();
    private final List<RouteCatalogEntry> routes = buildRoutes();

    public List<ProvinceOption> provinces() {
        return provinces;
    }

    public List<RouteCatalogEntry> routes() {
        return routes;
    }

    public String provinceName(String provinceId) {
        ProvinceOption province = provinceById.get(text(provinceId));
        if (province == null) {
            Optional<String> canonicalId = canonicalProvinceId(provinceId);
            province = canonicalId.map(provinceById::get).orElse(null);
        }
        if (province == null) {
            throw new IllegalArgumentException("Không tìm thấy mã tỉnh: " + provinceId);
        }
        return province.name();
    }

    public Optional<String> provinceIdByName(String name) {
        return canonicalProvinceId(name);
    }

    public Optional<String> canonicalProvinceId(String idOrName) {
        String value = text(idOrName);
        if (value.isBlank()) {
            return Optional.empty();
        }
        String idKey = value.toLowerCase(Locale.ROOT);
        if (provinceById.containsKey(idKey)) {
            return Optional.of(idKey);
        }
        Optional<String> stopIdProvince = provinceIdByStopId(idKey);
        if (stopIdProvince.isPresent()) {
            return stopIdProvince;
        }
        return Optional.ofNullable(provinceAliasByKey.get(normalizeName(value)));
    }

    public ProvinceLocation canonicalLocation(String idOrName, String fallbackName) {
        Optional<String> canonicalId = canonicalProvinceId(idOrName);
        if (canonicalId.isEmpty()) {
            canonicalId = canonicalProvinceId(fallbackName);
        }
        if (canonicalId.isPresent()) {
            String id = canonicalId.get();
            return new ProvinceLocation(id, provinceName(id));
        }
        String retainedId = text(idOrName).isBlank() ? null : text(idOrName);
        String retainedName = text(fallbackName).isBlank() ? text(idOrName) : text(fallbackName);
        return new ProvinceLocation(retainedId, retainedName);
    }

    public boolean locationMatches(String candidate, String query) {
        String queryText = text(query);
        if (queryText.isBlank()) {
            return true;
        }
        String candidateKey = normalizeName(candidate);
        String queryKey = normalizeName(queryText);
        if (!candidateKey.isBlank() && candidateKey.contains(queryKey)) {
            return true;
        }
        return canonicalProvinceId(queryText)
                .map(this::provinceName)
                .map(this::normalizeName)
                .map(candidateKey::equals)
                .orElse(false);
    }

    public Optional<RouteCatalogEntry> findRoute(String originId, String destinationId) {
        String canonicalOriginId = canonicalProvinceId(originId).orElse(originId);
        String canonicalDestinationId = canonicalProvinceId(destinationId).orElse(destinationId);
        return routes.stream()
                .filter(route -> route.originId().equals(canonicalOriginId) && route.destinationId().equals(canonicalDestinationId))
                .findFirst();
    }

    public long price(String originId, String destinationId) {
        String canonicalOriginId = canonicalProvinceId(originId).orElse(originId);
        String canonicalDestinationId = canonicalProvinceId(destinationId).orElse(destinationId);
        return prices.getOrDefault(routeKey(canonicalOriginId, canonicalDestinationId), DEFAULT_PRICE);
    }

    private Map<String, ProvinceOption> buildProvinceById() {
        Map<String, ProvinceOption> result = new HashMap<>();
        provinces.forEach(province -> result.put(province.id(), province));
        return Map.copyOf(result);
    }

    private Map<String, String> buildProvinceAliasByKey() {
        Map<String, String> result = new HashMap<>();
        provinces.forEach(province -> addProvinceAliases(result, province.id(), province.id(), province.name()));
        addProvinceAliases(result, "ha_noi",
                "Bến xe Mỹ Đình", "Bến xe Giáp Bát", "Bến xe Nước Ngầm", "Bến xe Gia Lâm",
                "Bến xe Yên Nghĩa", "Bến xe Sơn Tây", "Bến xe Trôi", "Bến xe Phùng", "Bến xe Kim Mã");
        addProvinceAliases(result, "ninh_binh",
                "Bến xe Ninh Bình", "Bến xe Tam Điệp", "Bến xe Kim Sơn", "Bến xe Yên Khánh",
                "Bến xe Nho Quan", "Bến xe Gián Khẩu");
        addProvinceAliases(result, "thai_nguyen",
                "Bến xe Trung tâm Thái Nguyên", "Bến xe Sông Công", "Bến xe Đại Từ",
                "Bến xe Phổ Yên", "Bến xe Đồng Hỷ", "Bến xe Võ Nhai", "Bến xe Phú Bình", "Bến xe Phú Lương");
        addProvinceAliases(result, "hai_phong",
                "Bến xe Trung tâm Hải Phòng", "Bến xe Niệm Nghĩa", "Bến xe Vĩnh Niệm",
                "Bến xe Thượng Lý", "Bến xe Đồ Sơn", "Bến xe Cát Bà", "Bến xe Tiên Lãng", "Bến xe Vĩnh Bảo");
        addProvinceAliases(result, "quang_ninh",
                "Bến xe Bãi Cháy", "Bến xe Cửa Ông", "Bến xe Cẩm Phả", "Bến xe Móng Cái",
                "Bến xe Uông Bí", "Bến xe Đông Triều", "Bến xe Quảng Yên", "Bến xe Vân Đồn");
        addProvinceAliases(result, "thanh_hoa",
                "Bến xe phía Bắc Thanh Hóa", "Bến xe phía Nam Thanh Hóa", "Bến xe Bỉm Sơn",
                "Bến xe Sầm Sơn", "Bến xe Ngọc Lặc", "Bến xe Tĩnh Gia", "Bến xe Thọ Xuân");
        addProvinceAliases(result, "nghe_an",
                "Bến xe Vinh", "Bến xe Bắc Vinh", "Bến xe Cửa Lò", "Bến xe Diễn Châu",
                "Bến xe Thái Hòa", "Bến xe Quỳnh Lưu", "Bến xe Đô Lương");
        addProvinceAliases(result, "ha_tinh",
                "Bến xe Hà Tĩnh", "Bến xe Hồng Lĩnh", "Bến xe Kỳ Anh", "Bến xe Hương Sơn",
                "Bến xe Nghi Xuân", "Bến xe Can Lộc");
        addProvinceAliases(result, "da_nang",
                "Bến xe Trung tâm Đà Nẵng", "Đà Nẵng", "Da Nang", "Bến xe Đức Long");
        addProvinceAliases(result, "thua_thien_hue",
                "Huế", "Hue", "Bến xe phía Nam Huế", "Bến xe phía Bắc Huế", "Bến xe An Cựu",
                "Bến xe Phú Bài", "Bến xe Lăng Cô", "Bến xe A Lưới");
        addProvinceAliases(result, "quang_tri",
                "Bến xe Đông Hà", "Bến xe Quảng Trị", "Bến xe Lao Bảo", "Bến xe Gio Linh", "Bến xe Vĩnh Linh");
        addProvinceAliases(result, "quang_binh",
                "Bến xe Đồng Hới", "Bến xe Ba Đồn", "Bến xe Hoàn Lão", "Bến xe Lệ Thủy", "Bến xe Minh Hóa");
        addProvinceAliases(result, "hoi_an",
                "Bến xe Hội An", "Bến xe Điện Bàn", "Bến xe Cửa Đại", "Hội An - Tây Giang");
        addProvinceAliases(result, "quang_ngai",
                "Bến xe Quảng Ngãi", "Bến xe Đức Phổ", "Bến xe Sa Huỳnh", "Bến xe Lý Sơn", "Bến xe Bình Sơn");
        addProvinceAliases(result, "binh_dinh",
                "Bến xe Quy Nhơn", "Bến xe An Nhơn", "Bến xe Phù Mỹ", "Bến xe Tây Sơn",
                "Bến xe Hoài Nhơn", "Bến xe Bồng Sơn");
        addProvinceAliases(result, "phu_yen",
                "Bến xe Tuy Hòa", "Bến xe Sông Cầu", "Bến xe Tuy An", "Bến xe Đông Hòa", "Bến xe Sơn Hòa");
        addProvinceAliases(result, "ninh_thuan",
                "Bến xe Phan Rang", "Bến xe Ninh Sơn", "Bến xe Ninh Hải", "Bến xe Tháp Chàm", "Bến xe Cà Ná");
        addProvinceAliases(result, "binh_thuan",
                "Bến xe Phan Thiết", "Bến xe La Gi", "Bến xe Hàm Tân", "Bến xe Bắc Bình",
                "Bến xe Tuy Phong", "Bến xe Phan Rí", "Lagi");
        addProvinceAliases(result, "khanh_hoa",
                "Bến xe Nha Trang", "Bến xe phía Nam Nha Trang", "Bến xe Cam Ranh",
                "Bến xe Ninh Hòa", "Bến xe Vạn Giã");
        addProvinceAliases(result, "lam_dong",
                "dalat", "Đà Lạt", "Da Lat", "Bến xe Đà Lạt", "Bến xe Bảo Lộc",
                "Bến xe Đức Trọng", "Bến xe Di Linh", "Bến xe Đơn Dương", "Bến xe Liên Nghĩa", "Bến xe Lạc Dương");
        addProvinceAliases(result, "gia_lai",
                "Pleiku", "Bến xe Đức Long Pleiku", "Bến xe An Khê", "Bến xe Ayun Pa",
                "Bến xe Chư Sê", "Bến xe Chư Prông", "Bến xe Đắk Đoa", "Bến xe Mang Yang");
        addProvinceAliases(result, "dak_lak",
                "Buôn Ma Thuột", "Bến xe phía Bắc Buôn Ma Thuột", "Bến xe phía Nam Buôn Ma Thuột",
                "Bến xe Ea H'leo", "Bến xe Krông Năng", "Bến xe Buôn Hồ", "Bến xe Ea Kar", "Bến xe Krông Pắc");
        addProvinceAliases(result, "ho_chi_minh",
                "hcm", "tp_hcm", "tp_ho_chi_minh", "Thành phố Hồ Chí Minh", "TP. Ho Chi Minh",
                "Ho Chi Minh City", "Sài Gòn", "Sai Gon", "Saigon", "Bến xe Miền Đông",
                "Bến xe Miền Đông Mới", "Bến xe Miền Tây", "Bến xe Ngã Tư Ga",
                "Bến xe An Sương", "Bến xe Chợ Lớn", "Bến xe Quận 8", "Bến xe Sài Gòn");
        addProvinceAliases(result, "binh_duong",
                "Bến xe Thủ Dầu Một", "Bến xe Bến Cát", "Bến xe Dĩ An", "Bến xe Thuận An",
                "Bến xe Tân Uyên", "Bến xe Bàu Bàng");
        addProvinceAliases(result, "binh_phuoc",
                "Bến xe Đồng Xoài", "Bến xe Bù Đăng", "Bến xe Bù Đốp", "Bến xe Chơn Thành", "Bến xe Lộc Ninh");
        addProvinceAliases(result, "dong_nai",
                "Bến xe Biên Hòa", "Bến xe Trảng Bom", "Bến xe Long Khánh", "Bến xe Định Quán",
                "Bến xe Xuân Lộc", "Bến xe Nhơn Trạch");
        addProvinceAliases(result, "ba_ria_vung_tau",
                "brvt", "Bến xe Vũng Tàu", "Bến xe Bà Rịa", "Bến xe Long Hải",
                "Bến xe Xuyên Mộc", "Bến xe Côn Đảo");
        return Map.copyOf(result);
    }

    private Map<String, Long> buildPrices() {
        Map<String, Long> result = new HashMap<>();
        addPrice(result, "ha_noi", "ninh_binh", 150_000);
        addPrice(result, "ha_noi", "thai_nguyen", 120_000);
        addPrice(result, "ha_noi", "hai_phong", 180_000);
        addPrice(result, "ha_noi", "quang_ninh", 220_000);
        addPrice(result, "ha_noi", "thanh_hoa", 230_000);
        addPrice(result, "ha_noi", "nghe_an", 320_000);
        addPrice(result, "ha_noi", "da_nang", 650_000);
        addPrice(result, "ha_noi", "thua_thien_hue", 580_000);
        addPrice(result, "ha_noi", "lam_dong", 1_250_000);
        addPrice(result, "ha_noi", "ho_chi_minh", 1_400_000);
        addPrice(result, "da_nang", "hoi_an", 120_000);
        addPrice(result, "da_nang", "thua_thien_hue", 150_000);
        addPrice(result, "da_nang", "quang_tri", 220_000);
        addPrice(result, "da_nang", "quang_ngai", 220_000);
        addPrice(result, "da_nang", "binh_dinh", 350_000);
        addPrice(result, "da_nang", "khanh_hoa", 550_000);
        addPrice(result, "da_nang", "lam_dong", 650_000);
        addPrice(result, "da_nang", "gia_lai", 450_000);
        addPrice(result, "da_nang", "ho_chi_minh", 850_000);
        addPrice(result, "ho_chi_minh", "binh_duong", 120_000);
        addPrice(result, "ho_chi_minh", "dong_nai", 130_000);
        addPrice(result, "ho_chi_minh", "ba_ria_vung_tau", 180_000);
        addPrice(result, "ho_chi_minh", "binh_phuoc", 250_000);
        addPrice(result, "ho_chi_minh", "quang_tri", 950_000);
        addPrice(result, "ho_chi_minh", "lam_dong", 320_000);
        addPrice(result, "ho_chi_minh", "khanh_hoa", 500_000);
        addPrice(result, "ho_chi_minh", "binh_thuan", 280_000);
        addPrice(result, "khanh_hoa", "lam_dong", 250_000);
        addPrice(result, "khanh_hoa", "binh_dinh", 300_000);
        addPrice(result, "lam_dong", "dong_nai", 280_000);
        addPrice(result, "lam_dong", "dak_lak", 300_000);
        addPrice(result, "binh_dinh", "gia_lai", 220_000);
        addPrice(result, "nghe_an", "ha_tinh", 100_000);
        return Map.copyOf(result);
    }

    private List<RouteCatalogEntry> buildRoutes() {
        List<RouteCatalogEntry> oneWay = List.of(
                route("ha_noi", "da_nang", 765, hours(13)),
                route("thua_thien_hue", "ha_noi", 670, hours(12)),
                route("ha_noi", "lam_dong", 1450, hours(26)),
                route("ha_noi", "ho_chi_minh", 1700, hours(30)),
                route("da_nang", "lam_dong", 720, hours(12)),
                route("da_nang", "ho_chi_minh", 980, hours(16)),
                route("quang_tri", "ho_chi_minh", 1110, hours(20)),
                route("ho_chi_minh", "lam_dong", 300, hours(6)),
                route("da_nang", "hoi_an", 30, hours(1)),
                route("da_nang", "thua_thien_hue", 100, hours(2)),
                route("da_nang", "quang_ngai", 130, hours(2) + minutes(30)),
                route("da_nang", "binh_dinh", 300, hours(5)),
                route("da_nang", "khanh_hoa", 530, hours(9)),
                route("da_nang", "gia_lai", 200, hours(4)),
                route("da_nang", "quang_tri", 165, hours(3)),
                route("ha_noi", "hai_phong", 120, hours(2)),
                route("ha_noi", "ninh_binh", 95, hours(2)),
                route("ha_noi", "thanh_hoa", 160, hours(3)),
                route("ha_noi", "nghe_an", 300, hours(5)),
                route("ha_noi", "quang_ninh", 160, hours(3)),
                route("ho_chi_minh", "binh_duong", 30, hours(1)),
                route("ho_chi_minh", "dong_nai", 35, hours(1) + minutes(30)),
                route("ho_chi_minh", "ba_ria_vung_tau", 125, hours(2)),
                route("ho_chi_minh", "binh_phuoc", 120, hours(2) + minutes(30)),
                route("ho_chi_minh", "khanh_hoa", 440, hours(7)),
                route("ho_chi_minh", "binh_thuan", 200, hours(3) + minutes(30)),
                route("khanh_hoa", "lam_dong", 200, hours(4)),
                route("khanh_hoa", "binh_dinh", 240, hours(4)),
                route("lam_dong", "dong_nai", 200, hours(4)),
                route("lam_dong", "dak_lak", 200, hours(4)),
                route("binh_dinh", "gia_lai", 170, hours(3)),
                route("nghe_an", "ha_tinh", 50, hours(1))
        );

        List<RouteCatalogEntry> result = new ArrayList<>();
        oneWay.forEach(route -> {
            result.add(route);
            result.add(route(route.destinationId(), route.originId(), route.distanceKm(), route.durationMs()));
        });
        return List.copyOf(result);
    }

    private RouteCatalogEntry route(String originId, String destinationId, int distanceKm, long durationMs) {
        return new RouteCatalogEntry(
                originId,
                provinceName(originId),
                destinationId,
                provinceName(destinationId),
                distanceKm,
                durationMs,
                price(originId, destinationId)
        );
    }

    private void addPrice(Map<String, Long> target, String a, String b, long price) {
        target.put(routeKey(a, b), price);
        target.put(routeKey(b, a), price);
    }

    private String routeKey(String originId, String destinationId) {
        return originId + "->" + destinationId;
    }

    private Optional<String> provinceIdByStopId(String value) {
        if (!value.startsWith("stop_")) {
            return Optional.empty();
        }
        if (value.startsWith("stop_hcm_")) {
            return Optional.of("ho_chi_minh");
        }
        if (value.startsWith("stop_hue_")) {
            return Optional.of("thua_thien_hue");
        }
        if (value.startsWith("stop_brvt_")) {
            return Optional.of("ba_ria_vung_tau");
        }
        String withoutPrefix = value.substring("stop_".length());
        String best = null;
        for (ProvinceOption province : provinces) {
            String prefix = province.id() + "_";
            if (withoutPrefix.startsWith(prefix) && (best == null || province.id().length() > best.length())) {
                best = province.id();
            }
        }
        return Optional.ofNullable(best);
    }

    private void addProvinceAliases(Map<String, String> target, String provinceId, String... aliases) {
        for (String alias : aliases) {
            addAlias(target, provinceId, alias);
        }
    }

    private void addAlias(Map<String, String> target, String provinceId, String alias) {
        String key = normalizeName(alias);
        putAlias(target, key, provinceId);
        putAlias(target, stripStopPrefix(key), provinceId);
    }

    private void putAlias(Map<String, String> target, String key, String provinceId) {
        if (key.isBlank()) {
            return;
        }
        String previous = target.putIfAbsent(key, provinceId);
        if (previous != null && !previous.equals(provinceId)) {
            target.remove(key);
        }
    }

    private String stripStopPrefix(String normalized) {
        String value = normalized;
        String previous;
        do {
            previous = value;
            value = value.replaceFirst("^(ben xe|bx)\\s+", "");
            value = value.replaceFirst("^trung tam\\s+", "");
            value = value.replaceFirst("^phia\\s+(bac|nam)\\s+", "");
            value = value.replaceFirst("^(tp|thanh pho|tinh)\\s+", "");
        } while (!value.equals(previous));
        return value.trim();
    }

    private String normalizeName(String name) {
        String value = Normalizer.normalize(text(name).replace('_', ' '), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return value.replaceAll("\\s+", " ");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private long hours(long value) {
        return value * 3_600_000L;
    }

    private long minutes(long value) {
        return value * 60_000L;
    }

    public record ProvinceLocation(String id, String name) {
    }
}
