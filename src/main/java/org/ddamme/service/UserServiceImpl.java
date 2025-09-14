package org.ddamme.service;

import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.Role;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.UserRepository;
import org.ddamme.dto.RegisterRequest;
import org.ddamme.exception.DuplicateResourceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public User registerUser(RegisterRequest request) {
    if (userRepository.findByUsername(request.getUsername()).isPresent()) {
      throw new DuplicateResourceException(
          "User with username " + request.getUsername() + " already exists");
    }

    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new DuplicateResourceException(
          "User with email " + request.getEmail() + " already exists");
    }

    User user =
        User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(Role.USER)
            .build();

    return userRepository.save(user);
  }
}
