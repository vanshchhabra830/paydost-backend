package com.example.paydost.service;

import com.example.paydost.exception.InvalidAmountException;
import com.example.paydost.model.User;
import com.example.paydost.model.Wallet;
import com.example.paydost.repository.UserRepository;
import com.example.paydost.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    /**
     * Get the current balance for the given user.
     */
    public BigDecimal getBalance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user id: " + userId));
        return wallet.getBalance();
    }

    /**
     * Add money to the user's wallet.
     * Validates that the amount is positive before updating.
     */
    @Transactional
    public BigDecimal addMoney(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than 0");
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user id: " + userId));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        return wallet.getBalance();
    }

    /**
     * Helper: Find user by email and return their ID.
     * Used by WalletController to resolve the authenticated user's ID.
     */
    public Long getUserIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return user.getId();
    }
}
