package com.project.service;

import com.project.entity.dto.AuthRequest;
import com.project.entity.dto.AuthResponse;
import com.project.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthResponse authenticate(AuthRequest request) {
        try {
            AuthResponse response = userRepository.getAuthResponseByUsername(request.getUsername());
            if (response == null) {
                log.warn("User not found: {}", request.getUsername());
                return null;
            }

            if (!response.getPassword().equals(request.getPassword())) {
                log.warn("Incorrect password for user: {}", request.getUsername());
                return null;
            }

            response.setPassword(null);
            return response;

        } catch (Exception e) {
            log.error("Authentication error", e);
            return null;
        }
    }
}