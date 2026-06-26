package com.gemono.backend.service;

import com.gemono.backend.model.User;
import com.gemono.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Find existing OAuth user or create new one
    public User findOrCreateOAuthUser(String email, String name, String picture,
                                       String provider, String providerId) {
        Optional<User> existing = userRepository.findByProviderAndProviderId(provider, providerId);

        if (existing.isPresent()) {
            User user = existing.get();
            // Update avatar if changed
            user.setAvatarUrl(picture);
            user.setFullName(name);
            return userRepository.save(user);
        }

        // Check if email already registered manually
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setProvider(provider);
            user.setProviderId(providerId);
            user.setAvatarUrl(picture);
            return userRepository.save(user);
        }

        // Create brand new user
        User newUser = User.builder()
                .email(email)
                .fullName(name)
                .avatarUrl(picture)
                .role(User.Role.USER)
                .provider(provider)
                .providerId(providerId)
                .build();

        return userRepository.save(newUser);
    }

    // Register via email + password
    public User registerUser(String email, String fullName, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .password(passwordEncoder.encode(rawPassword))
                .role(User.Role.USER)
                .provider("local")
                .build();

        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
}