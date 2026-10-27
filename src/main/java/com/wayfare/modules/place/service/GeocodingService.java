package com.wayfare.modules.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Dịch vụ Geocoding tự động chuyển đổi địa chỉ thành tọa độ GPS (Latitude / Longitude)
 * Đảm bảo 100% địa điểm sau khi tạo hoặc được Admin duyệt luôn có tọa độ để cắm ghim trên bản đồ.
 */
@Slf4j
@Service
public class GeocodingService {

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Bản đồ tọa độ trung tâm các tỉnh thành phố phổ biến tại Việt Nam làm fallback chuẩn xác
    private static final Map<String, Coordinates> CITY_COORDINATES = new HashMap<>();

    static {
        registerCity("đà lạt", "11.9404192", "108.4583132");
        registerCity("lâm đồng", "11.9404192", "108.4583132");
        registerCity("đà nẵng", "16.0544068", "108.2021667");
        registerCity("hà nội", "21.0285110", "105.8541670");
        registerCity("hồ chí minh", "10.8230989", "106.6296638");
        registerCity("sài gòn", "10.8230989", "106.6296638");
        registerCity("hà giang", "22.8233330", "104.9836110");
        registerCity("sa pa", "22.3363600", "103.8437900");
        registerCity("sapa", "22.3363600", "103.8437900");
        registerCity("lào cai", "22.4855560", "103.9708330");
        registerCity("hội an", "15.8800584", "108.3380469");
        registerCity("quảng nam", "15.5393860", "108.0194440");
        registerCity("phú quốc", "10.2898790", "103.9840200");
        registerCity("kiên giang", "10.0125000", "105.0808330");
        registerCity("ninh bình", "20.2506149", "105.9744536");
        registerCity("nha trang", "12.2387911", "109.1967488");
        registerCity("khánh hòa", "12.2500000", "109.1833330");
        registerCity("quy nhơn", "13.7829670", "109.2196630");
        registerCity("bình định", "14.1666670", "109.0000000");
        registerCity("huế", "16.4637130", "107.5908660");
        registerCity("thừa thiên huế", "16.4637130", "107.5908660");
        registerCity("quảng bình", "17.4690449", "106.6225574");
        registerCity("phú yên", "13.0881958", "109.3149537");
        registerCity("tuy hòa", "13.0881958", "109.3149537");
        registerCity("hạ long", "20.9504537", "107.0733642");
        registerCity("quảng ninh", "21.2500000", "107.3333330");
        registerCity("hải phòng", "20.8449115", "106.6880841");
        registerCity("vũng tàu", "10.3459897", "107.0842602");
        registerCity("bà rịa", "10.4962770", "107.1687440");
        registerCity("cần thơ", "10.0451618", "105.7468535");
        registerCity("buôn ma thuột", "12.6661942", "108.0382570");
        registerCity("đắk lắk", "12.8333330", "108.3333330");
        registerCity("mộc châu", "20.8436000", "104.6853000");
        registerCity("sơn la", "21.3283330", "103.9166670");
        registerCity("yên bái", "21.7229000", "104.9113000");
        registerCity("cao bằng", "22.6666670", "106.2500000");
        registerCity("măng đen", "14.6000000", "108.3000000");
        registerCity("kon tum", "14.3500000", "108.0000000");
        registerCity("gia lai", "13.9833330", "108.0000000");
        registerCity("pleiku", "13.9833330", "108.0000000");
        registerCity("an giang", "10.5215830", "105.1258950");
        registerCity("đồng tháp", "10.5964400", "105.6802600");
        registerCity("tiền giang", "10.4493300", "106.3421500");
        registerCity("bến tre", "10.2433300", "106.3755600");
        registerCity("bình thuận", "11.1041670", "108.1866670");
        registerCity("phan thiết", "10.9333330", "108.1000000");
        registerCity("ninh thuận", "11.7500000", "108.8333330");
        registerCity("phan rang", "11.5666670", "108.9833330");
    }

    private static void registerCity(String name, String lat, String lon) {
        CITY_COORDINATES.put(name.toLowerCase(), new Coordinates(new BigDecimal(lat), new BigDecimal(lon)));
    }

