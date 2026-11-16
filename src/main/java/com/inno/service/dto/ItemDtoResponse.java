package com.inno.service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemDtoResponse(
        Long id,
        String name,
        BigDecimal price,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
