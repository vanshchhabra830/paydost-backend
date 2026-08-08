package com.example.paydost.controller;

import com.example.paydost.dto.TransactionResponseDto;
import com.example.paydost.dto.TransferRequestDto;
import com.example.paydost.service.TransactionService;
import com.example.paydost.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final WalletService walletService;

    /**
     * POST /api/transactions/transfer
     *
     * Transfer money from the authenticated user to a receiver (by email).
     * The sender is ALWAYS derived from the JWT — never from the request body.
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponseDto> transfer(
            @Valid @RequestBody TransferRequestDto request) {

        String senderEmail = getAuthenticatedEmail();

        TransactionResponseDto response = transactionService.transfer(senderEmail, request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/transactions/history?page=0&size=10
     *
     * Returns paginated transaction history (sent + received) for the logged-in user.
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String email = getAuthenticatedEmail();
        Long userId = walletService.getUserIdByEmail(email);

        Pageable pageable = PageRequest.of(page, size);
        Page<TransactionResponseDto> historyPage = transactionService.getHistory(userId, pageable);

        return ResponseEntity.ok(Map.of(
                "transactions", historyPage.getContent(),
                "currentPage", historyPage.getNumber(),
                "totalItems", historyPage.getTotalElements(),
                "totalPages", historyPage.getTotalPages()
        ));
    }

    // ── Extract logged-in user's email from SecurityContext ──
    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
