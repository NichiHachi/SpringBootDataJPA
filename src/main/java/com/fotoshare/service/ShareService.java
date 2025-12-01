package com.fotoshare.service;

import com.fotoshare.dto.ShareDTO;
import com.fotoshare.entity.Photo;
import com.fotoshare.entity.Share;
import com.fotoshare.entity.User;
import com.fotoshare.enums.PermissionLevel;
import com.fotoshare.mapper.EntityMapper;
import com.fotoshare.repository.PhotoRepository;
import com.fotoshare.repository.ShareRepository;
import com.fotoshare.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing photo sharing between users.
 * 
 * This service handles the ACL (Access Control List) system for photos,
 * allowing users to share their photos with specific users at different
 * permission levels:
 * - READ: View the photo
 * - COMMENT: View and comment on the photo
 * - ADMIN: Full management (edit, delete, share)
 * 
 * Business rules:
 * - Only photo owners (or users with ADMIN permission) can share photos
 * - A photo can only be shared once with each user (unique constraint)
 * - Sharing permissions can be updated after creation
 * - Share removal is allowed by photo owner or admins
 */
@Service
@Transactional
public class ShareService {

    private static final Logger logger = LoggerFactory.getLogger(ShareService.class);

    private final ShareRepository shareRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final EntityMapper entityMapper;

    public ShareService(ShareRepository shareRepository,
                        PhotoRepository photoRepository,
                        UserRepository userRepository,
                        SecurityService securityService,
                        EntityMapper entityMapper) {
        this.shareRepository = shareRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.securityService = securityService;
        this.entityMapper = entityMapper;
    }

    // =========================================
    // SHARE CREATION
    // =========================================

    /**
     * Share a photo with a user.
     *
     * @param photoId The photo to share
     * @param userId The user to share with
     * @param permissionLevel The permission level to grant
     * @param authentication Current user's authentication
     * @return The created share as DTO
     * @throws IllegalArgumentException If validation fails
     */
    public ShareDTO sharePhoto(Long photoId, Long userId, PermissionLevel permissionLevel,
                               Authentication authentication) {
        logger.info("Sharing photo {} with user {} at level {}", photoId, userId, permissionLevel);

        // Validate inputs
        if (photoId == null || userId == null) {
            throw new IllegalArgumentException("Photo ID et User ID sont requis");
        }

        // Get the photo
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        // Verify permission to share
        if (!securityService.canSharePhoto(authentication, photoId)) {
            throw new IllegalArgumentException("Vous n'avez pas la permission de partager cette photo");
        }

        // Get the target user
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

        // Prevent sharing with the photo owner
        if (photo.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("Vous ne pouvez pas partager une photo avec son propriétaire");
        }

        // Check if share already exists
        if (shareRepository.existsByPhotoIdAndUserId(photoId, userId)) {
            throw new IllegalArgumentException("Cette photo est déjà partagée avec cet utilisateur");
        }

        // Create the share
        Share share = Share.builder()
                .photo(photo)
                .user(targetUser)
                .permissionLevel(permissionLevel != null ? permissionLevel : PermissionLevel.READ)
                .build();

        share = shareRepository.save(share);
        logger.info("Photo shared successfully: shareId={}", share.getId());

        return entityMapper.toShareDTO(share);
    }

    /**
     * Share a photo using ShareDTO.
     *
     * @param shareDTO The share details
     * @param authentication Current user's authentication
     * @return The created share as DTO
     */
    public ShareDTO sharePhoto(ShareDTO shareDTO, Authentication authentication) {
        return sharePhoto(
                shareDTO.getPhotoId(),
                shareDTO.getUserId(),
                shareDTO.getPermissionLevel(),
                authentication
        );
    }

    // =========================================
    // SHARE RETRIEVAL
    // =========================================

