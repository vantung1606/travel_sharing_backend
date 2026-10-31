package com.wayfare.repository;

import com.wayfare.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
    Optional<SiteSetting> findBySettingKey(String settingKey);
    List<SiteSetting> findByIsPublicTrue();
    List<SiteSetting> findByGroupName(String groupName);
}
