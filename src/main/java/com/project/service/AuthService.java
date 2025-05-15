package com.project.service;

import com.project.model.dto.AuthRequest;
import com.project.model.dto.AuthResponse;
import com.project.model.dto.SignUpRequest;
import com.project.repository.UserRepository;

public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthResponse authorize(AuthRequest request) throws Exception {
        AuthResponse response = userRepository.getAuthResponseByUsername(request.getUsername());
        if (response == null) {
            throw new Exception("User not found");
        }

        if (!response.getPassword().equals(request.getPassword())) {
            throw new Exception("Incorrect password");
        }

        response.setPassword(null);
        response.setUserName(request.getUsername());
        return response;
    }

    public AuthResponse register(SignUpRequest request) throws Exception {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new Exception("Username already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email already in use");
        }

        AuthResponse response = userRepository.createUser(request);

        if (response == null) {
            throw new Exception("Failed to create user");
        }

        response.setPassword(null);
        response.setUserName(request.getUsername());
        return response;
    }

}