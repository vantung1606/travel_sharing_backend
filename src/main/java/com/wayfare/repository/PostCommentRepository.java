package com.wayfare.repository;

import com.wayfare.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    List<PostComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    List<PostComment> findByPostIdAndParentIsNullOrderByCreatedAtAsc(Long postId);

    List<PostComment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    long countByPostId(Long postId);
}
