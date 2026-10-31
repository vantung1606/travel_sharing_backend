package com.wayfare.repository;

import com.wayfare.entity.ThemeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThemeVersionRepository extends JpaRepository<ThemeVersion, Long> {
    Optional<ThemeVersion> findFirstByStatusOrderByPublishedAtDesc(String status);
    Optional<ThemeVersion> findFirstByStatusOrderByCreatedAtDesc(String status);
    List<ThemeVersion> findAllByOrderByVersionNoDesc();
    Optional<ThemeVersion> findFirstByOrderByVersionNoDesc();
}
