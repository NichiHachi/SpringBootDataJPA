package com.fotoshare.repository;

import com.fotoshare.entity.Album;
import com.fotoshare.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Album entity operations.
 * Provides CRUD operations and custom queries for album management.
 */
@Repository
public interface AlbumRepository extends JpaRepository<Album, Long> {

    /**
     * Find all albums owned by a specific user.
     */
    List<Album> findByOwnerOrderByCreatedAtDesc(User owner);

    /**
     * Find all albums owned by a specific user (paginated).
     */
    Page<Album> findByOwnerOrderByCreatedAtDesc(User owner, Pageable pageable);

    /**
     * Find all albums by owner ID.
     */
    List<Album> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /**
     * Find all albums by owner ID (paginated).
     */
    Page<Album> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    /**
     * Find an album by ID with its photos eagerly loaded.
     */
    @Query("SELECT a FROM Album a LEFT JOIN FETCH a.photos WHERE a.id = :albumId")
    Optional<Album> findByIdWithPhotos(@Param("albumId") Long albumId);

    /**
     * Find an album by ID and owner ID.
     * Useful for verifying ownership before operations.
     */
    Optional<Album> findByIdAndOwnerId(Long albumId, Long ownerId);

    /**
     * Check if an album exists with the given name for a specific user.
     */
    boolean existsByNameAndOwnerId(String name, Long ownerId);

    /**
     * Count albums owned by a user.
     */
    long countByOwnerId(Long ownerId);

    /**
     * Search albums by name containing a keyword (case-insensitive).
     */
    @Query("SELECT a FROM Album a WHERE a.owner.id = :ownerId AND LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Album> searchByNameAndOwnerId(@Param("keyword") String keyword, @Param("ownerId") Long ownerId);

    /**
     * Delete all albums by owner.
     */
    void deleteAllByOwnerId(Long ownerId);
}