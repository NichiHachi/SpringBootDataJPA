package com.fotoshare.mapper;

import com.fotoshare.dto.*;
import com.fotoshare.entity.*;
import com.fotoshare.enums.PermissionLevel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper component for converting between JPA entities and DTOs.
 * This ensures that entities never leak to the presentation layer,
 * following N-Tier architecture best practices.
 */
@Component
public class EntityMapper {

    // =========================================
    // USER MAPPINGS
    // =========================================

    /**
     * Convert User entity to UserDTO.
     */
    public UserDTO toUserDTO(User user) {
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .photoCount(user.getPhotos() != null ? (long) user.getPhotos().size() : 0L)
                .albumCount(user.getAlbums() != null ? (long) user.getAlbums().size() : 0L)
                .build();
    }

    /**
     * Convert User entity to UserDTO with explicit counts.
     */
    public UserDTO toUserDTO(User user, Long photoCount, Long albumCount) {
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .photoCount(photoCount != null ? photoCount : 0L)
                .albumCount(albumCount != null ? albumCount : 0L)
                .build();
    }

    /**
     * Convert list of User entities to UserDTOs.
     */
    public List<UserDTO> toUserDTOList(List<User> users) {
        if (users == null) {
            return Collections.emptyList();
        }
        return users.stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create User entity from UserRegistrationDTO.
     * Note: Password hashing should be done in the service layer.
     */
    public User toUserEntity(UserRegistrationDTO dto) {
        if (dto == null) {
            return null;
        }
        return User.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .build();
    }

    // =========================================
    // PHOTO MAPPINGS
    // =========================================

    /**
     * Convert Photo entity to PhotoDTO.
     */
    public PhotoDTO toPhotoDTO(Photo photo) {
        if (photo == null) {
            return null;
        }
        return PhotoDTO.builder()
                .id(photo.getId())
                .title(photo.getTitle())
                .description(photo.getDescription())
                .originalFilename(photo.getOriginalFilename())
                .storageFilename(photo.getStorageFilename())
                .contentType(photo.getContentType())
                .visibility(photo.getVisibility())
                .ownerId(photo.getOwner() != null ? photo.getOwner().getId() : null)
                .ownerUsername(photo.getOwner() != null ? photo.getOwner().getUsername() : null)
                .createdAt(photo.getCreatedAt())
                .commentCount(photo.getComments() != null ? photo.getComments().size() : 0)
                .shareCount(photo.getShares() != null ? photo.getShares().size() : 0)
                .photoUrl("/photos/view/" + photo.getId() + "/image")
                .thumbnailUrl("/photos/view/" + photo.getId() + "/thumbnail")
                .build();
    }

    /**
     * Convert Photo entity to PhotoDTO with permission flags.
     */
    public PhotoDTO toPhotoDTO(Photo photo, boolean canEdit, boolean canDelete, boolean canComment) {
        PhotoDTO dto = toPhotoDTO(photo);
        if (dto != null) {
            dto.setCanEdit(canEdit);
            dto.setCanDelete(canDelete);
            dto.setCanComment(canComment);
        }
        return dto;
    }

    /**
     * Convert list of Photo entities to PhotoDTOs.
     */
    public List<PhotoDTO> toPhotoDTOList(List<Photo> photos) {
        if (photos == null) {
            return Collections.emptyList();
        }
        return photos.stream()
                .map(this::toPhotoDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create Photo entity from PhotoUploadDTO.
     * Note: File handling should be done in the service layer.
     */
    public Photo toPhotoEntity(PhotoUploadDTO dto) {
        if (dto == null) {
            return null;
        }
        return Photo.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .visibility(dto.getVisibility())
                .build();
    }

    // =========================================
    // ALBUM MAPPINGS
    // =========================================

    /**
     * Convert Album entity to AlbumDTO.
     */
    public AlbumDTO toAlbumDTO(Album album) {
        if (album == null) {
            return null;
        }
        return AlbumDTO.builder()
                .id(album.getId())
                .name(album.getName())
                .description(album.getDescription())
                .ownerId(album.getOwner() != null ? album.getOwner().getId() : null)
                .ownerUsername(album.getOwner() != null ? album.getOwner().getUsername() : null)
                .createdAt(album.getCreatedAt())
                .photoCount(album.getPhotos() != null ? album.getPhotos().size() : 0)
                .build();
    }

    /**
     * Convert Album entity to AlbumDTO with photos.
     */
    public AlbumDTO toAlbumDTOWithPhotos(Album album) {
        AlbumDTO dto = toAlbumDTO(album);
        if (dto != null && album.getPhotos() != null) {
            dto.setPhotos(toPhotoDTOList(album.getPhotos().stream().toList()));
        }
        return dto;
    }

    /**
     * Convert list of Album entities to AlbumDTOs.
     */
    public List<AlbumDTO> toAlbumDTOList(List<Album> albums) {
        if (albums == null) {
            return Collections.emptyList();
        }
        return albums.stream()
                .map(this::toAlbumDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create Album entity from AlbumDTO.
     */
    public Album toAlbumEntity(AlbumDTO dto) {
        if (dto == null) {
            return null;
        }
        return Album.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
    }

    // =========================================
    // COMMENT MAPPINGS
    // =========================================

    /**
     * Convert Comment entity to CommentDTO.
     */
    public CommentDTO toCommentDTO(Comment comment) {
        if (comment == null) {
            return null;
        }
        return CommentDTO.builder()
                .id(comment.getId())
                .text(comment.getText())
                .photoId(comment.getPhoto() != null ? comment.getPhoto().getId() : null)
                .authorId(comment.getAuthor() != null ? comment.getAuthor().getId() : null)
                .authorUsername(comment.getAuthor() != null ? comment.getAuthor().getUsername() : null)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    /**
     * Convert list of Comment entities to CommentDTOs.
     */
    public List<CommentDTO> toCommentDTOList(List<Comment> comments) {
        if (comments == null) {
            return Collections.emptyList();
        }
        return comments.stream()
                .map(this::toCommentDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create Comment entity from CommentDTO.
     */
    public Comment toCommentEntity(CommentDTO dto) {
        if (dto == null) {
            return null;
        }
        return Comment.builder()
                .text(dto.getText())
                .build();
    }

    // =========================================
    // SHARE MAPPINGS
    // =========================================

    /**
     * Convert Share entity to ShareDTO.
     */
    public ShareDTO toShareDTO(Share share) {
        if (share == null) {
            return null;
        }
        return ShareDTO.builder()
                .id(share.getId())
                .photoId(share.getPhoto() != null ? share.getPhoto().getId() : null)
                .userId(share.getUser() != null ? share.getUser().getId() : null)
                .username(share.getUser() != null ? share.getUser().getUsername() : null)
                .permissionLevel(share.getPermissionLevel())
                .createdAt(share.getCreatedAt())
                .photoTitle(share.getPhoto() != null ? share.getPhoto().getTitle() : null)
                .photoThumbnailUrl(share.getPhoto() != null ? 
                        "/photos/view/" + share.getPhoto().getId() + "/thumbnail" : null)
                .build();
    }

    /**
     * Convert list of Share entities to ShareDTOs.
     */
    public List<ShareDTO> toShareDTOList(List<Share> shares) {
        if (shares == null) {
            return Collections.emptyList();
        }
        return shares.stream()
                .map(this::toShareDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create Share entity from ShareDTO.
     */
    public Share toShareEntity(ShareDTO dto) {
        if (dto == null) {
            return null;
        }
        return Share.builder()
                .permissionLevel(dto.getPermissionLevel() != null ? 
                        dto.getPermissionLevel() : PermissionLevel.READ)
                .build();
    }
}