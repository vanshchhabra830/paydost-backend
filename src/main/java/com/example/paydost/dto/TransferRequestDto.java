package com.example.paydost.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequestDto {

    @NotBlank(message = "Receiver email is required")
    @Email(message = "Please provide a valid receiver email")
    private String receiverEmail;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    /**
     * Client-generated UUID for idempotency.
     * If the client retries a failed network request with the same referenceId,
     * the server will return the existing transaction instead of processing again.
     */
    @NotBlank(message = "Reference ID is required (generate a UUID on the client)")
    private String referenceId;
}
