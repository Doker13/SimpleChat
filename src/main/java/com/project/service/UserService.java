package com.project.service;

import com.project.model.dto.UserSearchResponse;
import com.project.repository.UserRepository;

import java.util.List;

public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSearchResponse> searchUsers (String displayName) throws Exception {
        List<UserSearchResponse> displayNames = userRepository.getDisplayNames(displayName);
        if (displayNames.isEmpty()) {
            throw new Exception("No users found");
        }
        return displayNames;
    }
}
