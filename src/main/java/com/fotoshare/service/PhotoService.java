package com.fotoshare.service;

import com.fotoshare.dto.PhotoDTO;
import com.fotoshare.dto.PhotoUploadDTO;
import com.fotoshare.entity.Album;
import com.fotoshare.entity.Photo;
import com.fotoshare.entity.User;
import com.fotoshare.enums.Visibility;
import com.fotoshare.mapper.EntityMapper;
import com.fotoshare.repository.AlbumRepository;
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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing photos.
 * 
 * Business Logic Layer responsibilities:
 * - Photo upload with file validation and storage
 * - Photo CRUD operations
 * - Visibility management
 * - Thumbnail generation coordination
 * - Transaction management
 * 
 * This service uses the FileStorageService for actual file operations
 * and the SecurityService for access control verification.
 */
@Service
@Transactional
public class PhotoService {

    private static final Logger logger = LoggerFactory.getLogger(PhotoService.class);

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final AlbumRepository albumRepository;
    private final ShareRepository shareRepository;
    private final FileStorageService fileStorageService;
    private final SecurityService securityService;
    private final EntityMapper entityMapper;

    public PhotoService(PhotoRepository photoRepository,
                        UserRepository userRepository,
                        AlbumRepository albumRepository,
                        ShareRepository shareRepository,
                        FileStorageService fileStorageService,
                        SecurityService securityService,
                        EntityMapper entityMapper) {
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.albumRepository = albumRepository;
        this.shareRepository = shareRepository;
        this.fileStorageService = fileStorageService;
        this.securityService = securityService;
        this.entityMapper = entityMapper;
    }

    // =========================================
    // PHOTO UPLOAD
    // =========================================

    /**
     * Upload a new photo.
     *
     * @param uploadDTO The upload form data including file
     * @param authentication Current user's authentication
     * @return The created photo as DTO
     * @throws IOException If file storage fails
     * @throws IllegalArgumentException If validation fails
     */
    public PhotoDTO uploadPhoto(PhotoUploadDTO uploadDTO, Authentication authentication) throws IOException {
        logger.info("Processing photo upload for user: {}", authentication.getName());

        // Get the current user
        User owner = securityService.getCurrentUser(authentication)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Store the file (validates and creates thumbnail)
        FileStorageService.StorageResult storageResult = fileStorageService.storePhoto(uploadDTO.getFile());

        // Create the photo entity
        Photo photo = Photo.builder()
                .title(uploadDTO.getTitle())
                .description(uploadDTO.getDescription())
                .originalFilename(storageResult.getOriginalFilename())
                .storageFilename(storageResult.getStorageFilename())
                .contentType(storageResult.getContentType())
                .visibility(uploadDTO.getVisibility() != null ? uploadDTO.getVisibility() : Visibility.PRIVATE)
                .owner(owner)
                .build();

        photo = photoRepository.save(photo);
        logger.info("Photo uploaded successfully: id={}, filename={}", photo.getId(), photo.getStorageFilename());

        // Add to album if specified
        if (uploadDTO.getAlbumId() != null) {
            addPhotoToAlbum(photo.getId(), uploadDTO.getAlbumId(), authentication);
        }

        return entityMapper.toPhotoDTO(photo, true, true, true);
    }

    // =========================================
    // PHOTO RETRIEVAL
    // =========================================

    /**
     * Get a photo by ID.
     *
     * @param photoId The photo ID
     * @return Optional containing the photo DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<PhotoDTO> getPhotoById(Long photoId) {
        return photoRepository.findById(photoId)
                .map(entityMapper::toPhotoDTO);
    }

    /**
     * Get a photo by ID with permission flags for the current user.
     *
     * @param photoId The photo ID
     * @param authentication Current user's authentication
     * @return Optional containing the photo DTO with permissions
     */
    @Transactional(readOnly = true)
    public Optional<PhotoDTO> getPhotoById(Long photoId, Authentication authentication) {
        return photoRepository.findById(photoId)
                .map(photo -> {
                    boolean canEdit = securityService.canEditPhoto(authentication, photoId);
                    boolean canDelete = securityService.canDeletePhoto(authentication, photoId);
                    boolean canComment = securityService.canCommentOnPhoto(authentication, photoId);
                    return entityMapper.toPhotoDTO(photo, canEdit, canDelete, canComment);
                });
    }

