package com.fotoshare.integration;

import com.fotoshare.entity.Photo;
import com.fotoshare.entity.User;
import com.fotoshare.enums.Role;
import com.fotoshare.enums.Visibility;
import com.fotoshare.repository.PhotoRepository;
import com.fotoshare.repository.UserRepository;
import com.fotoshare.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the complete photo upload flow.
 * 
 * Tests:
 * 1. File upload via HTTP endpoint
 * 2. File saved to disk (both original and thumbnail)
 * 3. Database entry created with correct metadata
 * 4. Proper validation (MIME type, file size)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "fotoshare.upload.path=target/test-uploads/photos",
    "fotoshare.thumbnail.path=target/test-uploads/thumbnails"
})
@Transactional
public class PhotoUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User testUser;
    private Path photoStoragePath;
    private Path thumbnailStoragePath;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = User.builder()
                .username("testuser")
                .email("testuser@example.com")
                .passwordHash("$2a$10$dummyHashForTesting")
                .role(Role.USER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        testUser = userRepository.save(testUser);

        // Setup storage paths
        photoStoragePath = Paths.get("target/test-uploads/photos").toAbsolutePath().normalize();
        thumbnailStoragePath = Paths.get("target/test-uploads/thumbnails").toAbsolutePath().normalize();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test files
        if (Files.exists(photoStoragePath)) {
            Files.walk(photoStoragePath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
        if (Files.exists(thumbnailStoragePath)) {
            Files.walk(thumbnailStoragePath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Test
    @WithMockUser(username = "testuser")
    void testCompletePhotoUploadFlow() throws Exception {
        // ARRANGE: Create a valid JPEG file
        byte[] imageBytes = createValidJpegImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-photo.jpg",
                "image/jpeg",
                imageBytes
        );

        String title = "Integration Test Photo";
        String description = "This is a test photo for integration testing";

        long photosCountBefore = photoRepository.count();

        // ACT: Upload the photo via HTTP endpoint
        mockMvc.perform(multipart("/photos/upload")
                        .file(file)
                        .param("title", title)
                        .param("description", description)
                        .param("visibility", "PUBLIC")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/photos/view/*"));

        // ASSERT: Verify database entry was created
        long photosCountAfter = photoRepository.count();
        assertThat(photosCountAfter).isEqualTo(photosCountBefore + 1);

        // Find the newly created photo
        Optional<Photo> photoOpt = photoRepository.findAll().stream()
                .filter(p -> title.equals(p.getTitle()))
                .findFirst();

        assertThat(photoOpt).isPresent();
        Photo savedPhoto = photoOpt.get();

        // Verify photo metadata
        assertThat(savedPhoto.getTitle()).isEqualTo(title);
        assertThat(savedPhoto.getDescription()).isEqualTo(description);
        assertThat(savedPhoto.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(savedPhoto.getOriginalFilename()).isEqualTo("test-photo.jpg");
        assertThat(savedPhoto.getContentType()).isEqualTo("image/jpeg");
        assertThat(savedPhoto.getStorageFilename()).isNotNull();
        assertThat(savedPhoto.getStorageFilename()).isNotEmpty();
        assertThat(savedPhoto.getOwner().getId()).isEqualTo(testUser.getId());
        assertThat(savedPhoto.getCreatedAt()).isNotNull();

        // ASSERT: Verify file exists on disk
        Path originalFilePath = photoStoragePath.resolve(savedPhoto.getStorageFilename());
        assertThat(Files.exists(originalFilePath))
                .as("Original photo file should exist on disk")
                .isTrue();
        assertThat(Files.isRegularFile(originalFilePath))
                .as("Original photo should be a regular file")
                .isTrue();
        assertThat(Files.size(originalFilePath))
                .as("Original photo file should not be empty")
                .isGreaterThan(0);

        // ASSERT: Verify thumbnail exists on disk
        String thumbnailFilename = "thumb_" + savedPhoto.getStorageFilename();
        Path thumbnailFilePath = thumbnailStoragePath.resolve(thumbnailFilename);
        assertThat(Files.exists(thumbnailFilePath))
                .as("Thumbnail file should exist on disk")
                .isTrue();
        assertThat(Files.isRegularFile(thumbnailFilePath))
                .as("Thumbnail should be a regular file")
                .isTrue();
        assertThat(Files.size(thumbnailFilePath))
                .as("Thumbnail file should not be empty")
                .isGreaterThan(0);
    }

    @Test
    @WithMockUser(username = "testuser")
    void testPhotoUploadWithInvalidMimeType() throws Exception {
        // ARRANGE: Create a file with invalid content type
        byte[] textBytes = "This is not an image".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.jpg", // Pretends to be image
                "image/jpeg",    // Declares as image
                textBytes        // But content is text
        );

        long photosCountBefore = photoRepository.count();

        // ACT & ASSERT: Upload should fail
        mockMvc.perform(multipart("/photos/upload")
                        .file(file)
                        .param("title", "Invalid Photo")
                        .param("description", "Should fail")
                        .param("visibility", "PRIVATE")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        // Verify no photo was created in database
        long photosCountAfter = photoRepository.count();
        assertThat(photosCountAfter).isEqualTo(photosCountBefore);
    }

    @Test
    @WithMockUser(username = "testuser")
    void testPhotoUploadWithOversizedFile() throws Exception {
        // ARRANGE: Create a file that exceeds size limit (> 10MB)
        byte[] largeImageBytes = new byte[11 * 1024 * 1024]; // 11 MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large-photo.jpg",
                "image/jpeg",
                largeImageBytes
        );

        long photosCountBefore = photoRepository.count();

        // ACT & ASSERT: Upload should fail
        mockMvc.perform(multipart("/photos/upload")
                        .file(file)
                        .param("title", "Large Photo")
                        .param("description", "Too big")
                        .param("visibility", "PRIVATE")
                        .with(csrf()))
                .andExpect(status().is4xxClientError()); // Should return 400 or similar

        // Verify no photo was created in database
        long photosCountAfter = photoRepository.count();
        assertThat(photosCountAfter).isEqualTo(photosCountBefore);
    }

    @Test
    @WithMockUser(username = "testuser")
    void testPhotoUploadCreatesUniqueStorageFilename() throws Exception {
        // ARRANGE: Create two photos with the same original filename
        byte[] imageBytes1 = createValidJpegImage();
        byte[] imageBytes2 = createValidJpegImage();

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "duplicate.jpg",
                "image/jpeg",
                imageBytes1
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "duplicate.jpg", // Same filename
                "image/jpeg",
                imageBytes2
        );

        // ACT: Upload both photos
        mockMvc.perform(multipart("/photos/upload")
                        .file(file1)
                        .param("title", "Photo 1")
                        .param("visibility", "PRIVATE")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(multipart("/photos/upload")
                        .file(file2)
                        .param("title", "Photo 2")
                        .param("visibility", "PRIVATE")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // ASSERT: Both photos should have different storage filenames
        Optional<Photo> photo1 = photoRepository.findAll().stream()
                .filter(p -> "Photo 1".equals(p.getTitle()))
                .findFirst();
        Optional<Photo> photo2 = photoRepository.findAll().stream()
                .filter(p -> "Photo 2".equals(p.getTitle()))
                .findFirst();

        assertThat(photo1).isPresent();
        assertThat(photo2).isPresent();
        assertThat(photo1.get().getStorageFilename())
                .isNotEqualTo(photo2.get().getStorageFilename());
        assertThat(photo1.get().getOriginalFilename())
                .isEqualTo(photo2.get().getOriginalFilename());
    }

    /**
     * Creates a minimal valid JPEG image for testing.
     * This is a 1x1 pixel red JPEG image.
     */
    private byte[] createValidJpegImage() {
        // Minimal valid JPEG file (1x1 red pixel)
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x48,
                0x00, 0x48, 0x00, 0x00, (byte) 0xFF, (byte) 0xDB, 0x00, 0x43,
                0x00, 0x03, 0x02, 0x02, 0x02, 0x02, 0x02, 0x03,
                0x02, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x04,
                0x06, 0x04, 0x04, 0x04, 0x04, 0x04, 0x08, 0x06,
                0x06, 0x05, 0x06, 0x09, 0x08, 0x0A, 0x0A, 0x09,
                0x08, 0x09, 0x09, 0x0A, 0x0C, 0x0F, 0x0C, 0x0A,
                0x0B, 0x0E, 0x0B, 0x09, 0x09, 0x0D, 0x11, 0x0D,
                0x0E, 0x0F, 0x10, 0x10, 0x11, 0x10, 0x0A, 0x0C,
                0x12, 0x13, 0x12, 0x10, 0x13, 0x0F, 0x10, 0x10,
                0x10, (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01,
                0x00, 0x01, 0x01, 0x01, 0x11, 0x00, (byte) 0xFF, (byte) 0xC4,
                0x00, 0x14, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xC4, 0x00, 0x14,
                0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01,
                0x00, 0x00, 0x3F, 0x00, (byte) 0x7F, (byte) 0xFF, (byte) 0xD9
        };
    }
}