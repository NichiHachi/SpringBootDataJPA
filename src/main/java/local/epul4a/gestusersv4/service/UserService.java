package local.epul4a.gestusersv4.service;

import local.epul4a.gestusersv4.dto.UserDto;
import local.epul4a.gestusersv4.entity.User;

import java.util.List;

public interface UserService {
    void saveUser(UserDto userDto);

    User findByEmail(String email);

    List<UserDto> findAllUsers();
}