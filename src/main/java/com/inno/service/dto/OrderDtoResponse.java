package com.inno.service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDtoResponse(
        Long id,
        String status,
        BigDecimal totalPrice,
        List<OrderItemDtoResponse> orderItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UserDtoResponse user
) {
}