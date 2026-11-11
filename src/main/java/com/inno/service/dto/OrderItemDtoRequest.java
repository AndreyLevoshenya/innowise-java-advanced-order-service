package com.inno.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemDtoRequest(
        @NotNull(message = "Item ID is required")
        @Positive(message = "Item ID must be positive")
        Long itemId,
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        Integer quantity) {
}
