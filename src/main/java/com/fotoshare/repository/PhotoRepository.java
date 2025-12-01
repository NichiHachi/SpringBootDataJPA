package com.fotoshare.repository;

import com.fotoshare.entity.Photo;
import com.fotoshare.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Photo entity operations.
 * Provides CRUD operations and custom queries for photos.
 */
@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /**
     * Find all photos owned by a specific user.
     */
    Page<Photo> findByOwnerId(Long ownerId, Pageable pageable);

    /**
     * Find all photos owned by a user (unpaged).
     */
    List<Photo> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /**
     * Find all public photos.
     */
    Page<Photo> findByVisibility(Visibility visibility, Pageable pageable);

    /**
     * Find a photo by its storage filename (UUID).
     */
    Optional<Photo> findByStorageFilename(String storageFilename);

    /**
     * Find all public photos ordered by creation date.
     */
    @Query("SELECT p FROM Photo p WHERE p.visibility = :visibility ORDER BY p.createdAt DESC")
    Page<Photo> findPublicPhotos(@Param("visibility") Visibility visibility, Pageable pageable);

    /**
     * Find photos accessible by a user (owned + shared + public).
     */
    @Query("""
        SELECT DISTINCT p FROM Photo p 
        LEFT JOIN p.shares s 
        WHERE p.owner.id = :userId 
           OR p.visibility = 'PUBLIC' 
           OR s.user.id = :userId
        ORDER BY p.createdAt DESC
        """)
    Page<Photo> findAccessiblePhotos(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find photos shared with a specific user.
     */
    @Query("""
        SELECT p FROM Photo p 
        JOIN p.shares s 
        WHERE s.user.id = :userId 
        ORDER BY s.createdAt DESC
        """)
    Page<Photo> findSharedWithUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Count photos owned by a user.
     */
    long countByOwnerId(Long ownerId);

    /**
     * Check if a photo exists and is owned by the specified user.
     */
    boolean existsByIdAndOwnerId(Long photoId, Long ownerId);

    /**
     * Find photos by owner and visibility.
     */
    Page<Photo> findByOwnerIdAndVisibility(Long ownerId, Visibility visibility, Pageable pageable);

    /**
     * Search photos by title containing the search term (case insensitive).
     */
    @Query("""
        SELECT p FROM Photo p 
        WHERE (p.visibility = 'PUBLIC' OR p.owner.id = :userId)
          AND LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        ORDER BY p.createdAt DESC
        """)
    Page<Photo> searchByTitle(@Param("userId") Long userId, @Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Delete all photos by owner ID.
     */
    void deleteByOwnerId(Long ownerId);

    /**
     * Find recent photos for gallery (public only).
     */
    @Query("SELECT p FROM Photo p WHERE p.visibility = 'PUBLIC' ORDER BY p.createdAt DESC")
    List<Photo> findRecentPublicPhotos(Pageable pageable);
}