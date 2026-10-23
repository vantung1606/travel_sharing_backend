package com.wayfare.modules.geo.service;

import com.wayfare.entity.Province;
import com.wayfare.entity.User;
import com.wayfare.entity.UserProvinceVisit;
import com.wayfare.modules.geo.dto.ProvinceDto;
import com.wayfare.repository.ProvinceRepository;
import com.wayfare.repository.UserProvinceVisitRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProvinceService {

    private final ProvinceRepository provinceRepository;
    private final UserProvinceVisitRepository userProvinceVisitRepository;
    private final UserRepository userRepository;

    /** Danh mục tỉnh thành (dữ liệu thật trong bảng provinces — seed 34 đơn vị 2025). */
    @Transactional(readOnly = true)
    public List<ProvinceDto> listProvinces() {
        return provinceRepository.findAllByOrderByRegionAscNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    /** Các tỉnh mà user đã đánh dấu "đã đặt chân tới" — lưu trong bảng user_provinces_visited. */
    @Transactional
    public List<String> getVisitedProvinces(String email) {
        User user = requireUser(email);
        return userProvinceVisitRepository.findByUserIdOrderByVisitedAtDesc(user.getId()).stream()
                .map(UserProvinceVisit::getProvinceCode)
                .toList();
    }

    /** Bật/tắt dấu chân 1 tỉnh; cập nhật cả thời gian check-in. */
    @Transactional
    public List<String> setVisitedProvince(String email, String provinceCode, boolean visited) {
        User user = requireUser(email);
        Province province = provinceRepository.findByCode(provinceCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tồn tại mã tỉnh: " + provinceCode));

        if (visited) {
            if (userProvinceVisitRepository.findByUserIdAndProvinceCode(user.getId(), province.getCode()).isEmpty()) {
                UserProvinceVisit visit = UserProvinceVisit.builder()
                        .user(user)
                        .provinceCode(province.getCode())
                        .build();
                userProvinceVisitRepository.save(visit);
            }
        } else {
            userProvinceVisitRepository.deleteByUserIdAndProvinceCode(user.getId(), province.getCode());
        }

        log.info("User '{}' {} dấu chân tỉnh {} ({})", user.getEmail(),
                visited ? "thêm" : "gỡ", province.getName(), province.getCode());
        return getVisitedProvinces(email);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không xác định được người dùng: " + email));
    }

    private ProvinceDto toDto(Province p) {
        return ProvinceDto.builder()
                .code(p.getCode())
                .name(p.getName())
                .region(p.getRegion())
                .aliases(Arrays.asList(p.getAliases().split("\\s*,\\s*")))
                .build();
    }
}
