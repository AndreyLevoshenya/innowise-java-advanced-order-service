package com.inno.service;

import com.inno.service.dto.OrderDtoRequest;
import com.inno.service.dto.OrderDtoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService extends BaseService<OrderDtoRequest, OrderDtoResponse, Long> {

    Page<OrderDtoResponse> findAllByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    boolean isOwner(Long orderId, Long userId);
}
