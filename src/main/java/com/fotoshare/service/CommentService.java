package com.fotoshare.service;

import com.fotoshare.dto.CommentDTO;
import com.fotoshare.entity.Comment;
import com.fotoshare.entity.Photo;
import com.fotoshare.entity.User;
import com.fotoshare.mapper.EntityMapper;
import com.fotoshare.repository.CommentRepository;
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
 * Service for managing comments on photos.
 * 
 * Business Logic Layer responsibilities:
 * - Comment CRUD operations
 * - Validation of comment permissions
 * - Comment pagination
 * - Transaction management
 * 
 * Comments require at least COMMENT permission on the photo,
 * which is verified by the SecurityService.
 */
@Service
@Transactional
public class CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final PhotoRepository photoRepository;
    private final SecurityService securityService;
    private final EntityMapper entityMapper;

    public CommentService(CommentRepository commentRepository,
                          PhotoRepository photoRepository,
                          SecurityService securityService,
                          EntityMapper entityMapper) {
        this.commentRepository = commentRepository;
        this.photoRepository = photoRepository;
        this.securityService = securityService;
        this.entityMapper = entityMapper;
    }

    // =========================================
    // COMMENT CREATION
    // =========================================

    /**
     * Add a comment to a photo.
     *
     * @param photoId The photo ID to comment on
     * @param text The comment text
     * @param authentication Current user's authentication
     * @return The created comment as DTO
     * @throws IllegalArgumentException If photo not found or validation fails
     */
    public CommentDTO addComment(Long photoId, String text, Authentication authentication) {
        logger.info("Adding comment to photo {} by user {}", photoId, authentication.getName());

        // Validate text
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Le commentaire ne peut pas être vide");
        }

        if (text.length() > 2000) {
            throw new IllegalArgumentException("Le commentaire ne peut pas dépasser 2000 caractères");
        }

        // Get the photo
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo non trouvée"));

        // Get the current user
        User author = securityService.getCurrentUser(authentication)
                .orElseThrow(() -> new IllegalStateException("Utilisateur non connecté"));

        // Create the comment
        Comment comment = Comment.builder()
                .text(text.trim())
                .photo(photo)
                .author(author)
                .build();

        comment = commentRepository.save(comment);
        logger.info("Comment created: id={}, photoId={}, authorId={}", 
                comment.getId(), photoId, author.getId());

        return entityMapper.toCommentDTO(comment);
    }

    // =========================================
    // COMMENT RETRIEVAL
    // =========================================

    /**
     * Get a comment by ID.
     *
     * @param commentId The comment ID
     * @return Optional containing the comment DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<CommentDTO> getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .map(entityMapper::toCommentDTO);
    }

    /**
     * Get the raw comment entity by ID.
     * For internal service use only.
     *
     * @param commentId The comment ID
     * @return Optional containing the comment entity if found
     */
    @Transactional(readOnly = true)
    public Optional<Comment> getCommentEntityById(Long commentId) {
        return commentRepository.findById(commentId);
    }

    /**
     * Get all comments for a photo (ordered by creation date, newest first).
     *
     * @param photoId The photo ID
     * @return List of comment DTOs
     */
    @Transactional(readOnly = true)
    public List<CommentDTO> getCommentsByPhoto(Long photoId) {
        List<Comment> comments = commentRepository.findByPhotoIdOrderByCreatedAtDesc(photoId);
        return entityMapper.toCommentDTOList(comments);
    }

    /**
     * Get all comments for a photo with pagination.
     *
     * @param photoId The photo ID
     * @param pageable Pagination parameters
     * @return Page of comment DTOs
     */
    @Transactional(readOnly = true)
    public Page<CommentDTO> getCommentsByPhoto(Long photoId, Pageable pageable) {
        return commentRepository.findByPhotoId(photoId, pageable)
                .map(entityMapper::toCommentDTO);
    }

    /**
     * Get the latest comments for a photo.
     *
     * @param photoId The photo ID
     * @param pageable Pagination with size limit
     * @return List of latest comment DTOs
     */
    @Transactional(readOnly = true)
    public List<CommentDTO> getLatestComments(Long photoId, Pageable pageable) {
        List<Comment> comments = commentRepository.findLatestByPhotoId(photoId, pageable);
        return entityMapper.toCommentDTOList(comments);
    }

    /**
     * Get all comments by a specific author.
     *
     * @param authorId The author's user ID
     * @param pageable Pagination parameters
     * @return Page of comment DTOs
     */
    @Transactional(readOnly = true)
    public Page<CommentDTO> getCommentsByAuthor(Long authorId, Pageable pageable) {
        return commentRepository.findByAuthorId(authorId, pageable)
                .map(entityMapper::toCommentDTO);
    }

    /**
     * Get comments on photos owned by a specific user.
     *
     * @param ownerId The photo owner's user ID
     * @param pageable Pagination parameters
     * @return Page of comment DTOs
     */
    @Transactional(readOnly = true)
    public Page<CommentDTO> getCommentsOnUserPhotos(Long ownerId, Pageable pageable) {
        return commentRepository.findCommentsOnUserPhotos(ownerId, pageable)
                .map(entityMapper::toCommentDTO);
    }

    // =========================================
    // COMMENT UPDATE
    // =========================================

    /**
     * Update a comment's text.
     * Only the comment author can update their comment.
     *
     * @param commentId The comment ID
     * @param newText The new comment text
     * @param authentication Current user's authentication
     * @return Updated comment DTO
     * @throws IllegalArgumentException If comment not found or user not authorized
     */
    public CommentDTO updateComment(Long commentId, String newText, Authentication authentication) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Commentaire non trouvé"));

        // Verify the user is the author
        User currentUser = securityService.getCurrentUser(authentication)
                .orElseThrow(() -> new IllegalStateException("Utilisateur non connecté"));

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Vous ne pouvez modifier que vos propres commentaires");
        }

        // Validate new text
        if (newText == null || newText.trim().isEmpty()) {
            throw new IllegalArgumentException("Le commentaire ne peut pas être vide");
        }

        if (newText.length() > 2000) {
            throw new IllegalArgumentException("Le commentaire ne peut pas dépasser 2000 caractères");
        }

        comment.setText(newText.trim());
        comment = commentRepository.save(comment);

        logger.info("Comment updated: id={}", commentId);

        return entityMapper.toCommentDTO(comment);
    }

    // =========================================
    // COMMENT DELETION
    // =========================================

    /**
     * Delete a comment.
     * Can be deleted by the comment author, photo owner, or admin/moderator.
     *
     * @param commentId The comment ID to delete
     * @param authentication Current user's authentication
     * @throws IllegalArgumentException If comment not found or user not authorized
     */
    public void deleteComment(Long commentId, Authentication authentication) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Commentaire non trouvé"));

        // Security check is handled by @PreAuthorize in the controller
        // using securityService.canDeleteComment()

        commentRepository.delete(comment);
        logger.info("Comment deleted: id={}", commentId);
    }

    /**
     * Delete a comment (internal method without security check).
     *
     * @param commentId The comment ID to delete
     */
    public void deleteComment(Long commentId) {
        if (commentRepository.existsById(commentId)) {
            commentRepository.deleteById(commentId);
            logger.info("Comment deleted: id={}", commentId);
        }
    }

    /**
     * Delete all comments on a photo.
     *
     * @param photoId The photo ID
     */
    public void deleteCommentsByPhoto(Long photoId) {
        commentRepository.deleteByPhotoId(photoId);
        logger.info("All comments deleted for photo: id={}", photoId);
    }

    /**
     * Delete all comments by a specific author.
     *
     * @param authorId The author's user ID
     */
    public void deleteCommentsByAuthor(Long authorId) {
        commentRepository.deleteByAuthorId(authorId);
        logger.info("All comments deleted for author: id={}", authorId);
    }

    // =========================================
    // STATISTICS
    // =========================================

    /**
     * Count comments on a photo.
     *
     * @param photoId The photo ID
     * @return Number of comments
     */
    @Transactional(readOnly = true)
    public long countCommentsByPhoto(Long photoId) {
        return commentRepository.countByPhotoId(photoId);
    }

    /**
     * Count comments by a specific author.
     *
     * @param authorId The author's user ID
     * @return Number of comments
     */
    @Transactional(readOnly = true)
    public long countCommentsByAuthor(Long authorId) {
        return commentRepository.countByAuthorId(authorId);
    }

    /**
     * Check if a user has commented on a specific photo.
     *
     * @param photoId The photo ID
     * @param userId The user ID
     * @return true if the user has commented
     */
    @Transactional(readOnly = true)
    public boolean hasUserCommented(Long photoId, Long userId) {
        return commentRepository.existsByPhotoIdAndAuthorId(photoId, userId);
    }
}