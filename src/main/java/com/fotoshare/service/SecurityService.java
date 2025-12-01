package com.fotoshare.service;

import com.fotoshare.entity.Photo;
import com.fotoshare.entity.Share;
import com.fotoshare.entity.User;
import com.fotoshare.enums.PermissionLevel;
import com.fotoshare.enums.Role;
import com.fotoshare.enums.Visibility;
import com.fotoshare.repository.CommentRepository;
import com.fotoshare.repository.PhotoRepository;
import com.fotoshare.repository.ShareRepository;
import com.fotoshare.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for handling security and access control (ACL) verification.
 * 
 * This service is used with @PreAuthorize annotations to dynamically
 * check permissions before executing operations.
 * 
 * Permission levels:
 * - Owner: Full access (CRUD) to their photos
 * - Public: Read-only access to public photos
 * - Shared (READ): Can view the photo
 * - Shared (COMMENT): Can view and comment on the photo
 * - Shared (ADMIN): Can manage the photo (edit, delete)
 * 
 * Example usage in controllers:
 * @PreAuthorize("@securityService.canAccessPhoto(authentication, #photoId)")
 */
@Service("securityService")
@Transactional(readOnly = true)
public class SecurityService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);

    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final ShareRepository shareRepository;
    private final CommentRepository commentRepository;

    public SecurityService(UserRepository userRepository,
                           PhotoRepository photoRepository,
                           ShareRepository shareRepository,
                           CommentRepository commentRepository) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.shareRepository = shareRepository;
        this.commentRepository = commentRepository;
    }

    // =========================================
    // USER VERIFICATION
    // =========================================

    /**
     * Get the current authenticated user.
     */
    public Optional<User> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username);
    }

    /**
     * Get the current user's ID, or null if not authenticated.
     */
    public Long getCurrentUserId(Authentication authentication) {
        return getCurrentUser(authentication)
                .map(User::getId)
                .orElse(null);
    }

    /**
     * Check if the current user is an admin.
     */
    public boolean isAdmin(Authentication authentication) {
        return getCurrentUser(authentication)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    /**
     * Check if the current user is a moderator or admin.
     */
    public boolean isModerator(Authentication authentication) {
        return getCurrentUser(authentication)
                .map(user -> user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR)
                .orElse(false);
    }

    // =========================================
    // PHOTO ACCESS CONTROL
    // =========================================

    /**
     * Check if the user can access (view) a photo.
     * Access is granted if:
     * - The photo is public
     * - The user is the owner
     * - The user has been granted any share permission
     * - The user is an admin/moderator
     */
    public boolean canAccessPhoto(Authentication authentication, Long photoId) {
        if (photoId == null) {
            return false;
        }

        Optional<Photo> photoOpt = photoRepository.findById(photoId);
        if (photoOpt.isEmpty()) {
            logger.debug("Photo not found: {}", photoId);
            return false;
        }

        Photo photo = photoOpt.get();

        // Public photos are accessible to everyone
        if (photo.getVisibility() == Visibility.PUBLIC) {
            return true;
        }

        // Anonymous users can only access public photos
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<User> userOpt = getCurrentUser(authentication);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        // Admin and moderators can access all photos
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR) {
            return true;
        }

        // Owner can access their own photos
        if (photo.getOwner().getId().equals(user.getId())) {
            return true;
        }

        // Check if the photo is shared with the user
        return shareRepository.existsByPhotoIdAndUserId(photoId, user.getId());
    }

    /**
     * Check if the user can edit a photo.
     * Edit is allowed if:
     * - The user is the owner
     * - The user has ADMIN share permission
     * - The user is a global admin
     */
    public boolean canEditPhoto(Authentication authentication, Long photoId) {
        if (photoId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<User> userOpt = getCurrentUser(authentication);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        // Global admins can edit any photo
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        Optional<Photo> photoOpt = photoRepository.findById(photoId);
        if (photoOpt.isEmpty()) {
            return false;
        }

        Photo photo = photoOpt.get();

        // Owner can edit their photos
        if (photo.getOwner().getId().equals(user.getId())) {
            return true;
        }

        // Check for ADMIN share permission
        Optional<PermissionLevel> permission = shareRepository.getPermissionLevel(photoId, user.getId());
        return permission.isPresent() && permission.get() == PermissionLevel.ADMIN;
    }

    /**
     * Check if the user can delete a photo.
     * Same rules as editing.
     */
    public boolean canDeletePhoto(Authentication authentication, Long photoId) {
        return canEditPhoto(authentication, photoId);
    }

    /**
     * Check if the user can comment on a photo.
     * Commenting is allowed if:
     * - The photo is public (and user is authenticated)
     * - The user is the owner
     * - The user has COMMENT or ADMIN share permission
     * - The user is a global admin/moderator
     */
    public boolean canCommentOnPhoto(Authentication authentication, Long photoId) {
        if (photoId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<User> userOpt = getCurrentUser(authentication);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        // Admin and moderators can comment on any photo
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR) {
            return true;
        }

        Optional<Photo> photoOpt = photoRepository.findById(photoId);
        if (photoOpt.isEmpty()) {
            return false;
        }

        Photo photo = photoOpt.get();

        // Owner can comment on their photos
        if (photo.getOwner().getId().equals(user.getId())) {
            return true;
        }

        // Public photos allow comments from authenticated users
        if (photo.getVisibility() == Visibility.PUBLIC) {
            return true;
        }

        // Check for COMMENT or ADMIN share permission
        Optional<PermissionLevel> permission = shareRepository.getPermissionLevel(photoId, user.getId());
        if (permission.isEmpty()) {
            return false;
        }

        return permission.get() == PermissionLevel.COMMENT || permission.get() == PermissionLevel.ADMIN;
    }

    /**
     * Check if the user can share a photo.
     * Sharing is allowed if:
     * - The user is the owner
     * - The user has ADMIN share permission
     * - The user is a global admin
     */
    public boolean canSharePhoto(Authentication authentication, Long photoId) {
        return canEditPhoto(authentication, photoId);
    }

    /**
     * Check if the user is the owner of a photo.
     */
    public boolean isPhotoOwner(Authentication authentication, Long photoId) {
        if (photoId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<User> userOpt = getCurrentUser(authentication);
        if (userOpt.isEmpty()) {
            return false;
        }

        return photoRepository.existsByIdAndOwnerId(photoId, userOpt.get().getId());
    }

    // =========================================
    // ALBUM ACCESS CONTROL
    // =========================================

    /**
     * Check if the user can access an album.
     */
    public boolean canAccessAlbum(Authentication authentication, Long albumId) {
        if (albumId == null) {
            return false;
        }

        // For now, albums are only accessible to their owners
        return isAlbumOwner(authentication, albumId) || isAdmin(authentication);
    }

    /**
     * Check if the user can edit an album.
     */
    public boolean canEditAlbum(Authentication authentication, Long albumId) {
        return isAlbumOwner(authentication, albumId) || isAdmin(authentication);
    }

    /**
     * Check if the user is the owner of an album.
     */
    public boolean isAlbumOwner(Authentication authentication, Long albumId) {
        if (albumId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<User> userOpt = getCurrentUser(authentication);
        return userOpt.isPresent() && 
               userRepository.findById(userOpt.get().getId())
                       .map(user -> user.getAlbums().stream()
                               .anyMatch(album -> album.getId().equals(albumId)))
                       .orElse(false);
    }

    // =========================================
    // COMMENT ACCESS CONTROL
    // =========================================

    /**
     * Check if the user can delete a comment.
     * Deletion is allowed if:
     * - The user is the comment author
     * - The user is the photo owner
     * - The user is an admin/moderator
     */
    public boolean canDeleteComment(Authentication authentication, Long commentId) {
        if (commentId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<User> userOpt = getCurrentUser(authentication);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        // Admin and moderators can delete any comment
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR) {
            return true;
        }

        return commentRepository.findById(commentId)
                .map(comment -> {
                    // Comment author can delete their comment
                    if (comment.getAuthor().getId().equals(user.getId())) {
                        return true;
                    }
                    // Photo owner can delete comments on their photos
                    return comment.getPhoto().getOwner().getId().equals(user.getId());
                })
                .orElse(false);
    }

    // =========================================
    // SHARE ACCESS CONTROL
    // =========================================

    /**
     * Check if the user can manage shares for a photo.
     */
    public boolean canManageShares(Authentication authentication, Long photoId) {
        return canSharePhoto(authentication, photoId);
    }

    /**
     * Check if the user can remove a specific share.
     */
    public boolean canRemoveShare(Authentication authentication, Long shareId) {
        if (shareId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<User> userOpt = getCurrentUser(authentication);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        // Admin can remove any share
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        return shareRepository.findById(shareId)
                .map(share -> {
                    // Photo owner can remove shares
                    return share.getPhoto().getOwner().getId().equals(user.getId());
                })
                .orElse(false);
    }

    // =========================================
    // USER MANAGEMENT ACCESS CONTROL
    // =========================================

    /**
     * Check if the user can manage other users.
     * Only admins can manage users.
     */
    public boolean canManageUsers(Authentication authentication) {
        return isAdmin(authentication);
    }

    /**
     * Check if the user can ban/unban other users.
     */
    public boolean canBanUser(Authentication authentication, Long userId) {
        if (!isAdmin(authentication)) {
            return false;
        }

        // Prevent admin from banning themselves
        Long currentUserId = getCurrentUserId(authentication);
        return currentUserId != null && !currentUserId.equals(userId);
    }

    /**
     * Check if the current user is viewing their own profile.
     */
    public boolean isOwnProfile(Authentication authentication, Long userId) {
        Long currentUserId = getCurrentUserId(authentication);
        return currentUserId != null && currentUserId.equals(userId);
    }

    // =========================================
    // UTILITY METHODS
    // =========================================

    /**
     * Get the permission level for a user on a photo.
     * Returns null if no share exists.
     */
    public PermissionLevel getSharePermission(Long photoId, Long userId) {
        return shareRepository.getPermissionLevel(photoId, userId).orElse(null);
    }

    /**
     * Check if a user has at least the specified permission level on a photo.
     */
    public boolean hasMinimumPermission(Long photoId, Long userId, PermissionLevel requiredLevel) {
        Optional<PermissionLevel> actualLevel = shareRepository.getPermissionLevel(photoId, userId);
        if (actualLevel.isEmpty()) {
            return false;
        }

        return switch (requiredLevel) {
            case READ -> true; // Any permission includes READ
            case COMMENT -> actualLevel.get() == PermissionLevel.COMMENT || 
                           actualLevel.get() == PermissionLevel.ADMIN;
            case ADMIN -> actualLevel.get() == PermissionLevel.ADMIN;
        };
    }
}