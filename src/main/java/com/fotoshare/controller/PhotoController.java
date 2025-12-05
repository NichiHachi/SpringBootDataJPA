package com.fotoshare.controller;

import com.fotoshare.dto.CommentDTO;
import com.fotoshare.dto.PhotoDTO;
import com.fotoshare.dto.PhotoUploadDTO;
import com.fotoshare.dto.ShareDTO;
import com.fotoshare.dto.UserDTO;
import com.fotoshare.entity.Photo;
import com.fotoshare.enums.PermissionLevel;
import com.fotoshare.enums.Visibility;
import com.fotoshare.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Controller for photo management operations.
 * 
 * This controller handles:
 * - Photo upload with validation
 * - Photo viewing (full size and thumbnails)
 * - Photo editing (metadata and visibility)
 * - Photo deletion
 * - Photo sharing with other users
 * - Comments on photos
 * 
 * Security:
 * - All operations use @PreAuthorize for method-level security
 * - Photo access is verified through SecurityService
 * - Only DTOs are exposed to the view (never JPA entities)
 */
@Controller
@RequestMapping("/photos")
public class PhotoController {

    private static final Logger logger = LoggerFactory.getLogger(PhotoController.class);

    private final PhotoService photoService;
    private final CommentService commentService;
    private final ShareService shareService;
    private final AlbumService albumService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final SecurityService securityService;

    public PhotoController(PhotoService photoService,
                          CommentService commentService,
                          ShareService shareService,
                          AlbumService albumService,
                          UserService userService,
                          FileStorageService fileStorageService,
                          SecurityService securityService) {
        this.photoService = photoService;
        this.commentService = commentService;
        this.shareService = shareService;
        this.albumService = albumService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.securityService = securityService;
    }

    // =========================================
    // PHOTO LISTING
    // =========================================

