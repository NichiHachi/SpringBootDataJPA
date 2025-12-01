package com.fotoshare.repository;

import com.fotoshare.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Comment entity.
 * Provides database operations for comments on photos.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Find all comments for a specific photo, ordered by creation date descending.
     */
    List<Comment> findByPhotoIdOrderByCreatedAtDesc(Long photoId);

    /**
     * Find all comments for a photo with pagination.
     */
    Page<Comment> findByPhotoId(Long photoId, Pageable pageable);

    /**
     * Find all comments by a specific author.
     */
    List<Comment> findByAuthorId(Long authorId);

    /**
     * Find all comments by a specific author with pagination.
     */
    Page<Comment> findByAuthorId(Long authorId, Pageable pageable);

    /**
     * Count the number of comments on a specific photo.
     */
    long countByPhotoId(Long photoId);

    /**
     * Count the number of comments by a specific author.
     */
    long countByAuthorId(Long authorId);

    /**
     * Delete all comments on a specific photo.
     */
    void deleteByPhotoId(Long photoId);

    /**
     * Delete all comments by a specific author.
     */
    void deleteByAuthorId(Long authorId);

    /**
     * Find the latest comments on a photo (limited).
     */
    @Query("SELECT c FROM Comment c WHERE c.photo.id = :photoId ORDER BY c.createdAt DESC")
    List<Comment> findLatestByPhotoId(@Param("photoId") Long photoId, Pageable pageable);

    /**
     * Check if a user has commented on a specific photo.
     */
    boolean existsByPhotoIdAndAuthorId(Long photoId, Long authorId);

    /**
     * Find all comments for photos owned by a specific user.
     */
    @Query("SELECT c FROM Comment c WHERE c.photo.owner.id = :ownerId ORDER BY c.createdAt DESC")
    Page<Comment> findCommentsOnUserPhotos(@Param("ownerId") Long ownerId, Pageable pageable);
}