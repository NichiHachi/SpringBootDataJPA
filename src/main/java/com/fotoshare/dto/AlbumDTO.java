package com.fotoshare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for Album entity.
 * Used for transferring album data between presentation and service layers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlbumDTO {

    private Long id;

    @NotBlank(message = "Le nom de l'album est obligatoire")
    @Size(min = 1, max = 100, message = "Le nom doit contenir entre 1 et 100 caractères")
    private String name;

    @Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères")
    private String description;

    private Long ownerId;
    private String ownerUsername;

    private LocalDateTime createdAt;

    @Builder.Default
    private List<PhotoDTO> photos = new ArrayList<>();

    private int photoCount;

    /**
     * Static factory method to create an empty AlbumDTO for forms.
     */
    public static AlbumDTO empty() {
        return AlbumDTO.builder()
                .photos(new ArrayList<>())
                .build();
    }
}