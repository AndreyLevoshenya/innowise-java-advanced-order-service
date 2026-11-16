package com.inno.service.dto;

import java.time.LocalDateTime;

public record OrderItemDtoResponse(
        ItemDtoResponse item,
        Integer quantity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
