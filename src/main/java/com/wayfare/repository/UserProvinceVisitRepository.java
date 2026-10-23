package com.wayfare.repository;

import com.wayfare.entity.UserProvinceVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProvinceVisitRepository extends JpaRepository<UserProvinceVisit, Long> {

    List<UserProvinceVisit> findByUserIdOrderByVisitedAtDesc(Long userId);

    Optional<UserProvinceVisit> findByUserIdAndProvinceCode(Long userId, String provinceCode);

    long deleteByUserIdAndProvinceCode(Long userId, String provinceCode);

    long countByProvinceCode(String provinceCode);
}
