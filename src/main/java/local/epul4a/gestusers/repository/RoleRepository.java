package local.epul4a.gestusers.repository;
import local.epul4a.gestusers.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleRepository extends JpaRepository<Role, Long> {
    Role findByUsername(String username);
}
