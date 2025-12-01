package com.fotoshare.dto;

import com.fotoshare.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for transferring photo data between layers.
 * Used to prevent JPA entities from leaking to the presentation layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoDTO {

    private Long id;
    
    private String title;
    
    private String description;
    
    private String originalFilename;
    
    private String storageFilename;
    
    private String contentType;
    
    private Visibility visibility;
    
    private Long ownerId;
    
    private String ownerUsername;
    
    private LocalDateTime createdAt;
    
    private int commentCount;
    
    private int shareCount;
    
    /**
     * URL to access the photo (generated at runtime)
     */
    private String photoUrl;
    
    /**
     * URL to access the thumbnail (generated at runtime)
     */
    private String thumbnailUrl;
    
    /**
     * Indicates if the current user can edit this photo
     */
    private boolean canEdit;
    
    /**
     * Indicates if the current user can delete this photo
     */
    private boolean canDelete;
    
    /**
     * Indicates if the current user can comment on this photo
     */
    private boolean canComment;
}