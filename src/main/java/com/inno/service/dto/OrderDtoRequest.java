package com.inno.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderDtoRequest(
        @NotNull(message = "User ID is required")
        @Positive(message = "User ID must be positive")
        Long userId,
        @NotEmpty(message = "Order must contain at least one item")
        List<@Valid OrderItemDtoRequest> orderItems
) {
}
