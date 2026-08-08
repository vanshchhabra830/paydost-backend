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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TXN_CACHE_PREFIX = "txn:";
    private static final long TXN_CACHE_TTL_HOURS = 24;

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
     *
     * REDIS IDEMPOTENCY LAYER:
     * ────────────────────────
     * Redis is used as a FAST FIRST-CHECK before hitting the DB. Why?
     * - Redis lookup is sub-millisecond (in-memory) vs ~5-10ms for a DB query.
     * - On retried requests (same referenceId), we return the cached result instantly.
     * - Redis TTL (24h) auto-evicts old entries — no cleanup job needed.
     * - The DB referenceId UNIQUE constraint remains the source of truth.
     */
    @Transactional
    public TransactionResponseDto transfer(String senderEmail, TransferRequestDto request) {

        // ── IDEMPOTENCY CHECK: REDIS FIRST (fast), then DB (source of truth) ──
        String cacheKey = TXN_CACHE_PREFIX + request.getReferenceId();

        // Check Redis cache first — sub-millisecond lookup
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Idempotency hit (Redis cache) for referenceId: {}", request.getReferenceId());
            return mapFromCachedObject(cached);
        }

        // Redis miss — check DB (source of truth)
        Optional<Transaction> existing = transactionRepository.findByReferenceId(request.getReferenceId());
        if (existing.isPresent()) {
            log.info("Idempotency hit (DB) for referenceId: {}", request.getReferenceId());
            TransactionResponseDto dto = mapToDto(existing.get());
            // Backfill Redis cache for future lookups
            cacheTransaction(cacheKey, dto);
            return dto;
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

        TransactionResponseDto dto = mapToDto(transaction);

        // Cache in Redis for fast idempotency lookups (24h TTL)
        cacheTransaction(cacheKey, dto);

        return dto;
    }

    /**
     * Get paginated transaction history for a user (both sent and received).
     */
    public Page<TransactionResponseDto> getHistory(Long userId, Pageable pageable) {
        Page<Transaction> transactions = transactionRepository
                .findBySenderIdOrReceiverIdOrderByTimestampDesc(userId, userId, pageable);

        return transactions.map(this::mapToDto);
    }

    // ── HELPERS ──

    private void cacheTransaction(String cacheKey, TransactionResponseDto dto) {
        try {
            redisTemplate.opsForValue().set(cacheKey, dto, TXN_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // Redis failure should not break the transfer — DB is the source of truth
            log.warn("Failed to cache transaction in Redis: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private TransactionResponseDto mapFromCachedObject(Object cached) {
        // GenericJackson2JsonRedisSerializer deserializes to a LinkedHashMap;
        // we need to convert it to our DTO
        if (cached instanceof TransactionResponseDto dto) {
            return dto;
        }
        // Fallback: if it's a Map (from Redis JSON deserialization), map manually
        if (cached instanceof java.util.Map) {
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) cached;
            return TransactionResponseDto.builder()
                    .id(map.get("id") != null ? Long.valueOf(map.get("id").toString()) : null)
                    .senderId(map.get("senderId") != null ? Long.valueOf(map.get("senderId").toString()) : null)
                    .receiverId(map.get("receiverId") != null ? Long.valueOf(map.get("receiverId").toString()) : null)
                    .amount(map.get("amount") != null ? new BigDecimal(map.get("amount").toString()) : null)
                    .status(map.get("status") != null ? TransactionStatus.valueOf(map.get("status").toString()) : null)
                    .referenceId(map.get("referenceId") != null ? map.get("referenceId").toString() : null)
                    .build();
        }
        throw new RuntimeException("Unexpected cached object type: " + cached.getClass());
    }

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
