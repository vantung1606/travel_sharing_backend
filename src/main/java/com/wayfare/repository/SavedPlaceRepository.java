package com.wayfare.repository;

import com.wayfare.entity.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    void deleteByUserIdAndPlaceId(Long userId, Long placeId);

    /**
     * Lấy toàn bộ địa điểm người dùng đánh dấu "muốn đi", join fetch luôn
     * place để không bị LazyInitializationException khi serialize ngoài
     * transaction.
     */
    @Query("select sp from SavedPlace sp join fetch sp.place where sp.user.id = :userId order by sp.createdAt desc")
    List<SavedPlace> findAllByUserIdWithPlace(@Param("userId") Long userId);
}
