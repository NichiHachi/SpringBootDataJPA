package local.epul4a.gestusersv4.repository;

import local.epul4a.gestusersv4.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Role findByName(String name);
}