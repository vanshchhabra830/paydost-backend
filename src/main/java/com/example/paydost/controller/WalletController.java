package com.example.paydost.controller;

import com.example.paydost.dto.AddMoneyRequestDto;
import com.example.paydost.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * GET /api/wallet/balance
     *
     * Returns the authenticated user's wallet balance.
     *
     * HOW THE LOGGED-IN USER IS RESOLVED:
     * ────────────────────────────────────
     * 1. The JwtAuthFilter already validated the JWT from the Authorization header
     *    and set the Authentication object in SecurityContextHolder.
     *
     * 2. We call SecurityContextHolder.getContext().getAuthentication() to get it.
     *
     * 3. authentication.getName() returns the "principal name" — which is the email,
     *    because our CustomUserDetailsService uses email as the username.
     *
     * 4. We NEVER take userId from the request body/params for wallet operations.
     *    Always derive it from the JWT → SecurityContext → email → userId lookup.
     *    This prevents users from accessing other people's wallets.
     */
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance() {
        // ▼ Extract the logged-in user's email from SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Long userId = walletService.getUserIdByEmail(email);
        BigDecimal balance = walletService.getBalance(userId);

        return ResponseEntity.ok(Map.of(
                "email", email,
                "balance", balance
        ));
    }

    /**
     * POST /api/wallet/add-money
     *
     * Adds the specified amount to the authenticated user's wallet.
     */
    @PostMapping("/add-money")
    public ResponseEntity<Map<String, Object>> addMoney(@Valid @RequestBody AddMoneyRequestDto request) {
        // ▼ Same pattern: derive user from JWT, never from request params
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Long userId = walletService.getUserIdByEmail(email);
        BigDecimal newBalance = walletService.addMoney(userId, request.getAmount());

        return ResponseEntity.ok(Map.of(
                "message", "Money added successfully",
                "email", email,
                "newBalance", newBalance
        ));
    }
}
