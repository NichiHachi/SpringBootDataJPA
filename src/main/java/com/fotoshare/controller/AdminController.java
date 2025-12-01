package com.fotoshare.controller;

import com.fotoshare.dto.UserDTO;
import com.fotoshare.enums.Role;
import com.fotoshare.service.PhotoService;
import com.fotoshare.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for admin dashboard and user management.
 * 
 * This controller handles:
 * - Admin dashboard with statistics
 * - User listing and search
 * - User status management (enable/disable)
 * - Role management
 * 
 * Security:
 * - All endpoints require ADMIN role
 * - Uses @PreAuthorize for method-level security
 * - Prevents admins from banning themselves
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;
    private final PhotoService photoService;

    public AdminController(UserService userService, PhotoService photoService) {
        this.userService = userService;
        this.photoService = photoService;
    }

    // =========================================
    // DASHBOARD
    // =========================================

    /**
     * Display the admin dashboard with statistics.
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        // User statistics
        long totalUsers = userService.getTotalUserCount();
        long activeUsers = userService.getActiveUserCount();
        long adminCount = userService.getUserCountByRole(Role.ADMIN);
        long moderatorCount = userService.getUserCountByRole(Role.MODERATOR);

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", totalUsers - activeUsers);
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("moderatorCount", moderatorCount);

        return "admin/dashboard";
    }

    // =========================================
    // USER MANAGEMENT
    // =========================================

    /**
     * Display the user list with pagination and filtering.
     */
    @GetMapping("/users")
    public String listUsers(Model model,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) String search,
                           @RequestParam(required = false) String status) {

        size = Math.min(size, 100);
        Page<UserDTO> users;

        if (search != null && !search.trim().isEmpty()) {
            users = userService.searchByUsername(search.trim(), 
                    PageRequest.of(page, size, Sort.by("createdAt").descending()));
        } else if ("enabled".equals(status)) {
            users = userService.findEnabledUsers(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        } else if ("disabled".equals(status)) {
            users = userService.findDisabledUsers(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        } else {
            users = userService.findAllUsers(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        }

        model.addAttribute("users", users);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", users.getTotalPages());
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("roles", Role.values());

        return "admin/users";
    }

    /**
     * View a specific user's details.
     */
    @GetMapping("/users/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        UserDTO user = userService.findById(id)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException("Utilisateur", id));

        long photoCount = photoService.countPhotosByOwner(id);

        model.addAttribute("user", user);
        model.addAttribute("photoCount", photoCount);
        model.addAttribute("roles", Role.values());

        return "admin/user-detail";
    }

    /**
     * Toggle a user's enabled status (ban/unban).
     */
    @PostMapping("/users/{id}/toggle-status")
    @PreAuthorize("@securityService.canBanUser(authentication, #id)")
    public String toggleUserStatus(@PathVariable Long id,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {

        try {
            UserDTO updatedUser = userService.toggleUserStatus(id)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

            String action = updatedUser.getEnabled() ? "activé" : "désactivé";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Utilisateur " + updatedUser.getUsername() + " " + action + " avec succès !");

            logger.info("User {} status toggled to {} by admin {}",
                    id, updatedUser.getEnabled(), authentication.getName());

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    /**
     * Enable a user account.
     */
    @PostMapping("/users/{id}/enable")
    @PreAuthorize("@securityService.canBanUser(authentication, #id)")
    public String enableUser(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {

        if (userService.enableUser(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Utilisateur activé avec succès !");
            logger.info("User {} enabled by admin {}", id, authentication.getName());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Utilisateur non trouvé");
        }

        return "redirect:/admin/users/" + id;
    }

    /**
     * Disable (ban) a user account.
     */
    @PostMapping("/users/{id}/disable")
    @PreAuthorize("@securityService.canBanUser(authentication, #id)")
    public String disableUser(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {

        if (userService.disableUser(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Utilisateur désactivé avec succès !");
            logger.info("User {} disabled by admin {}", id, authentication.getName());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Utilisateur non trouvé");
        }

        return "redirect:/admin/users/" + id;
    }

    /**
     * Change a user's role.
     */
    @PostMapping("/users/{id}/role")
    @PreAuthorize("@securityService.canBanUser(authentication, #id)")
    public String changeUserRole(@PathVariable Long id,
                                @RequestParam Role role,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {

        try {
            if (userService.changeUserRole(id, role)) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Rôle modifié en " + role.name() + " avec succès !");
                logger.info("User {} role changed to {} by admin {}",
                        id, role, authentication.getName());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Utilisateur non trouvé");
            }

        } catch (Exception e) {
            logger.error("Error changing user role: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la modification du rôle");
        }

        return "redirect:/admin/users/" + id;
    }

    /**
     * Delete a user account.
     * Warning: This will cascade delete all user's photos, albums, comments, and shares.
     */
    @PostMapping("/users/{id}/delete")
    @PreAuthorize("@securityService.canBanUser(authentication, #id)")
    public String deleteUser(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {

        try {
            // First, get user info for the log message
            UserDTO user = userService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

            String username = user.getUsername();

            if (userService.deleteUser(id)) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Utilisateur " + username + " supprimé avec succès !");
                logger.warn("User {} (ID: {}) deleted by admin {}",
                        username, id, authentication.getName());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Échec de la suppression");
            }

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la suppression de l'utilisateur");
        }

        return "redirect:/admin/users";
    }

    // =========================================
    // SYSTEM STATS (can be extended)
    // =========================================

    /**
     * Display system statistics and monitoring information.
     */
    @GetMapping("/system")
    public String systemStats(Model model) {
        // Basic system info
        Runtime runtime = Runtime.getRuntime();

        model.addAttribute("totalMemory", runtime.totalMemory() / (1024 * 1024));
        model.addAttribute("freeMemory", runtime.freeMemory() / (1024 * 1024));
        model.addAttribute("usedMemory", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        model.addAttribute("maxMemory", runtime.maxMemory() / (1024 * 1024));
        model.addAttribute("processors", runtime.availableProcessors());
        model.addAttribute("javaVersion", System.getProperty("java.version"));

        return "admin/system";
    }
}