    /**
     * Get a share by ID.
     *
     * @param shareId The share ID
     * @return Optional containing the share DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<ShareDTO> getShareById(Long shareId) {
        return shareRepository.findById(shareId)
                .map(entityMapper::toShareDTO);
    }

    /**
     * Get a share by photo and user.
     *
     * @param photoId The photo ID
     * @param userId The user ID
     * @return Optional containing the share DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<ShareDTO> getShare(Long photoId, Long userId) {
        return shareRepository.findByPhotoIdAndUserId(photoId, userId)
                .map(entityMapper::toShareDTO);
    }

    /**
     * Get all shares for a photo.
     *
     * @param photoId The photo ID
     * @return List of share DTOs
     */
    @Transactional(readOnly = true)
    public List<ShareDTO> getSharesByPhoto(Long photoId) {
        return entityMapper.toShareDTOList(shareRepository.findByPhotoId(photoId));
    }

    /**
     * Get all shares for a photo (paginated).
     *
     * @param photoId The photo ID
     * @param pageable Pagination parameters
     * @return Page of share DTOs
     */
    @Transactional(readOnly = true)
    public Page<ShareDTO> getSharesByPhoto(Long photoId, Pageable pageable) {
        return shareRepository.findByPhotoId(photoId, pageable)
                .map(entityMapper::toShareDTO);
    }

    /**
     * Get all photos shared with a user.
     *
     * @param userId The user ID
     * @return List of share DTOs
     */
    @Transactional(readOnly = true)
    public List<ShareDTO> getSharesForUser(Long userId) {
        return entityMapper.toShareDTOList(shareRepository.findByUserId(userId));
    }

    /**
     * Get all photos shared with a user (paginated).
     *
     * @param userId The user ID
     * @param pageable Pagination parameters
     * @return Page of share DTOs
     */
    @Transactional(readOnly = true)
    public Page<ShareDTO> getSharesForUser(Long userId, Pageable pageable) {
        return shareRepository.findByUserId(userId, pageable)
                .map(entityMapper::toShareDTO);
    }

