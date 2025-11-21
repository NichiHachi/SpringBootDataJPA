package local.epul4a.gestusers.controller;
import local.epul4a.gestusers.model.User;
import local.epul4a.gestusers.model.Role;
import local.epul4a.gestusers.repository.UserRepository;
import local.epul4a.gestusers.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.HashSet;
import java.util.Set;
@Controller
public class UserController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "index";}
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    @PostMapping("/addUser")
    public String addUser(String username, String password, String email, String
            roleName) {
        Role role = roleRepository.findByUsername(roleName);
        if (role == null) {
            role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(password); // Assurez-vous de hasher le mot de passe dans
        user.setEmail(email);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
        userRepository.save(user);
        return "redirect:/";
    }
}