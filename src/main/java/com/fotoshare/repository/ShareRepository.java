package com.fotoshare.repository;

import com.fotoshare.entity.Share;
import com.fotoshare.enums.PermissionLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Share entity (partage table).
 * Manages photo sharing permissions between users.
 */
@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {

    /**
     * Find a specific share by photo and user.
     * Used to check if a photo is already shared with a user.
     */
    Optional<Share> findByPhotoIdAndUserId(Long photoId, Long userId);

    /**
     * Check if a share exists between a photo and a user.
     */
    boolean existsByPhotoIdAndUserId(Long photoId, Long userId);

    /**
     * Find all shares for a specific photo.
     */
    List<Share> findByPhotoId(Long photoId);

    /**
     * Find all shares for a specific photo with pagination.
     */
    Page<Share> findByPhotoId(Long photoId, Pageable pageable);

    /**
     * Find all photos shared with a specific user.
     */
    List<Share> findByUserId(Long userId);

    /**
     * Find all photos shared with a specific user with pagination.
     */
    Page<Share> findByUserId(Long userId, Pageable pageable);

    /**
     * Find shares by user with a minimum permission level.
     * Useful for finding photos a user can comment on or admin.
     */
    @Query("SELECT s FROM Share s WHERE s.user.id = :userId AND s.permissionLevel IN :permissions")
    List<Share> findByUserIdAndPermissionLevelIn(
            @Param("userId") Long userId,
            @Param("permissions") List<PermissionLevel> permissions
    );

    /**
     * Check if a user has a specific permission level for a photo.
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Share s " +
           "WHERE s.photo.id = :photoId AND s.user.id = :userId AND s.permissionLevel = :permission")
    boolean hasPermission(
            @Param("photoId") Long photoId,
            @Param("userId") Long userId,
            @Param("permission") PermissionLevel permission
    );

    /**
     * Check if a user has at least the given permission level for a photo.
     * ADMIN includes COMMENT and READ permissions.
     * COMMENT includes READ permission.
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Share s " +
           "WHERE s.photo.id = :photoId AND s.user.id = :userId")
    boolean hasAnyPermission(@Param("photoId") Long photoId, @Param("userId") Long userId);

    /**
     * Get the permission level for a specific photo-user combination.
     */
    @Query("SELECT s.permissionLevel FROM Share s WHERE s.photo.id = :photoId AND s.user.id = :userId")
    Optional<PermissionLevel> getPermissionLevel(
            @Param("photoId") Long photoId,
            @Param("userId") Long userId
    );

    /**
     * Delete all shares for a specific photo.
     */
    @Modifying
    @Query("DELETE FROM Share s WHERE s.photo.id = :photoId")
    void deleteByPhotoId(@Param("photoId") Long photoId);

    /**
     * Delete all shares for a specific user (when user is deleted).
     */
    @Modifying
    @Query("DELETE FROM Share s WHERE s.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Delete a specific share by photo and user.
     */
    @Modifying
    void deleteByPhotoIdAndUserId(Long photoId, Long userId);

    /**
     * Count shares for a photo.
     */
    long countByPhotoId(Long photoId);

    /**
     * Count photos shared with a user.
     */
    long countByUserId(Long userId);

    /**
     * Find all shares created by photos owned by a specific user.
     * Useful for listing who has access to a user's photos.
     */
    @Query("SELECT s FROM Share s WHERE s.photo.owner.id = :ownerId")
    Page<Share> findSharesByPhotoOwner(@Param("ownerId") Long ownerId, Pageable pageable);

    /**
     * Update permission level for an existing share.
     */
    @Modifying
    @Query("UPDATE Share s SET s.permissionLevel = :permission WHERE s.photo.id = :photoId AND s.user.id = :userId")
    int updatePermissionLevel(
            @Param("photoId") Long photoId,
            @Param("userId") Long userId,
            @Param("permission") PermissionLevel permission
    );
}