package com.example.paydost.service;

import com.example.paydost.dto.TransactionResponseDto;
import com.example.paydost.dto.TransferRequestDto;
import com.example.paydost.exception.InsufficientBalanceException;
import com.example.paydost.exception.InvalidAmountException;
import com.example.paydost.model.Transaction;
import com.example.paydost.model.TransactionStatus;
import com.example.paydost.model.User;
import com.example.paydost.model.Wallet;
import com.example.paydost.repository.TransactionRepository;
import com.example.paydost.repository.UserRepository;
import com.example.paydost.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    /**
     * Transfer money from the sender to the receiver.
     *
     * WHY @Transactional IS CRITICAL HERE:
     * ─────────────────────────────────────
     * This method performs MULTIPLE database writes that MUST succeed or fail together:
     *   1. Debit sender's wallet
     *   2. Credit receiver's wallet
     *   3. Save the transaction record
     *
     * Without @Transactional, imagine this scenario:
     *   - Step 1 succeeds: sender is debited ₹500
     *   - Step 2 fails: DB error, receiver never gets credited
     *   - Result: ₹500 has VANISHED — debited from sender but never credited to receiver
     *
     * With @Transactional, Spring wraps all three steps in a single database transaction.
     * If ANY step throws an exception (runtime or checked if configured), the entire
     * transaction is ROLLED BACK — the sender's debit is undone, and the database
     * stays consistent. All-or-nothing.
     *
     * This is the "A" (Atomicity) in ACID.
     */
    @Transactional
    public TransactionResponseDto transfer(String senderEmail, TransferRequestDto request) {

        // ── IDEMPOTENCY CHECK ──
        // If a transaction with this referenceId already exists, return it as-is.
        // This prevents double-processing when a client retries a failed network request.
        Optional<Transaction> existing = transactionRepository.findByReferenceId(request.getReferenceId());
        if (existing.isPresent()) {
            return mapToDto(existing.get());
        }

        // ── RESOLVE SENDER AND RECEIVER ──
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Sender not found: " + senderEmail));

        User receiver = userRepository.findByEmail(request.getReceiverEmail())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Receiver not found with email: " + request.getReceiverEmail()));

        // Prevent self-transfer
        if (sender.getId().equals(receiver.getId())) {
            throw new InvalidAmountException("Cannot transfer money to yourself");
        }

        // ── FETCH WALLETS ──
        Wallet senderWallet = walletRepository.findByUserId(sender.getId())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        Wallet receiverWallet = walletRepository.findByUserId(receiver.getId())
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        BigDecimal amount = request.getAmount();

        // ── VALIDATE SUFFICIENT BALANCE ──
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Current balance: ₹" + senderWallet.getBalance()
                            + ", attempted transfer: ₹" + amount);
        }

        // ── DEBIT SENDER, CREDIT RECEIVER ──
        // If credit fails after debit, @Transactional rolls back both.
        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        walletRepository.save(senderWallet);

        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));
        walletRepository.save(receiverWallet);

        // ── SAVE TRANSACTION RECORD ──
        Transaction transaction = Transaction.builder()
                .senderId(sender.getId())
                .receiverId(receiver.getId())
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .referenceId(request.getReferenceId())
                .build();

        transactionRepository.save(transaction);

        return mapToDto(transaction);
    }

    /**
     * Get paginated transaction history for a user (both sent and received).
     */
    public Page<TransactionResponseDto> getHistory(Long userId, Pageable pageable) {
        Page<Transaction> transactions = transactionRepository
                .findBySenderIdOrReceiverIdOrderByTimestampDesc(userId, userId, pageable);

        return transactions.map(this::mapToDto);
    }

    // ── HELPER ──

    private TransactionResponseDto mapToDto(Transaction tx) {
        return TransactionResponseDto.builder()
                .id(tx.getId())
                .senderId(tx.getSenderId())
                .receiverId(tx.getReceiverId())
                .amount(tx.getAmount())
                .status(tx.getStatus())
                .referenceId(tx.getReferenceId())
                .timestamp(tx.getTimestamp())
                .build();
    }
}
