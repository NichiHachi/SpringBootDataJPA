package com.fotoshare.integration;

import com.fotoshare.entity.Comment;
import com.fotoshare.entity.Photo;
import com.fotoshare.entity.User;
import com.fotoshare.enums.Role;
import com.fotoshare.enums.Visibility;
import com.fotoshare.repository.CommentRepository;
import com.fotoshare.repository.PhotoRepository;
import com.fotoshare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for cascade deletion behavior.
 * 
 * Tests:
 * 1. Deleting a user cascades to their owned photos
 * 2. Deleting a user cascades to their comments
 * 3. Deleting a photo cascades to its comments
 * 4. Orphaned entities are properly removed
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "fotoshare.upload.path=target/test-uploads/photos",
    "fotoshare.thumbnail.path=target/test-uploads/thumbnails"
})
@Transactional
public class UserCascadeDeletionIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private CommentRepository commentRepository;

    private User ownerUser;
    private User commenterUser;
    private Photo photo1;
    private Photo photo2;
    private Comment comment1;
    private Comment comment2;
    private Comment comment3;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        commentRepository.deleteAll();
        photoRepository.deleteAll();
        userRepository.deleteAll();

        // Create owner user who will own photos
        ownerUser = User.builder()
                .username("photoowner")
                .email("owner@example.com")
                .passwordHash("$2a$10$dummyHashForTesting")
                .role(Role.USER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        ownerUser = userRepository.save(ownerUser);

        // Create commenter user who will comment on photos
        commenterUser = User.builder()
                .username("commenter")
                .email("commenter@example.com")
                .passwordHash("$2a$10$dummyHashForTesting")
                .role(Role.USER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        commenterUser = userRepository.save(commenterUser);

        // Create photos owned by ownerUser
        photo1 = Photo.builder()
                .title("Photo 1")
                .description("First test photo")
                .originalFilename("photo1.jpg")
                .storageFilename("uuid-photo1.jpg")
                .contentType("image/jpeg")
                .visibility(Visibility.PUBLIC)
                .owner(ownerUser)
                .createdAt(LocalDateTime.now())
                .build();
        photo1 = photoRepository.save(photo1);

        photo2 = Photo.builder()
                .title("Photo 2")
                .description("Second test photo")
                .originalFilename("photo2.jpg")
                .storageFilename("uuid-photo2.jpg")
                .contentType("image/jpeg")
                .visibility(Visibility.PRIVATE)
                .owner(ownerUser)
                .createdAt(LocalDateTime.now())
                .build();
        photo2 = photoRepository.save(photo2);

        // Create comments - some by owner, some by commenter
        comment1 = Comment.builder()
                .text("Owner's comment on photo 1")
                .photo(photo1)
                .author(ownerUser)
                .createdAt(LocalDateTime.now())
                .build();
        comment1 = commentRepository.save(comment1);

        comment2 = Comment.builder()
                .text("Commenter's comment on photo 1")
                .photo(photo1)
                .author(commenterUser)
                .createdAt(LocalDateTime.now())
                .build();
        comment2 = commentRepository.save(comment2);

        comment3 = Comment.builder()
                .text("Commenter's comment on photo 2")
                .photo(photo2)
                .author(commenterUser)
                .createdAt(LocalDateTime.now())
                .build();
        comment3 = commentRepository.save(comment3);
    }

    @Test
    void testDeletingUserCascadesDeleteTheirPhotos() {
        // ARRANGE: Verify initial state
        assertThat(userRepository.findById(ownerUser.getId())).isPresent();
        assertThat(photoRepository.findById(photo1.getId())).isPresent();
        assertThat(photoRepository.findById(photo2.getId())).isPresent();
        
        long photoCountBefore = photoRepository.count();
        assertThat(photoCountBefore).isGreaterThanOrEqualTo(2);

        // ACT: Delete the owner user
        userRepository.delete(ownerUser);
        userRepository.flush(); // Force synchronization with database

        // ASSERT: User should be deleted
        Optional<User> deletedUser = userRepository.findById(ownerUser.getId());
        assertThat(deletedUser)
                .as("Deleted user should not exist in database")
                .isEmpty();

        // ASSERT: User's photos should be cascade deleted
        Optional<Photo> deletedPhoto1 = photoRepository.findById(photo1.getId());
        Optional<Photo> deletedPhoto2 = photoRepository.findById(photo2.getId());
        
        assertThat(deletedPhoto1)
                .as("Photo 1 owned by deleted user should be cascade deleted")
                .isEmpty();
        assertThat(deletedPhoto2)
                .as("Photo 2 owned by deleted user should be cascade deleted")
                .isEmpty();

        // Verify total photo count decreased
        long photoCountAfter = photoRepository.count();
        assertThat(photoCountAfter)
                .as("Photo count should decrease by 2 after user deletion")
                .isEqualTo(photoCountBefore - 2);
    }

    @Test
    void testDeletingUserCascadesDeleteTheirComments() {
        // ARRANGE: Verify initial state
        assertThat(commentRepository.findById(comment2.getId())).isPresent();
        assertThat(commentRepository.findById(comment3.getId())).isPresent();
        
        long commentCountBefore = commentRepository.count();
        assertThat(commentCountBefore).isGreaterThanOrEqualTo(3);

        // Count comments by commenter user
        List<Comment> commenterComments = commentRepository.findByAuthorId(commenterUser.getId());
        assertThat(commenterComments).hasSize(2); // comment2 and comment3

        // ACT: Delete the commenter user
        userRepository.delete(commenterUser);
        userRepository.flush();

        // ASSERT: User should be deleted
        assertThat(userRepository.findById(commenterUser.getId())).isEmpty();

        // ASSERT: User's comments should be cascade deleted
        assertThat(commentRepository.findById(comment2.getId()))
                .as("Comment by deleted user should be cascade deleted")
                .isEmpty();
        assertThat(commentRepository.findById(comment3.getId()))
                .as("Comment by deleted user should be cascade deleted")
                .isEmpty();

        // ASSERT: Owner's comment should still exist (different author)
        assertThat(commentRepository.findById(comment1.getId()))
                .as("Comment by other user should still exist")
                .isPresent();

        // Verify total comment count decreased by 2
        long commentCountAfter = commentRepository.count();
        assertThat(commentCountAfter)
                .as("Comment count should decrease by 2 after user deletion")
                .isEqualTo(commentCountBefore - 2);

        // ASSERT: Photos should still exist (they belong to ownerUser)
        assertThat(photoRepository.findById(photo1.getId()))
                .as("Photos should not be deleted when commenter is deleted")
                .isPresent();
        assertThat(photoRepository.findById(photo2.getId()))
                .as("Photos should not be deleted when commenter is deleted")
                .isPresent();
    }

    @Test
    void testDeletingPhotoDoesNotDeleteUser() {
        // ARRANGE: Verify initial state
        assertThat(photoRepository.findById(photo1.getId())).isPresent();
        assertThat(userRepository.findById(ownerUser.getId())).isPresent();

        // ACT: Delete a photo
        photoRepository.delete(photo1);
        photoRepository.flush();

        // ASSERT: Photo should be deleted
        assertThat(photoRepository.findById(photo1.getId()))
                .as("Deleted photo should not exist")
                .isEmpty();

        // ASSERT: Owner user should still exist
        assertThat(userRepository.findById(ownerUser.getId()))
                .as("Owner user should still exist after photo deletion")
                .isPresent();

        // ASSERT: Other photos by the same owner should still exist
        assertThat(photoRepository.findById(photo2.getId()))
                .as("Other photos by the same owner should still exist")
                .isPresent();
    }

    @Test
    void testDeletingPhotoCascadesDeleteItsComments() {
        // ARRANGE: Verify initial state
        assertThat(commentRepository.findById(comment1.getId())).isPresent();
        assertThat(commentRepository.findById(comment2.getId())).isPresent();
        
        List<Comment> photo1Comments = commentRepository.findByPhotoIdOrderByCreatedAtDesc(photo1.getId());
        assertThat(photo1Comments).hasSize(2); // comment1 and comment2

        long commentCountBefore = commentRepository.count();

        // ACT: Delete photo1
        photoRepository.delete(photo1);
        photoRepository.flush();

        // ASSERT: Photo should be deleted
        assertThat(photoRepository.findById(photo1.getId())).isEmpty();

        // ASSERT: Photo's comments should be cascade deleted
        assertThat(commentRepository.findById(comment1.getId()))
                .as("Comment on deleted photo should be cascade deleted")
                .isEmpty();
        assertThat(commentRepository.findById(comment2.getId()))
                .as("Comment on deleted photo should be cascade deleted")
                .isEmpty();

        // ASSERT: Comments on other photos should still exist
        assertThat(commentRepository.findById(comment3.getId()))
                .as("Comments on other photos should still exist")
                .isPresent();

        // Verify total comment count decreased by 2
        long commentCountAfter = commentRepository.count();
        assertThat(commentCountAfter)
                .as("Comment count should decrease by 2 after photo deletion")
                .isEqualTo(commentCountBefore - 2);

        // ASSERT: Users should still exist
        assertThat(userRepository.findById(ownerUser.getId()))
                .as("Owner user should still exist")
                .isPresent();
        assertThat(userRepository.findById(commenterUser.getId()))
                .as("Commenter user should still exist")
                .isPresent();
    }

    @Test
    void testDeletingUserDeletesPhotosAndAllRelatedComments() {
        // ARRANGE: Verify initial state
        long userCountBefore = userRepository.count();
        long photoCountBefore = photoRepository.count();
        long commentCountBefore = commentRepository.count();

        assertThat(userCountBefore).isEqualTo(2); // ownerUser + commenterUser
        assertThat(photoCountBefore).isEqualTo(2); // photo1 + photo2
        assertThat(commentCountBefore).isEqualTo(3); // comment1 + comment2 + comment3

        // ACT: Delete the owner user
        userRepository.delete(ownerUser);
        userRepository.flush();

        // ASSERT: User deleted
        assertThat(userRepository.count()).isEqualTo(userCountBefore - 1);
        assertThat(userRepository.findById(ownerUser.getId())).isEmpty();

        // ASSERT: All photos owned by user deleted (2 photos)
        assertThat(photoRepository.count()).isEqualTo(photoCountBefore - 2);
        assertThat(photoRepository.findById(photo1.getId())).isEmpty();
        assertThat(photoRepository.findById(photo2.getId())).isEmpty();

        // ASSERT: All comments deleted (3 comments):
        // - comment1 (owner's comment, deleted because author deleted)
        // - comment2 (commenter's comment on photo1, deleted because photo deleted)
        // - comment3 (commenter's comment on photo2, deleted because photo deleted)
        assertThat(commentRepository.count()).isEqualTo(0);
        assertThat(commentRepository.findById(comment1.getId())).isEmpty();
        assertThat(commentRepository.findById(comment2.getId())).isEmpty();
        assertThat(commentRepository.findById(comment3.getId())).isEmpty();

        // ASSERT: Commenter user still exists (was not deleted)
        assertThat(userRepository.findById(commenterUser.getId()))
                .as("Commenter user should still exist")
                .isPresent();
    }

    @Test
    void testMultipleUserDeletionsWithInterleavedComments() {
        // ARRANGE: Create a more complex scenario
        User user3 = User.builder()
                .username("user3")
                .email("user3@example.com")
                .passwordHash("$2a$10$dummyHashForTesting")
                .role(Role.USER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        user3 = userRepository.save(user3);

        // User3 creates a photo
        Photo photo3 = Photo.builder()
                .title("Photo by User3")
                .description("Third photo")
                .originalFilename("photo3.jpg")
                .storageFilename("uuid-photo3.jpg")
                .contentType("image/jpeg")
                .visibility(Visibility.PUBLIC)
                .owner(user3)
                .createdAt(LocalDateTime.now())
                .build();
        photo3 = photoRepository.save(photo3);

        // All users comment on photo3
        Comment commentByOwner = commentRepository.save(Comment.builder()
                .text("Owner on photo3")
                .photo(photo3)
                .author(ownerUser)
                .createdAt(LocalDateTime.now())
                .build());

        Comment commentByCommenter = commentRepository.save(Comment.builder()
                .text("Commenter on photo3")
                .photo(photo3)
                .author(commenterUser)
                .createdAt(LocalDateTime.now())
                .build());

        Comment commentByUser3 = commentRepository.save(Comment.builder()
                .text("User3 on photo3")
                .photo(photo3)
                .author(user3)
                .createdAt(LocalDateTime.now())
                .build());

        long commentCountBefore = commentRepository.count();
        assertThat(commentCountBefore).isEqualTo(6); // 3 original + 3 new

        // ACT: Delete ownerUser
        userRepository.delete(ownerUser);
        userRepository.flush();

        // ASSERT: OwnerUser's photos and comments deleted
        // - photo1 deleted (owned by ownerUser)
        // - photo2 deleted (owned by ownerUser)
        // - comment1 deleted (authored by ownerUser on photo1)
        // - commentByOwner deleted (authored by ownerUser on photo3)
        // But also:
        // - comment2 deleted (on photo1 which was deleted)
        // - comment3 deleted (on photo2 which was deleted)

        assertThat(photoRepository.findById(photo1.getId())).isEmpty();
        assertThat(photoRepository.findById(photo2.getId())).isEmpty();
        assertThat(photoRepository.findById(photo3.getId()))
                .as("Photo3 should still exist")
                .isPresent();

        // Comments on deleted photos should be gone
        assertThat(commentRepository.findById(comment1.getId())).isEmpty();
        assertThat(commentRepository.findById(comment2.getId())).isEmpty();
        assertThat(commentRepository.findById(comment3.getId())).isEmpty();
        assertThat(commentRepository.findById(commentByOwner.getId())).isEmpty();

        // Comments on photo3 by other users should still exist
        assertThat(commentRepository.findById(commentByCommenter.getId()))
                .as("Commenter's comment on photo3 should still exist")
                .isPresent();
        assertThat(commentRepository.findById(commentByUser3.getId()))
                .as("User3's comment on photo3 should still exist")
                .isPresent();

        assertThat(commentRepository.count()).isEqualTo(2);
    }
}