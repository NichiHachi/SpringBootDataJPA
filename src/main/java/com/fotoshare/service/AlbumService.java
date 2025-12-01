package com.fotoshare.service;

import com.fotoshare.dto.AlbumDTO;
import com.fotoshare.dto.PhotoDTO;
import com.fotoshare.entity.Album;
import com.fotoshare.entity.Photo;
import com.fotoshare.entity.User;
import com.fotoshare.mapper.EntityMapper;
import com.fotoshare.repository.AlbumRepository;
import com.fotoshare.repository.PhotoRepository;
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
 * Service for managing photo albums.
 * 
 * Business Logic Layer responsibilities:
 * - Album CRUD operations
 * - Adding/removing photos from albums
 * - Album ownership verification
 * - Transaction management
 * 
 * This service ensures proper access control through the SecurityService
 * and uses DTOs for external communication following N-Tier architecture.
 */
@Service
@Transactional
public class AlbumService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumService.class);

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final SecurityService securityService;
    private final EntityMapper entityMapper;

    public AlbumService(AlbumRepository albumRepository,
                        PhotoRepository photoRepository,
                        SecurityService securityService,
                        EntityMapper entityMapper) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.securityService = securityService;
        this.entityMapper = entityMapper;
    }

    // =========================================
    // ALBUM CREATION
    // =========================================

    /**
     * Create a new album for the current user.
     *
     * @param albumDTO The album data
     * @param authentication Current user's authentication
     * @return The created album as DTO
     * @throws IllegalArgumentException If validation fails
     */
    public AlbumDTO createAlbum(AlbumDTO albumDTO, Authentication authentication) {
        logger.info("Creating album '{}' for user: {}", albumDTO.getName(), authentication.getName());

        User owner = securityService.getCurrentUser(authentication)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Check if album with same name already exists for this user
        if (albumRepository.existsByNameAndOwnerId(albumDTO.getName(), owner.getId())) {
            throw new IllegalArgumentException("Un album avec ce nom existe déjà");
        }

        Album album = Album.builder()
                .name(albumDTO.getName().trim())
                .description(albumDTO.getDescription() != null ? albumDTO.getDescription().trim() : null)
                .owner(owner)
                .build();

        album = albumRepository.save(album);
        logger.info("Album created successfully: id={}, name={}", album.getId(), album.getName());

        return entityMapper.toAlbumDTO(album);
    }

    // =========================================
    // ALBUM RETRIEVAL
    // =========================================

    /**
     * Get an album by ID.
     *
     * @param albumId The album ID
     * @return Optional containing the album DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<AlbumDTO> getAlbumById(Long albumId) {
        return albumRepository.findById(albumId)
                .map(entityMapper::toAlbumDTO);
    }

    /**
     * Get an album by ID with its photos.
     *
     * @param albumId The album ID
     * @return Optional containing the album DTO with photos if found
     */
    @Transactional(readOnly = true)
    public Optional<AlbumDTO> getAlbumWithPhotos(Long albumId) {
        return albumRepository.findByIdWithPhotos(albumId)
                .map(entityMapper::toAlbumDTOWithPhotos);
    }

    /**
     * Get an album entity by ID (for internal use).
     *
     * @param albumId The album ID
     * @return Optional containing the album entity if found
     */
    @Transactional(readOnly = true)
    public Optional<Album> getAlbumEntityById(Long albumId) {
        return albumRepository.findById(albumId);
    }

    /**
     * Get all albums owned by a user (paginated).
     *
     * @param userId The owner's user ID
     * @param pageable Pagination parameters
     * @return Page of album DTOs
     */
    @Transactional(readOnly = true)
    public Page<AlbumDTO> getAlbumsByOwner(Long userId, Pageable pageable) {
        return albumRepository.findByOwnerIdOrderByCreatedAtDesc(userId, pageable)
                .map(entityMapper::toAlbumDTO);
    }

    /**
     * Get all albums owned by the current user (paginated).
     *
     * @param authentication Current user's authentication
     * @param pageable Pagination parameters
     * @return Page of album DTOs
     */
    @Transactional(readOnly = true)
    public Page<AlbumDTO> getMyAlbums(Authentication authentication, Pageable pageable) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return Page.empty(pageable);
        }
        return getAlbumsByOwner(userId, pageable);
    }

    /**
     * Get all albums owned by the current user (unpaged).
     *
     * @param authentication Current user's authentication
     * @return List of album DTOs
     */
    @Transactional(readOnly = true)
    public List<AlbumDTO> getMyAlbumsList(Authentication authentication) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return List.of();
        }
        return entityMapper.toAlbumDTOList(
                albumRepository.findByOwnerIdOrderByCreatedAtDesc(userId)
        );
    }

    /**
     * Search albums by name for the current user.
     *
     * @param keyword Search keyword
     * @param authentication Current user's authentication
     * @return List of matching album DTOs
     */
    @Transactional(readOnly = true)
    public List<AlbumDTO> searchAlbums(String keyword, Authentication authentication) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return List.of();
        }
        return entityMapper.toAlbumDTOList(
                albumRepository.searchByNameAndOwnerId(keyword, userId)
        );
    }

    // =========================================
    // ALBUM UPDATE
    // =========================================

    /**
     * Update an album's metadata.
     *
     * @param albumId The album ID
     * @param name New name (null to keep existing)
     * @param description New description (null to keep existing)
     * @param authentication Current user's authentication
     * @return Updated album DTO
     * @throws IllegalArgumentException If album not found or validation fails
     */
    public AlbumDTO updateAlbum(Long albumId, String name, String description, Authentication authentication) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album non trouvé"));

        Long userId = securityService.getCurrentUserId(authentication);
        
        // Verify ownership (or admin rights)
        if (!album.getOwner().getId().equals(userId) && !securityService.isAdmin(authentication)) {
            throw new IllegalArgumentException("Vous n'êtes pas autorisé à modifier cet album");
        }

        if (name != null && !name.isBlank()) {
            String trimmedName = name.trim();
            // Check for name collision with other albums
            if (!trimmedName.equals(album.getName()) && 
                albumRepository.existsByNameAndOwnerId(trimmedName, album.getOwner().getId())) {
                throw new IllegalArgumentException("Un album avec ce nom existe déjà");
            }
            album.setName(trimmedName);
        }

        if (description != null) {
            album.setDescription(description.trim());
        }

        album = albumRepository.save(album);
        logger.info("Album updated: id={}", albumId);

        return entityMapper.toAlbumDTO(album);
    }

    // =========================================
    // ALBUM DELETION
    // =========================================

    /**
     * Delete an album.
     * Note: This does not delete the photos in the album, only the album itself.
     *
     * @param albumId The album ID to delete
     * @param authentication Current user's authentication
     * @throws IllegalArgumentException If album not found or user not authorized
     */
    public void deleteAlbum(Long albumId, Authentication authentication) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album non trouvé"));

        Long userId = securityService.getCurrentUserId(authentication);

        // Verify ownership (or admin rights)
        if (!album.getOwner().getId().equals(userId) && !securityService.isAdmin(authentication)) {
            throw new IllegalArgumentException("Vous n'êtes pas autorisé à supprimer cet album");
        }

        albumRepository.delete(album);
        logger.info("Album deleted: id={}", albumId);
    }

    /**
     * Delete all albums owned by a user.
     * Used when deleting a user account.
     *
     * @param userId The owner's user ID
     */
    public void deleteAllAlbumsByOwner(Long userId) {
        albumRepository.deleteAllByOwnerId(userId);
        logger.info("Deleted all albums for user: id={}", userId);
    }

    // =========================================
    // PHOTO MANAGEMENT IN ALBUMS
    // =========================================

    /**
     * Add a photo to an album.
     *
     * @param albumId The album ID
     * @param photoId The photo ID
     * @param authentication Current user's authentication
     * @throws IllegalArgumentException If album/photo not found or user not authorized
     */
    public void addPhotoToAlbum(Long albumId, Long photoId, Authentication authentication) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album non trouvé"));

        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        Long userId = securityService.getCurrentUserId(authentication);

        // Verify the user owns both the album and the photo
        if (!album.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("Vous n'êtes pas propriétaire de cet album");
        }
        if (!photo.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("Vous n'êtes pas propriétaire de cette photo");
        }

        // Check if photo is already in the album
        if (album.getPhotos().contains(photo)) {
            throw new IllegalArgumentException("Cette photo est déjà dans l'album");
        }

        album.addPhoto(photo);
        albumRepository.save(album);

        logger.info("Photo added to album: albumId={}, photoId={}", albumId, photoId);
    }

    /**
     * Add multiple photos to an album.
     *
     * @param albumId The album ID
     * @param photoIds List of photo IDs to add
     * @param authentication Current user's authentication
     * @return Number of photos successfully added
     */
    public int addPhotosToAlbum(Long albumId, List<Long> photoIds, Authentication authentication) {
        int addedCount = 0;
        for (Long photoId : photoIds) {
            try {
                addPhotoToAlbum(albumId, photoId, authentication);
                addedCount++;
            } catch (IllegalArgumentException e) {
                logger.warn("Failed to add photo {} to album {}: {}", photoId, albumId, e.getMessage());
            }
        }
        return addedCount;
    }

    /**
     * Remove a photo from an album.
     *
     * @param albumId The album ID
     * @param photoId The photo ID
     * @param authentication Current user's authentication
     * @throws IllegalArgumentException If album/photo not found or user not authorized
     */
    public void removePhotoFromAlbum(Long albumId, Long photoId, Authentication authentication) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album non trouvé"));

        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        Long userId = securityService.getCurrentUserId(authentication);

        // Verify the user owns the album
        if (!album.getOwner().getId().equals(userId) && !securityService.isAdmin(authentication)) {
            throw new IllegalArgumentException("Vous n'êtes pas autorisé à modifier cet album");
        }

        album.removePhoto(photo);
        albumRepository.save(album);

        logger.info("Photo removed from album: albumId={}, photoId={}", albumId, photoId);
    }

    /**
     * Get all photos in an album.
     *
     * @param albumId The album ID
     * @return List of photo DTOs in the album
     */
    @Transactional(readOnly = true)
    public List<PhotoDTO> getPhotosInAlbum(Long albumId) {
        return albumRepository.findByIdWithPhotos(albumId)
                .map(album -> entityMapper.toPhotoDTOList(album.getPhotos().stream().toList()))
                .orElse(List.of());
    }

    // =========================================
    // STATISTICS
    // =========================================

    /**
     * Count albums owned by a user.
     *
     * @param userId The owner's user ID
     * @return Number of albums
     */
    @Transactional(readOnly = true)
    public long countAlbumsByOwner(Long userId) {
        return albumRepository.countByOwnerId(userId);
    }

    /**
     * Get the number of photos in an album.
     *
     * @param albumId The album ID
     * @return Number of photos in the album
     */
    @Transactional(readOnly = true)
    public int getPhotoCountInAlbum(Long albumId) {
        return albumRepository.findById(albumId)
                .map(Album::getPhotoCount)
                .orElse(0);
    }

    // =========================================
    // VALIDATION
    // =========================================

    /**
     * Check if the current user owns an album.
     *
     * @param albumId The album ID
     * @param authentication Current user's authentication
     * @return true if the user owns the album
     */
    @Transactional(readOnly = true)
    public boolean isAlbumOwner(Long albumId, Authentication authentication) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return false;
        }
        return albumRepository.findByIdAndOwnerId(albumId, userId).isPresent();
    }

    /**
     * Check if an album name is available for a user.
     *
     * @param name The album name to check
     * @param authentication Current user's authentication
     * @return true if the name is available
     */
    @Transactional(readOnly = true)
    public boolean isAlbumNameAvailable(String name, Authentication authentication) {
        Long userId = securityService.getCurrentUserId(authentication);
        if (userId == null) {
            return true;
        }
        return !albumRepository.existsByNameAndOwnerId(name.trim(), userId);
    }
}