    /**
     * Get all photos shared with the current user.
     *
     * @param authentication Current user's authentication
     * @param pageable Pagination parameters
     * @return Page of share DTOs
     */
    @Transactional(readOnly = true)
    public Page<ShareDTO> getMySharedPhotos(Authentication authentication, Pageable pageable) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return Page.empty(pageable);
        }
        return getSharesForUser(userId, pageable);
    }

    /**
     * Get all shares created by the current user (photos they've shared).
     *
     * @param authentication Current user's authentication
     * @param pageable Pagination parameters
     * @return Page of share DTOs
     */
    @Transactional(readOnly = true)
    public Page<ShareDTO> getSharesICreated(Authentication authentication, Pageable pageable) {
        Long ownerId = securityService.getCurrentUserId(authentication);
        if (ownerId == null) {
            return Page.empty(pageable);
        }
        return shareRepository.findSharesByPhotoOwner(ownerId, pageable)
                .map(entityMapper::toShareDTO);
    }

    // =========================================
    // SHARE UPDATE
    // =========================================

    /**
     * Update a share's permission level.
     *
     * @param photoId The photo ID
     * @param userId The user ID
     * @param newPermissionLevel The new permission level
     * @param authentication Current user's authentication
     * @return Updated share DTO
     */
    public ShareDTO updateSharePermission(Long photoId, Long userId, PermissionLevel newPermissionLevel,
                                          Authentication authentication) {
        logger.info("Updating share permission: photo={}, user={}, level={}", photoId, userId, newPermissionLevel);

        // Verify permission to manage shares
        if (!securityService.canSharePhoto(authentication, photoId)) {
            throw new IllegalArgumentException("Vous n'avez pas la permission de modifier ce partage");
        }

        // Get existing share
        Share share = shareRepository.findByPhotoIdAndUserId(photoId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Partage non trouvé"));

        share.setPermissionLevel(newPermissionLevel);
        share = shareRepository.save(share);

        logger.info("Share permission updated: shareId={}", share.getId());

        return entityMapper.toShareDTO(share);
    }

    /**
     * Update a share by ID.
     *
     * @param shareId The share ID
     * @param newPermissionLevel The new permission level
     * @param authentication Current user's authentication
     * @return Updated share DTO
     */
    public ShareDTO updateShare(Long shareId, PermissionLevel newPermissionLevel, Authentication authentication) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Partage non trouvé"));

        return updateSharePermission(share.getPhoto().getId(), share.getUser().getId(),
                newPermissionLevel, authentication);
    }

    // =========================================
    // SHARE DELETION
    // =========================================

    /**
     * Remove a share (unshare a photo with a user).
     *
     * @param photoId The photo ID
     * @param userId The user ID
     * @param authentication Current user's authentication
     */
    public void removeShare(Long photoId, Long userId, Authentication authentication) {
        logger.info("Removing share: photo={}, user={}", photoId, userId);

        // Verify permission to manage shares
        if (!securityService.canSharePhoto(authentication, photoId)) {
            throw new IllegalArgumentException("Vous n'avez pas la permission de supprimer ce partage");
        }

        // Check if share exists
        if (!shareRepository.existsByPhotoIdAndUserId(photoId, userId)) {
            throw new IllegalArgumentException("Partage non trouvé");
        }

        shareRepository.deleteByPhotoIdAndUserId(photoId, userId);
        logger.info("Share removed successfully");
    }

    /**
     * Remove a share by ID.
     *
     * @param shareId The share ID
     * @param authentication Current user's authentication
     */
    public void removeShareById(Long shareId, Authentication authentication) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Partage non trouvé"));

        removeShare(share.getPhoto().getId(), share.getUser().getId(), authentication);
    }

    /**
     * Remove all shares for a photo.
     *
     * @param photoId The photo ID
     * @param authentication Current user's authentication
     */
    public void removeAllSharesForPhoto(Long photoId, Authentication authentication) {
        logger.info("Removing all shares for photo: {}", photoId);

        // Verify permission
        if (!securityService.canSharePhoto(authentication, photoId)) {
            throw new IllegalArgumentException("Vous n'avez pas la permission de gérer les partages de cette photo");
        }

        shareRepository.deleteByPhotoId(photoId);
        logger.info("All shares removed for photo: {}", photoId);
    }

    // =========================================
    // PERMISSION CHECKING
    // =========================================

    /**
     * Check if a photo is shared with a user.
     *
     * @param photoId The photo ID
     * @param userId The user ID
     * @return true if shared
     */
    @Transactional(readOnly = true)
    public boolean isPhotoSharedWithUser(Long photoId, Long userId) {
        return shareRepository.existsByPhotoIdAndUserId(photoId, userId);
    }

    /**
     * Get the permission level for a user on a photo.
     *
     * @param photoId The photo ID
     * @param userId The user ID
     * @return The permission level, or null if not shared
     */
    @Transactional(readOnly = true)
    public PermissionLevel getPermissionLevel(Long photoId, Long userId) {
        return shareRepository.getPermissionLevel(photoId, userId).orElse(null);
    }

    /**
     * Check if a user has at least the specified permission level.
     *
     * @param photoId The photo ID
     * @param userId The user ID
     * @param requiredLevel The minimum required permission level
     * @return true if the user has sufficient permission
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(Long photoId, Long userId, PermissionLevel requiredLevel) {
        return securityService.hasMinimumPermission(photoId, userId, requiredLevel);
    }

    // =========================================
    // STATISTICS
    // =========================================

    /**
     * Count shares for a photo.
     *
     * @param photoId The photo ID
     * @return Number of shares
     */
    @Transactional(readOnly = true)
    public long countSharesForPhoto(Long photoId) {
        return shareRepository.countByPhotoId(photoId);
    }

    /**
     * Count photos shared with a user.
     *
     * @param userId The user ID
     * @return Number of shared photos
     */
    @Transactional(readOnly = true)
    public long countSharesForUser(Long userId) {
        return shareRepository.countByUserId(userId);
    }
}