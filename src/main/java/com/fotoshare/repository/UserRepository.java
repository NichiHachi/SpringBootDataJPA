package com.fotoshare.repository;

import com.fotoshare.entity.User;
import com.fotoshare.enums.Role;
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
 * Repository for User entity operations.
 * Provides database access methods for the 'utilisateur' table.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by username.
     * Used for authentication and user lookup.
     */
    Optional<User> findByUsername(String username);

    /**
     * Find a user by email address.
     * Used for registration validation and password recovery.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a username already exists.
     */
    boolean existsByUsername(String username);

    /**
     * Check if an email already exists.
     */
    boolean existsByEmail(String email);

    /**
     * Find all users by role.
     */
    List<User> findByRole(Role role);

    /**
     * Find all enabled/disabled users.
     */
    List<User> findByEnabled(Boolean enabled);

    /**
     * Find users by role with pagination.
     */
    Page<User> findByRole(Role role, Pageable pageable);

    /**
     * Find all users except the specified user (for sharing).
     */
    @Query("SELECT u FROM User u WHERE u.id <> :userId AND u.enabled = true ORDER BY u.username")
    List<User> findAllOtherUsers(@Param("userId") Long userId);

    /**
     * Search users by username or email (for sharing functionality).
     */
    @Query("SELECT u FROM User u WHERE u.enabled = true AND " +
           "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchUsers(@Param("search") String search);

    /**
     * Count users by role.
     */
    long countByRole(Role role);

    /**
     * Count active (enabled) users.
     */
    long countByEnabledTrue();

    /**
     * Enable or disable a user account.
     */
    @Modifying
    @Query("UPDATE User u SET u.enabled = :enabled WHERE u.id = :userId")
    int updateEnabledStatus(@Param("userId") Long userId, @Param("enabled") Boolean enabled);

    /**
     * Update user's role.
     */
    @Modifying
    @Query("UPDATE User u SET u.role = :role WHERE u.id = :userId")
    int updateUserRole(@Param("userId") Long userId, @Param("role") Role role);

    /**
     * Find users with pagination and filter by enabled status.
     */
    Page<User> findByEnabled(Boolean enabled, Pageable pageable);

    /**
     * Search users by username containing the given string (case-insensitive).
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))")
    Page<User> findByUsernameContainingIgnoreCase(@Param("username") String username, Pageable pageable);
}