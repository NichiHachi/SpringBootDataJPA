package com.fotoshare.controller;

import com.fotoshare.dto.AlbumDTO;
import com.fotoshare.dto.PhotoDTO;
import com.fotoshare.service.AlbumService;
import com.fotoshare.service.PhotoService;
import com.fotoshare.service.SecurityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Controller for album management operations.
 * 
 * This controller handles:
 * - Album creation and deletion
 * - Album listing and viewing
 * - Adding/removing photos from albums
 * - Album metadata editing
 * 
 * Security:
 * - All operations require authentication
 * - Album ownership is verified through SecurityService
 * - Only DTOs are exposed to the view (never JPA entities)
 */
@Controller
@RequestMapping("/albums")
public class AlbumController {

    private static final Logger logger = LoggerFactory.getLogger(AlbumController.class);

    private final AlbumService albumService;
    private final PhotoService photoService;
    private final SecurityService securityService;

    public AlbumController(AlbumService albumService,
                          PhotoService photoService,
                          SecurityService securityService) {
        this.albumService = albumService;
        this.photoService = photoService;
        this.securityService = securityService;
    }

    // =========================================
    // ALBUM LISTING
    // =========================================

    /**
     * Display the current user's albums.
     */
    @GetMapping
    public String listAlbums(Model model,
                            Authentication authentication,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "12") int size) {
        
        size = Math.min(size, 48);
        Page<AlbumDTO> albums = albumService.getMyAlbums(authentication,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        
        model.addAttribute("albums", albums);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", albums.getTotalPages());
        model.addAttribute("pageTitle", "Mes Albums");
        
        return "albums/list";
    }

    /**
     * Search albums by name.
     */
    @GetMapping("/search")
    public String searchAlbums(Model model,
                              Authentication authentication,
                              @RequestParam String q) {
        
        List<AlbumDTO> albums = albumService.searchAlbums(q, authentication);
        
        model.addAttribute("albums", albums);
        model.addAttribute("searchQuery", q);
        model.addAttribute("pageTitle", "Résultats pour: " + q);
        
        return "albums/search-results";
    }

    // =========================================
    // ALBUM CREATION
    // =========================================

    /**
     * Display the album creation form.
     */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("albumDTO")) {
            model.addAttribute("albumDTO", AlbumDTO.empty());
        }
        return "albums/create";
    }

    /**
     * Process album creation.
     */
    @PostMapping("/create")
    public String createAlbum(@Valid @ModelAttribute("albumDTO") AlbumDTO albumDTO,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        
        if (bindingResult.hasErrors()) {
            return "albums/create";
        }

        try {
            AlbumDTO createdAlbum = albumService.createAlbum(albumDTO, authentication);
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Album '" + createdAlbum.getName() + "' créé avec succès !");
            return "redirect:/albums/view/" + createdAlbum.getId();
            
        } catch (IllegalArgumentException e) {
            logger.warn("Album creation failed: {}", e.getMessage());
            bindingResult.rejectValue("name", "error.name", e.getMessage());
            return "albums/create";
        }
    }

    // =========================================
    // ALBUM VIEWING
    // =========================================

    /**
     * View an album's contents.
     */
    @GetMapping("/view/{id}")
    @PreAuthorize("@securityService.canAccessAlbum(authentication, #id)")
    public String viewAlbum(@PathVariable Long id,
                           Model model,
                           Authentication authentication,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "12") int size) {
        
        Optional<AlbumDTO> albumOpt = albumService.getAlbumWithPhotos(id);
        
        if (albumOpt.isEmpty()) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Album", id);
        }

        AlbumDTO album = albumOpt.get();
        List<PhotoDTO> photos = albumService.getPhotosInAlbum(id);
        
        // Get user's other photos for adding to album
        Page<PhotoDTO> availablePhotos = null;
        if (albumService.isAlbumOwner(id, authentication)) {
            availablePhotos = photoService.getMyPhotos(authentication,
                    PageRequest.of(0, 100, Sort.by("createdAt").descending()));
        }

        model.addAttribute("album", album);
        model.addAttribute("photos", photos);
        model.addAttribute("availablePhotos", availablePhotos);
        model.addAttribute("canEdit", albumService.isAlbumOwner(id, authentication) || 
                                      securityService.isAdmin(authentication));
        
        return "albums/view";
    }

    // =========================================
    // ALBUM EDITING
    // =========================================

    /**
     * Display the edit form for an album.
     */
    @GetMapping("/edit/{id}")
    @PreAuthorize("@securityService.canEditAlbum(authentication, #id)")
    public String showEditForm(@PathVariable Long id, Model model) {
        Optional<AlbumDTO> albumOpt = albumService.getAlbumById(id);
        
        if (albumOpt.isEmpty()) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Album", id);
        }

        model.addAttribute("album", albumOpt.get());
        
        return "albums/edit";
    }

    /**
     * Process album edit.
     */
    @PostMapping("/edit/{id}")
    @PreAuthorize("@securityService.canEditAlbum(authentication, #id)")
    public String editAlbum(@PathVariable Long id,
                           @RequestParam String name,
                           @RequestParam(required = false) String description,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        
        try {
            albumService.updateAlbum(id, name, description, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Album mis à jour avec succès !");
            return "redirect:/albums/view/" + id;
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/albums/edit/" + id;
        }
    }

    // =========================================
    // ALBUM DELETION
    // =========================================

    /**
     * Delete an album.
     * Note: This does not delete the photos, only the album.
     */
    @PostMapping("/delete/{id}")
    @PreAuthorize("@securityService.canEditAlbum(authentication, #id)")
    public String deleteAlbum(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        
        try {
            albumService.deleteAlbum(id, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Album supprimé avec succès !");
            return "redirect:/albums";
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/albums/view/" + id;
        }
    }

    // =========================================
    // PHOTO MANAGEMENT IN ALBUMS
    // =========================================

    /**
     * Add a photo to an album.
     */
    @PostMapping("/{albumId}/add-photo")
    @PreAuthorize("@securityService.canEditAlbum(authentication, #albumId)")
    public String addPhotoToAlbum(@PathVariable Long albumId,
                                 @RequestParam Long photoId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            albumService.addPhotoToAlbum(albumId, photoId, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Photo ajoutée à l'album !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/albums/view/" + albumId;
    }

    /**
     * Add multiple photos to an album.
     */
    @PostMapping("/{albumId}/add-photos")
    @PreAuthorize("@securityService.canEditAlbum(authentication, #albumId)")
    public String addPhotosToAlbum(@PathVariable Long albumId,
                                  @RequestParam List<Long> photoIds,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        
        try {
            int addedCount = albumService.addPhotosToAlbum(albumId, photoIds, authentication);
            redirectAttributes.addFlashAttribute("successMessage", 
                    addedCount + " photo(s) ajoutée(s) à l'album !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/albums/view/" + albumId;
    }

    /**
     * Remove a photo from an album.
     */
    @PostMapping("/{albumId}/remove-photo")
    @PreAuthorize("@securityService.canEditAlbum(authentication, #albumId)")
    public String removePhotoFromAlbum(@PathVariable Long albumId,
                                      @RequestParam Long photoId,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        
        try {
            albumService.removePhotoFromAlbum(albumId, photoId, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Photo retirée de l'album !");
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/albums/view/" + albumId;
    }
}