    /**
     * Get the raw photo entity by ID.
     * For internal service use only.
     */
    @Transactional(readOnly = true)
    public Optional<Photo> getPhotoEntityById(Long photoId) {
        return photoRepository.findById(photoId);
    }

    /**
     * Get a photo by its storage filename.
     *
     * @param storageFilename The UUID-based storage filename
     * @return Optional containing the photo if found
     */
    @Transactional(readOnly = true)
    public Optional<Photo> getPhotoByStorageFilename(String storageFilename) {
        return photoRepository.findByStorageFilename(storageFilename);
    }

    /**
     * Get all photos owned by a user (paginated).
     *
     * @param userId The owner's user ID
     * @param pageable Pagination parameters
     * @return Page of photo DTOs
     */
    @Transactional(readOnly = true)
    public Page<PhotoDTO> getPhotosByOwner(Long userId, Pageable pageable) {
        return photoRepository.findByOwnerId(userId, pageable)
                .map(entityMapper::toPhotoDTO);
    }

    /**
     * Get all photos owned by the current user (paginated).
     *
     * @param authentication Current user's authentication
     * @param pageable Pagination parameters
     * @return Page of photo DTOs
     */
    @Transactional(readOnly = true)
    public Page<PhotoDTO> getMyPhotos(Authentication authentication, Pageable pageable) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return Page.empty(pageable);
        }
        return getPhotosByOwner(userId, pageable);
    }

    /**
     * Get all public photos (paginated).
     *
     * @param pageable Pagination parameters
     * @return Page of public photo DTOs
     */
    @Transactional(readOnly = true)
    public Page<PhotoDTO> getPublicPhotos(Pageable pageable) {
        return photoRepository.findByVisibility(Visibility.PUBLIC, pageable)
                .map(entityMapper::toPhotoDTO);
    }

    /**
     * Get all photos accessible by the current user.
     * Includes: owned photos, shared photos, and public photos.
     *
     * @param authentication Current user's authentication
     * @param pageable Pagination parameters
     * @return Page of accessible photo DTOs
     */
    @Transactional(readOnly = true)
    public Page<PhotoDTO> getAccessiblePhotos(Authentication authentication, Pageable pageable) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return getPublicPhotos(pageable);
        }
        return photoRepository.findAccessiblePhotos(userId, pageable)
                .map(entityMapper::toPhotoDTO);
    }

    /**
     * Get photos shared with the current user.
     *
     * @param authentication Current user's authentication
     * @param pageable Pagination parameters
     * @return Page of shared photo DTOs
     */
    @Transactional(readOnly = true)
    public Page<PhotoDTO> getSharedWithMe(Authentication authentication, Pageable pageable) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return Page.empty(pageable);
        }
        return photoRepository.findSharedWithUser(userId, pageable)
                .map(entityMapper::toPhotoDTO);
    }

    /**
     * Get recent public photos for the home page gallery.
     *
     * @param pageable Pagination parameters
     * @return List of recent public photo DTOs
     */
    @Transactional(readOnly = true)
    public List<PhotoDTO> getRecentPublicPhotos(Pageable pageable) {
        return entityMapper.toPhotoDTOList(
                photoRepository.findRecentPublicPhotos(pageable)
        );
    }

    /**
     * Search photos by title.
     *
     * @param searchTerm The search term
     * @param authentication Current user's authentication
     * @param pageable Pagination parameters
     * @return Page of matching photo DTOs
     */
    @Transactional(readOnly = true)
    public Page<PhotoDTO> searchPhotos(String searchTerm, Authentication authentication, Pageable pageable) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            userId = 0L; // For anonymous users, only show public photos
        }
        return photoRepository.searchByTitle(userId, searchTerm, pageable)
                .map(entityMapper::toPhotoDTO);
    }

    // =========================================
    // PHOTO UPDATE
    // =========================================

    /**
     * Update a photo's metadata.
     *
     * @param photoId The photo ID
     * @param title New title (null to keep existing)
     * @param description New description (null to keep existing)
     * @param visibility New visibility (null to keep existing)
     * @return Updated photo DTO
     * @throws IllegalArgumentException If photo not found
     */
    public PhotoDTO updatePhoto(Long photoId, String title, String description, Visibility visibility) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        if (title != null && !title.isBlank()) {
            photo.setTitle(title.trim());
        }
        if (description != null) {
            photo.setDescription(description.trim());
        }
        if (visibility != null) {
            photo.setVisibility(visibility);
        }

        photo = photoRepository.save(photo);
        logger.info("Photo updated: id={}", photoId);

        return entityMapper.toPhotoDTO(photo);
    }

    /**
     * Change a photo's visibility.
     *
     * @param photoId The photo ID
     * @param visibility New visibility setting
     * @return Updated photo DTO
     */
    public PhotoDTO updateVisibility(Long photoId, Visibility visibility) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        photo.setVisibility(visibility);
        photo = photoRepository.save(photo);

        logger.info("Photo visibility updated: id={}, visibility={}", photoId, visibility);

        return entityMapper.toPhotoDTO(photo);
    }

    // =========================================
    // PHOTO DELETION
    // =========================================

    /**
     * Delete a photo and its associated file.
     *
     * @param photoId The photo ID to delete
     */
    public void deletePhoto(Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        String storageFilename = photo.getStorageFilename();

        // Delete from database first
        photoRepository.delete(photo);

        // Then delete the file
        if (storageFilename != null) {
            boolean deleted = fileStorageService.deletePhoto(storageFilename);
            if (!deleted) {
                logger.warn("Failed to delete photo file: {}", storageFilename);
            }
        }

        logger.info("Photo deleted: id={}", photoId);
    }

    /**
     * Delete all photos owned by a user.
     * Used when deleting a user account.
     *
     * @param userId The owner's user ID
     */
    public void deleteAllPhotosByOwner(Long userId) {
        List<Photo> photos = photoRepository.findByOwnerIdOrderByCreatedAtDesc(userId);

        for (Photo photo : photos) {
            if (photo.getStorageFilename() != null) {
                fileStorageService.deletePhoto(photo.getStorageFilename());
            }
        }

        photoRepository.deleteByOwnerId(userId);
        logger.info("Deleted all photos for user: id={}", userId);
    }

    // =========================================
    // ALBUM OPERATIONS
    // =========================================

    /**
     * Add a photo to an album.
     *
     * @param photoId The photo ID
     * @param albumId The album ID
     * @param authentication Current user's authentication
     */
    public void addPhotoToAlbum(Long photoId, Long albumId, Authentication authentication) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album non trouvé"));

        // Verify the user owns both the photo and the album
        Long userId = securityService.getCurrentUserId(authentication);
        if (!photo.getOwner().getId().equals(userId) || !album.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("Vous ne pouvez ajouter que vos propres photos à vos propres albums");
        }

        album.addPhoto(photo);
        albumRepository.save(album);

        logger.info("Photo added to album: photoId={}, albumId={}", photoId, albumId);
    }

    /**
     * Remove a photo from an album.
     *
     * @param photoId The photo ID
     * @param albumId The album ID
     */
    public void removePhotoFromAlbum(Long photoId, Long albumId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album non trouvé"));

        album.removePhoto(photo);
        albumRepository.save(album);

        logger.info("Photo removed from album: photoId={}, albumId={}", photoId, albumId);
    }

    // =========================================
    // STATISTICS
    // =========================================

    /**
     * Count photos owned by a user.
     *
     * @param userId The owner's user ID
     * @return Number of photos
     */
    @Transactional(readOnly = true)
    public long countPhotosByOwner(Long userId) {
        return photoRepository.countByOwnerId(userId);
    }

    /**
     * Count shares for a photo.
     *
     * @param photoId The photo ID
     * @return Number of shares
     */
    @Transactional(readOnly = true)
    public long countSharesByPhoto(Long photoId) {
        return shareRepository.countByPhotoId(photoId);
    }
}