package com.fotoshare.entity;

import com.fotoshare.enums.PermissionLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Entity representing a share permission between a photo and a user.
 * Maps to the 'partage' table in the database.
 * 
 * This entity manages the ACL (Access Control List) for photos,
 * allowing owners to share their photos with specific users
 * at different permission levels (READ, COMMENT, ADMIN).
 */
@Entity
@Table(name = "partage", 
       uniqueConstraints = @UniqueConstraint(
           name = "uk_photo_user", 
           columnNames = {"photo_id", "user_id"}
       ),
       indexes = {
           @Index(name = "idx_partage_user", columnList = "user_id"),
           @Index(name = "idx_partage_photo", columnList = "photo_id")
       })
@Data
@EqualsAndHashCode(exclude = {"photo", "user"})
@ToString(exclude = {"photo", "user"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The photo being shared.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    /**
     * The user who receives access to the photo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The level of permission granted to the user.
     * - READ: Can view the photo
     * - COMMENT: Can view and comment on the photo
     * - ADMIN: Can manage the photo (edit, delete)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false)
    @Builder.Default
    private PermissionLevel permissionLevel = PermissionLevel.READ;

    /**
     * Timestamp when the share was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Check if this share grants at least READ permission.
     */
    public boolean canRead() {
        return permissionLevel != null;
    }

    /**
     * Check if this share grants at least COMMENT permission.
     */
    public boolean canComment() {
        return permissionLevel == PermissionLevel.COMMENT || 
               permissionLevel == PermissionLevel.ADMIN;
    }

    /**
     * Check if this share grants ADMIN permission.
     */
    public boolean canAdmin() {
        return permissionLevel == PermissionLevel.ADMIN;
    }
}