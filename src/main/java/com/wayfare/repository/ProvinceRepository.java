package com.wayfare.repository;

import com.wayfare.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {

    Optional<Province> findByCode(String code);

    List<Province> findAllByOrderByRegionAscNameAsc();

    List<Province> findByRegionOrderByRegionAscNameAsc(String region);
}
