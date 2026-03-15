package io.github.rroyoo.mockjdbc.users.service;

import io.github.rroyoo.mockjdbc.users.domain.User;
import io.github.rroyoo.mockjdbc.users.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(long id) {
        return userRepository.findById(id);
    }

    public User create(String name, String email) {
        return userRepository.create(name, email);
    }

    public Optional<User> update(long id, String name, String email) {
        return userRepository.update(id, name, email);
    }

    public boolean delete(long id) {
        return userRepository.delete(id);
    }
}
