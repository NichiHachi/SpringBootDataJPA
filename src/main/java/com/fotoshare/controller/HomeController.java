package com.fotoshare.controller;

import com.fotoshare.dto.PhotoDTO;
import com.fotoshare.service.PhotoService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Controller for the home page and public gallery.
 * 
 * This controller handles:
 * - Home page display
 * - Public photo gallery
 * 
 * All endpoints in this controller are publicly accessible.
 */
@Controller
public class HomeController {

    private final PhotoService photoService;

    public HomeController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /**
     * Display the home page.
     * Shows recent public photos and welcome information.
     */
    @GetMapping({"/", "/home"})
    public String home(Model model, Authentication authentication) {
        // Get recent public photos for the home page
        List<PhotoDTO> recentPhotos = photoService.getRecentPublicPhotos(PageRequest.of(0, 12));
        model.addAttribute("recentPhotos", recentPhotos);
        
        // Check if user is authenticated
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser");
        model.addAttribute("isAuthenticated", isAuthenticated);
        
        return "index";
    }

    /**
     * Display the public gallery.
     * Shows all public photos with pagination.
     */
    @GetMapping("/gallery")
    public String gallery(Model model, 
                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "24") int size) {
        
        // Limit size to reasonable bounds
        size = Math.min(size, 48);
        
        var publicPhotos = photoService.getPublicPhotos(PageRequest.of(page, size));
        model.addAttribute("photos", publicPhotos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", publicPhotos.getTotalPages());
        
        return "gallery";
    }

    /**
     * Display the about page.
     */
    @GetMapping("/about")
    public String about() {
        return "about";
    }
}