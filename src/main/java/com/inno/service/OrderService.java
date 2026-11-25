package com.inno.service;

import com.inno.service.dto.OrderDtoRequest;
import com.inno.service.dto.OrderDtoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService extends BaseService<OrderDtoRequest, OrderDtoResponse, Long> {

    Page<OrderDtoResponse> findAllByUserIdAndDeletedFalse(UUID userId, Pageable pageable);

    boolean isOwner(Long orderId, UUID userId);
}
