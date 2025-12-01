package com.fotoshare.dto;

import com.fotoshare.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO for uploading a new photo.
 * Used in the presentation layer to transfer upload data from the form.
 * 
 * This DTO validates:
 * - Title is required and has a max length
 * - File is required
 * - Visibility defaults to PRIVATE
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoUploadDTO {

    /**
     * Title of the photo (required).
     */
    @NotBlank(message = "Le titre est obligatoire")
    @Size(min = 1, max = 100, message = "Le titre doit contenir entre 1 et 100 caractères")
    private String title;

    /**
     * Optional description of the photo.
     */
    @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
    private String description;

    /**
     * The photo file to upload (required).
     * File validation (MIME type, size) is handled by the service layer.
     */
    @NotNull(message = "Veuillez sélectionner une image")
    private MultipartFile file;

    /**
     * Visibility level of the photo.
     * Defaults to PRIVATE if not specified.
     */
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    /**
     * Optional album ID to add the photo to upon upload.
     */
    private Long albumId;
}