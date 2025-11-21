package local.epul4a.springbootdatajpa.repository;

import local.epul4a.springbootdatajpa.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}