    /**
     * Display the current user's photos.
     */
    @GetMapping("/my")
    public String myPhotos(Model model,
                          Authentication authentication,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "12") int size) {
        
        size = Math.min(size, 48);
        Page<PhotoDTO> photos = photoService.getMyPhotos(authentication, 
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        
        model.addAttribute("photos", photos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", photos.getTotalPages());
        model.addAttribute("pageTitle", "Mes Photos");
        
        return "photos/list";
    }

    /**
     * Display photos shared with the current user.
     */
    @GetMapping("/shared")
    public String sharedPhotos(Model model,
                              Authentication authentication,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "12") int size) {
        
        size = Math.min(size, 48);
        Page<PhotoDTO> photos = photoService.getSharedWithMe(authentication,
                PageRequest.of(page, size));
        
        model.addAttribute("photos", photos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", photos.getTotalPages());
        model.addAttribute("pageTitle", "Photos partagées avec moi");
        
        return "photos/list";
    }

    /**
     * Search photos by title.
     */
    @GetMapping("/search")
    public String searchPhotos(Model model,
                              Authentication authentication,
                              @RequestParam String q,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "12") int size) {
        
        size = Math.min(size, 48);
        Page<PhotoDTO> photos = photoService.searchPhotos(q, authentication,
                PageRequest.of(page, size));
        
        model.addAttribute("photos", photos);
        model.addAttribute("searchQuery", q);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", photos.getTotalPages());
        model.addAttribute("pageTitle", "Résultats pour: " + q);
        
        return "photos/list";
    }

    // =========================================
    // PHOTO UPLOAD
    // =========================================

    /**
     * Display the upload form.
     */
    @GetMapping("/upload")
    public String showUploadForm(Model model, Authentication authentication) {
        model.addAttribute("photoUploadDTO", new PhotoUploadDTO());
        model.addAttribute("albums", albumService.getMyAlbumsList(authentication));
        model.addAttribute("visibilityOptions", Visibility.values());
        return "photos/upload";
    }

    /**
     * Process photo upload.
     */
    @PostMapping("/upload")
    public String uploadPhoto(@Valid @ModelAttribute("photoUploadDTO") PhotoUploadDTO uploadDTO,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("albums", albumService.getMyAlbumsList(authentication));
            model.addAttribute("visibilityOptions", Visibility.values());
            return "photos/upload";
        }

        try {
            PhotoDTO savedPhoto = photoService.uploadPhoto(uploadDTO, authentication);
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Photo '" + savedPhoto.getTitle() + "' uploadée avec succès !");
            return "redirect:/photos/view/" + savedPhoto.getId();
            
        } catch (IllegalArgumentException e) {
            logger.warn("Upload validation failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/photos/upload";
            
        } catch (IOException e) {
            logger.error("Upload IO error: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                    "Erreur lors de l'enregistrement du fichier. Veuillez réessayer.");
            return "redirect:/photos/upload";
        }
    }

    // =========================================
    // PHOTO VIEWING
    // =========================================

    /**
     * View a photo's detail page.
     */
    @GetMapping("/view/{id}")
    @PreAuthorize("@securityService.canAccessPhoto(authentication, #id)")
    public String viewPhoto(@PathVariable Long id,
                           Model model,
                           Authentication authentication,
                           @RequestParam(defaultValue = "0") int commentPage) {
        
        Optional<PhotoDTO> photoOpt = photoService.getPhotoById(id, authentication);
        
        if (photoOpt.isEmpty()) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Photo", id);
        }

        PhotoDTO photo = photoOpt.get();
        
        // Get comments with pagination
        Page<CommentDTO> comments = commentService.getCommentsByPhoto(id, 
                PageRequest.of(commentPage, 10, Sort.by("createdAt").descending()));
        
        // Get shares if user can manage them
        List<ShareDTO> shares = null;
        if (securityService.canSharePhoto(authentication, id)) {
            shares = shareService.getSharesByPhoto(id);
        }
        
        // Get available users for sharing (exclude owner and already shared users)
        List<UserDTO> availableUsers = null;
        if (securityService.canSharePhoto(authentication, id)) {
            Long currentUserId = securityService.getCurrentUserId(authentication);
            availableUsers = userService.findOtherUsers(currentUserId);
        }

        model.addAttribute("photo", photo);
        model.addAttribute("comments", comments);
        model.addAttribute("commentPage", commentPage);
        model.addAttribute("shares", shares);
        model.addAttribute("availableUsers", availableUsers);
        model.addAttribute("permissionLevels", PermissionLevel.values());
        model.addAttribute("newComment", new CommentDTO());
        
        // Permission flags
        model.addAttribute("canEdit", photo.isCanEdit());
        model.addAttribute("canDelete", photo.isCanDelete());
        model.addAttribute("canComment", photo.isCanComment());
        model.addAttribute("canShare", securityService.canSharePhoto(authentication, id));

        return "photos/view";
    }

    /**
     * Serve the actual photo image.
     */
    @GetMapping("/view/{id}/image")
    public ResponseEntity<Resource> servePhoto(@PathVariable Long id) {
        Optional<Photo> photoOpt = photoService.getPhotoEntityById(id);
        
        if (photoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Photo photo = photoOpt.get();
        Resource resource = fileStorageService.loadPhoto(photo.getStorageFilename());
        
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "inline; filename=\"" + photo.getOriginalFilename() + "\"")
                .body(resource);
    }

    /**
     * Serve the photo thumbnail.
     */
    @GetMapping("/view/{id}/thumbnail")
    public ResponseEntity<Resource> serveThumbnail(@PathVariable Long id) {
        Optional<Photo> photoOpt = photoService.getPhotoEntityById(id);
        
        if (photoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Photo photo = photoOpt.get();
        Resource resource = fileStorageService.loadThumbnail(photo.getStorageFilename());
        
        if (resource == null || !resource.exists()) {
            // Fall back to original photo if thumbnail doesn't exist
            resource = fileStorageService.loadPhoto(photo.getStorageFilename());
            if (resource == null || !resource.exists()) {
                return ResponseEntity.notFound().build();
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(resource);
    }

    // =========================================
    // PHOTO EDITING
    // =========================================

    /**
     * Display the edit form for a photo.
     */
    @GetMapping("/edit/{id}")
    @PreAuthorize("@securityService.canEditPhoto(authentication, #id)")
    public String showEditForm(@PathVariable Long id, Model model) {
        Optional<PhotoDTO> photoOpt = photoService.getPhotoById(id);
        
        if (photoOpt.isEmpty()) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Photo", id);
        }

        model.addAttribute("photo", photoOpt.get());
        model.addAttribute("visibilityOptions", Visibility.values());
        
        return "photos/edit";
    }

    /**
     * Process photo edit.
     */
    @PostMapping("/edit/{id}")
    @PreAuthorize("@securityService.canEditPhoto(authentication, #id)")
    public String editPhoto(@PathVariable Long id,
                           @RequestParam String title,
                           @RequestParam(required = false) String description,
                           @RequestParam Visibility visibility,
                           RedirectAttributes redirectAttributes) {
        
        try {
            photoService.updatePhoto(id, title, description, visibility);
            redirectAttributes.addFlashAttribute("successMessage", "Photo mise à jour avec succès !");
            return "redirect:/photos/view/" + id;
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/photos/edit/" + id;
        }
    }

    /**
     * Quick visibility toggle.
     */
    @PostMapping("/visibility/{id}")
    @PreAuthorize("@securityService.canEditPhoto(authentication, #id)")
    public String toggleVisibility(@PathVariable Long id,
                                  @RequestParam Visibility visibility,
                                  RedirectAttributes redirectAttributes) {
        
        photoService.updateVisibility(id, visibility);
        redirectAttributes.addFlashAttribute("successMessage", 
                "Visibilité mise à jour : " + visibility);
        return "redirect:/photos/view/" + id;
    }

    // =========================================
    // PHOTO DELETION
    // =========================================

    /**
     * Delete a photo.
     */
    @PostMapping("/delete/{id}")
    @PreAuthorize("@securityService.canDeletePhoto(authentication, #id)")
    public String deletePhoto(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            photoService.deletePhoto(id);
            redirectAttributes.addFlashAttribute("successMessage", "Photo supprimée avec succès !");
            return "redirect:/photos/my";
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/photos/view/" + id;
        }
    }

    // =========================================
    // PHOTO SHARING
    // =========================================

    /**
     * Share a photo with a user.
     */
    @PostMapping("/share/{photoId}")
    @PreAuthorize("@securityService.canSharePhoto(authentication, #photoId)")
    public String sharePhoto(@PathVariable Long photoId,
                            @RequestParam Long userId,
                            @RequestParam PermissionLevel permission,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        
        try {
            shareService.sharePhoto(photoId, userId, permission, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Photo partagée avec succès !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/photos/view/" + photoId;
    }

    /**
     * Update a share's permission level.
     */
    @PostMapping("/share/{photoId}/update")
    @PreAuthorize("@securityService.canSharePhoto(authentication, #photoId)")
    public String updateShare(@PathVariable Long photoId,
                             @RequestParam Long userId,
                             @RequestParam PermissionLevel permission,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        
        try {
            shareService.updateSharePermission(photoId, userId, permission, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Permission mise à jour !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/photos/view/" + photoId;
    }

    /**
     * Remove a share (unshare).
     */
    @PostMapping("/share/{photoId}/remove")
    @PreAuthorize("@securityService.canSharePhoto(authentication, #photoId)")
    public String removeShare(@PathVariable Long photoId,
                             @RequestParam Long userId,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        
        try {
            shareService.removeShare(photoId, userId, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Partage supprimé !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/photos/view/" + photoId;
    }

    // =========================================
    // COMMENTS
    // =========================================

    /**
     * Add a comment to a photo.
     */
    @PostMapping("/comment/{photoId}")
    @PreAuthorize("@securityService.canCommentOnPhoto(authentication, #photoId)")
    public String addComment(@PathVariable Long photoId,
                            @RequestParam String text,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        
        try {
            commentService.addComment(photoId, text, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Commentaire ajouté !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/photos/view/" + photoId;
    }

    /**
     * Delete a comment.
     */
    @PostMapping("/comment/{photoId}/delete/{commentId}")
    @PreAuthorize("@securityService.canDeleteComment(authentication, #commentId)")
    public String deleteComment(@PathVariable Long photoId,
                               @PathVariable Long commentId,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        
        try {
            commentService.deleteComment(commentId, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Commentaire supprimé !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/photos/view/" + photoId;
    }

    // =========================================
    // ALBUM OPERATIONS
    // =========================================

    /**
     * Add a photo to an album.
     */
    @PostMapping("/album/add")
    @PreAuthorize("@securityService.isPhotoOwner(authentication, #photoId)")
    public String addToAlbum(@RequestParam Long photoId,
                            @RequestParam Long albumId,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        
        try {
            albumService.addPhotoToAlbum(albumId, photoId, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Photo ajoutée à l'album !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/photos/view/" + photoId;
    }

    /**
     * Remove a photo from an album.
     */
    @PostMapping("/album/remove")
    @PreAuthorize("@securityService.isPhotoOwner(authentication, #photoId)")
    public String removeFromAlbum(@RequestParam Long photoId,
                                 @RequestParam Long albumId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            albumService.removePhotoFromAlbum(albumId, photoId, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Photo retirée de l'album !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/photos/view/" + photoId;
    }
}