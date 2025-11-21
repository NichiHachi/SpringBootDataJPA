package local.epul4a.gestusersv4.repository;

import local.epul4a.gestusersv4.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
}