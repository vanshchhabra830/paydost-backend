package com.example.paydost.service;

import com.example.paydost.dto.AuthResponseDto;
import com.example.paydost.dto.RegisterRequestDto;
import com.example.paydost.exception.UserAlreadyExistsException;
import com.example.paydost.model.User;
import com.example.paydost.model.Wallet;
import com.example.paydost.repository.UserRepository;
import com.example.paydost.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
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

        // Create a wallet with 0.00 balance for the new user
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();

        walletRepository.save(wallet);

        return AuthResponseDto.builder()
                .message("User registered successfully")
                .email(user.getEmail())
                .build();
    }
}