    public GeocodingService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Tự động lấy tọa độ GPS từ chuỗi địa chỉ và tên thành phố.
     * Quy trình 3 bước:
     * 1. Gọi Nominatim OpenStreetMap API với địa chỉ đầy đủ.
     * 2. Nếu không tìm thấy, gọi Nominatim với Quận/Huyện/Thành phố.
     * 3. Nếu vẫn không thấy hoặc timeout, fallback về bảng tọa độ trung tâm tỉnh thành phố.
     */
    public Coordinates geocode(String address, String city) {
        log.info("Geocoding request for address: '{}', city: '{}'", address, city);

        // Chuẩn bị query
        String cleanAddress = cleanAddressString(address);
        String cleanCity = (city != null && !city.isBlank()) ? city.trim() : "";

        // Thử bước 1: Địa chỉ đầy đủ + Thành phố + Việt Nam
        String fullQuery = cleanAddress;
        if (!cleanCity.isBlank() && !cleanAddress.toLowerCase().contains(cleanCity.toLowerCase())) {
            fullQuery += ", " + cleanCity;
        }
        if (!fullQuery.toLowerCase().contains("việt nam") && !fullQuery.toLowerCase().contains("vietnam")) {
            fullQuery += ", Việt Nam";
        }

        Coordinates coords = queryNominatim(fullQuery);
        if (coords != null) {
            log.info("Geocoded successfully via Nominatim full query: '{}' -> {}, {}", fullQuery, coords.latitude(), coords.longitude());
            return coords;
        }

        // Thử bước 2: Nếu địa chỉ có nhiều cấp (phường, quận), tách lấy phần quận/huyện + tỉnh thành
        if (cleanAddress.contains(",")) {
            String[] parts = cleanAddress.split(",");
            if (parts.length >= 2) {
                String broadQuery = parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim() + ", Việt Nam";
                coords = queryNominatim(broadQuery);
                if (coords != null) {
                    log.info("Geocoded successfully via relaxed query: '{}' -> {}, {}", broadQuery, coords.latitude(), coords.longitude());
                    return coords;
                }
            }
        }

        // Thử bước 3: Nominatim theo thành phố
        if (!cleanCity.isBlank()) {
            coords = queryNominatim(cleanCity + ", Việt Nam");
            if (coords != null) {
                log.info("Geocoded successfully via city query: '{}' -> {}, {}", cleanCity, coords.latitude(), coords.longitude());
                return coords;
            }
        }

        // Thử bước 4: Fallback bảng tọa độ địa phương tích hợp sẵn
        coords = getFallbackFromCity(cleanCity, cleanAddress);
        if (coords != null) {
            log.info("Used local city coordinate fallback for city '{}' -> {}, {}", cleanCity, coords.latitude(), coords.longitude());
            return coords;
        }

        // Nếu tất cả đều không khớp, đặt tọa độ trung tâm Việt Nam (Đà Nẵng) để luôn hiển thị
        log.warn("All geocoding attempts failed for address '{}', city '{}'. Falling back to default center.", address, city);
        return new Coordinates(new BigDecimal("16.0544068"), new BigDecimal("108.2021667"));
    }

    private Coordinates queryNominatim(String query) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .queryParam("countrycodes", "vn")
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "WayfareTravelPlatform/1.0 (contact@wayfare.vn)");
            headers.set("Accept-Language", "vi,en;q=0.9");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode first = root.get(0);
                    String latStr = first.path("lat").asText(null);
                    String lonStr = first.path("lon").asText(null);
                    if (latStr != null && lonStr != null) {
                        BigDecimal lat = new BigDecimal(latStr).setScale(7, RoundingMode.HALF_UP);
                        BigDecimal lon = new BigDecimal(lonStr).setScale(7, RoundingMode.HALF_UP);
                        return new Coordinates(lat, lon);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Nominatim query failed for '{}': {}", query, e.getMessage());
        }
        return null;
    }

    private Coordinates getFallbackFromCity(String city, String address) {
        String cityNorm = normalizeText(city);
        for (Map.Entry<String, Coordinates> entry : CITY_COORDINATES.entrySet()) {
            if (cityNorm.contains(entry.getKey()) || entry.getKey().contains(cityNorm)) {
                return entry.getValue();
            }
        }

        // Kiểm tra trong address xem có tên thành phố nào không
        String addressNorm = normalizeText(address);
        for (Map.Entry<String, Coordinates> entry : CITY_COORDINATES.entrySet()) {
            if (addressNorm.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String cleanAddressString(String raw) {
        if (raw == null) return "";
        // Chuyển dấu gạch ngang '-' hoặc gạch chéo '/' thành dấu phẩy ',' để Nominatim dễ phân tích
        return raw.replace("-", ", ").replace("/", ", ").replaceAll("\\s+", " ").trim();
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        return input.toLowerCase().trim();
    }
}
