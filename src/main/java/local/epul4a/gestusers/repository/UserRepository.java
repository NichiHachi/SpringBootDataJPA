package local.epul4a.gestusers.repository;
import local.epul4a.gestusers.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
}
