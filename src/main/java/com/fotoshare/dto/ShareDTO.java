package com.fotoshare.dto;

import com.fotoshare.enums.PermissionLevel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for sharing a photo with another user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareDTO {

    private Long id;

    @NotNull(message = "Photo ID is required")
    private Long photoId;

    @NotNull(message = "User ID is required")
    private Long userId;

    private String username;

    @NotNull(message = "Permission level is required")
    @Builder.Default
    private PermissionLevel permissionLevel = PermissionLevel.READ;

    private LocalDateTime createdAt;

    // Photo information for display
    private String photoTitle;
    private String photoThumbnailUrl;

    /**
     * Factory method for creating a share request.
     */
    public static ShareDTO createRequest(Long photoId, Long userId, PermissionLevel permission) {
        return ShareDTO.builder()
                .photoId(photoId)
                .userId(userId)
                .permissionLevel(permission)
                .build();
    }
}