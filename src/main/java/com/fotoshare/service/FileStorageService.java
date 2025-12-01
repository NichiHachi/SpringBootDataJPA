package com.fotoshare.service;

import jakarta.annotation.PostConstruct;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling file storage operations.
 * 
 * Responsibilities:
 * - Store uploaded photos securely with UUID filenames
 * - Generate thumbnails for efficient gallery display
 * - Validate file types using Magic Numbers (not just extensions)
 * - Serve files for download
 * - Delete files when photos are removed
 * 
 * Security features:
 * - Files are stored outside the web root
 * - Original filenames are replaced with UUIDs
 * - MIME type verification using Apache Tika
 * - Maximum file size enforcement
 */
@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    // Allowed MIME types for photo uploads
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    // Maximum file size: 10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // Thumbnail dimensions
    private static final int THUMBNAIL_WIDTH = 300;
    private static final int THUMBNAIL_HEIGHT = 300;

    private final Tika tika = new Tika();

    @Value("${fotoshare.upload.path:uploads/photos}")
    private String uploadPath;

    @Value("${fotoshare.thumbnail.path:uploads/thumbnails}")
    private String thumbnailPath;

    private Path photoStoragePath;
    private Path thumbnailStoragePath;

    /**
     * Initialize storage directories on application startup.
     */
    @PostConstruct
    public void init() {
        try {
            photoStoragePath = Paths.get(uploadPath).toAbsolutePath().normalize();
            thumbnailStoragePath = Paths.get(thumbnailPath).toAbsolutePath().normalize();

            Files.createDirectories(photoStoragePath);
            Files.createDirectories(thumbnailStoragePath);

            logger.info("Photo storage initialized at: {}", photoStoragePath);
            logger.info("Thumbnail storage initialized at: {}", thumbnailStoragePath);
        } catch (IOException e) {
            logger.error("Could not create storage directories", e);
            throw new RuntimeException("Could not create storage directories", e);
        }
    }

    /**
     * Store a photo file and generate its thumbnail.
     *
     * @param file The uploaded file
     * @return The storage filename (UUID-based)
     * @throws IOException If file operations fail
     * @throws IllegalArgumentException If file validation fails
     */
    public StorageResult storePhoto(MultipartFile file) throws IOException {
        // Validate file
        validateFile(file);

        // Generate UUID-based filename
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getFileExtension(originalFilename);
        String storageFilename = UUID.randomUUID().toString() + extension;

        // Detect actual MIME type using magic numbers
        String contentType = detectContentType(file);

        // Store the original photo
        Path targetPath = photoStoragePath.resolve(storageFilename);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        logger.info("Stored photo: {} -> {}", originalFilename, storageFilename);

        // Generate thumbnail
        String thumbnailFilename = generateThumbnail(targetPath, storageFilename);

        return new StorageResult(
                storageFilename,
                thumbnailFilename,
                contentType,
                originalFilename,
                file.getSize()
        );
    }

    /**
     * Generate a thumbnail for the given photo.
     *
     * @param sourcePath Path to the original photo
     * @param storageFilename The storage filename of the original
     * @return The thumbnail filename
     */
    private String generateThumbnail(Path sourcePath, String storageFilename) throws IOException {
        String thumbnailFilename = "thumb_" + storageFilename;
        Path thumbnailPath = thumbnailStoragePath.resolve(thumbnailFilename);

        try {
            Thumbnails.of(sourcePath.toFile())
                    .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    .keepAspectRatio(true)
                    .toFile(thumbnailPath.toFile());

            logger.info("Generated thumbnail: {}", thumbnailFilename);
            return thumbnailFilename;
        } catch (IOException e) {
            logger.warn("Failed to generate thumbnail for {}: {}", storageFilename, e.getMessage());
            // Return the original file if thumbnail generation fails
            return storageFilename;
        }
    }

    /**
     * Load a photo file as a Resource.
     *
     * @param filename The storage filename
     * @return Resource for streaming the file
     */
    public Resource loadPhoto(String filename) {
        return loadFile(photoStoragePath, filename);
    }

    /**
     * Load a thumbnail file as a Resource.
     *
     * @param filename The thumbnail filename
     * @return Resource for streaming the file
     */
    public Resource loadThumbnail(String filename) {
        // Try to load the thumbnail, fall back to the original if not found
        Resource thumbnail = loadFile(thumbnailStoragePath, "thumb_" + filename);
        if (thumbnail == null || !thumbnail.exists()) {
            return loadPhoto(filename);
        }
        return thumbnail;
    }

    /**
     * Load a file from the specified path.
     */
    private Resource loadFile(Path basePath, String filename) {
        try {
            Path filePath = basePath.resolve(filename).normalize();

            // Security check: ensure the file is within the storage directory
            if (!filePath.startsWith(basePath)) {
                logger.warn("Attempted path traversal attack: {}", filename);
                return null;
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }

            logger.warn("File not found or not readable: {}", filename);
            return null;
        } catch (MalformedURLException e) {
            logger.error("Invalid file path: {}", filename, e);
            return null;
        }
    }

    /**
     * Delete a photo and its thumbnail.
     *
     * @param storageFilename The storage filename
     * @return true if deletion was successful
     */
    public boolean deletePhoto(String storageFilename) {
        boolean photoDeleted = deleteFile(photoStoragePath, storageFilename);
        boolean thumbnailDeleted = deleteFile(thumbnailStoragePath, "thumb_" + storageFilename);

        if (photoDeleted) {
            logger.info("Deleted photo: {}", storageFilename);
        }

        return photoDeleted;
    }

    /**
     * Delete a file from the specified path.
     */
    private boolean deleteFile(Path basePath, String filename) {
        try {
            Path filePath = basePath.resolve(filename).normalize();

            // Security check
            if (!filePath.startsWith(basePath)) {
                logger.warn("Attempted path traversal in delete: {}", filename);
                return false;
            }

            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", filename, e);
            return false;
        }
    }

    /**
     * Validate the uploaded file.
     *
     * @param file The file to validate
     * @throws IllegalArgumentException If validation fails
     */
    private void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide ou non fourni");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("Le fichier dépasse la taille maximale autorisée (%d MB)", MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.contains("..")) {
            throw new IllegalArgumentException("Nom de fichier invalide");
        }

        // Validate MIME type using magic numbers (not just extension)
        String contentType = detectContentType(file);
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Type de fichier non autorisé. Types acceptés: JPEG, PNG, GIF, WebP"
            );
        }
    }

    /**
     * Detect the actual content type of a file using Apache Tika (magic numbers).
     *
     * @param file The file to analyze
     * @return The detected MIME type
     */
    private String detectContentType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        }
    }

    /**
     * Get the file extension from a filename.
     *
     * @param filename The filename
     * @return The extension including the dot, or empty string
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase();
    }

    /**
     * Check if a file exists in storage.
     *
     * @param storageFilename The storage filename
     * @return true if the file exists
     */
    public boolean photoExists(String storageFilename) {
        Path filePath = photoStoragePath.resolve(storageFilename).normalize();
        return Files.exists(filePath) && filePath.startsWith(photoStoragePath);
    }

    /**
     * Get the path to the photo storage directory.
     */
    public Path getPhotoStoragePath() {
        return photoStoragePath;
    }

    /**
     * Get the path to the thumbnail storage directory.
     */
    public Path getThumbnailStoragePath() {
        return thumbnailStoragePath;
    }

    /**
     * Result object containing storage operation details.
     */
    public static class StorageResult {
        private final String storageFilename;
        private final String thumbnailFilename;
        private final String contentType;
        private final String originalFilename;
        private final long fileSize;

        public StorageResult(String storageFilename, String thumbnailFilename,
                             String contentType, String originalFilename, long fileSize) {
            this.storageFilename = storageFilename;
            this.thumbnailFilename = thumbnailFilename;
            this.contentType = contentType;
            this.originalFilename = originalFilename;
            this.fileSize = fileSize;
        }

        public String getStorageFilename() {
            return storageFilename;
        }

        public String getThumbnailFilename() {
            return thumbnailFilename;
        }

        public String getContentType() {
            return contentType;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public long getFileSize() {
            return fileSize;
        }
    }
}