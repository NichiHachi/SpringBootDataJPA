package local.epul4a.gestusers.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Set;
@Entity
public class User {
    // Getters and Setters
    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Setter
    @Getter
    private String username;
    @Setter
    @Getter
    private String password;
    @Setter
    @Getter
    private String email;
    @Setter
    @Getter
    private String nom;
    @Setter
    @Getter
    private String prenom;
    @Getter
    @Setter
    private Date dateNaissance;
    @Setter
    @Getter
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

}