package local.epul4a.demosbt.controller;

import jakarta.validation.Valid;
import local.epul4a.demosbt.form.UserForm;
import local.epul4a.demosbt.model.User;
import local.epul4a.demosbt.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("message", "Bienvenue dans l'application de gestion d'utilisateurs");
        return "index";
    }

    @GetMapping("/users")
    public String listUsers(Model model,
                            @RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "size", defaultValue = "10") int size) {
        List<User> allUsers = userService.getAllUsers();

        int totalUsers = allUsers.size();
        int pageSize = size > 0 ? size : 10;
        int currentPage = page < 1 ? 1 : page;
        int totalPages = (int) Math.ceil((double) totalUsers / pageSize);
        if (totalPages < 1) {
            totalPages = 1;
        }
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }

        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalUsers);

        List<User> usersPage = allUsers.subList(Math.min(fromIndex, totalUsers), Math.min(toIndex, totalUsers));

        model.addAttribute("users", usersPage);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalUsers", totalUsers);

        return "userList";
    }

    @GetMapping("/users/add")
    public String showAddUserForm(Model model) {
        model.addAttribute("userForm", new UserForm());
        model.addAttribute("isEdit", false);
        return "userForm";
    }

    @PostMapping("/users/add")
    public String addUser(@Valid @ModelAttribute("userForm") UserForm userForm,
                          BindingResult result,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "userForm";
        }

        User user = convertFormToUser(userForm);
        userService.createUser(user);

        redirectAttributes.addFlashAttribute("successMessage", "Utilisateur créé avec succès");
        return "redirect:/users";
    }

    @GetMapping("/users/edit/{id}")
    public String showEditUserForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = userService.getUserById(id);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Utilisateur non trouvé");
            return "redirect:/users";
        }

        UserForm userForm = new UserForm(
                user.getId(),
                user.getNom(),
                user.getPrenom(),
                user.getEmail(),
                user.getDateNaissance()
        );

        model.addAttribute("userForm", userForm);
        model.addAttribute("isEdit", true);
        return "userForm";
    }

    @PostMapping("/users/edit/{id}")
    public String editUser(@PathVariable Long id,
                           @Valid @ModelAttribute("userForm") UserForm userForm,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "userForm";
        }

        User user = convertFormToUser(userForm);
        User updatedUser = userService.updateUser(id, user);

        if (updatedUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur lors de la mise à jour de l'utilisateur");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Utilisateur modifié avec succès");
        }

        return "redirect:/users";
    }

    @GetMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        boolean deleted = userService.deleteUser(id);

        if (deleted) {
            redirectAttributes.addFlashAttribute("successMessage", "Utilisateur supprimé avec succès");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur lors de la suppression de l'utilisateur");
        }

        return "redirect:/users";
    }

    private User convertFormToUser(UserForm userForm) {
        User user = new User();
        user.setNom(userForm.getNom());
        user.setPrenom(userForm.getPrenom());
        user.setEmail(userForm.getEmail());
        user.setDateNaissance(userForm.getDateNaissance());
        return user;
    }
}
