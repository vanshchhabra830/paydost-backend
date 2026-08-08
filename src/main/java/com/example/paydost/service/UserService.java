package com.example.paydost.service;

import com.example.paydost.dto.AuthResponseDto;
import com.example.paydost.dto.RegisterRequestDto;
import com.example.paydost.exception.UserAlreadyExistsException;
import com.example.paydost.model.User;
import com.example.paydost.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthResponseDto register(RegisterRequestDto request) {
        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException(
                    "User with email '" + request.getEmail() + "' already exists");
        }

        // Build and save the user with hashed password
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        return AuthResponseDto.builder()
                .message("User registered successfully")
                .email(user.getEmail())
                .build();
    }
}
