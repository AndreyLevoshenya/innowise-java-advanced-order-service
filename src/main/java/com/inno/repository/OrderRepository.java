package com.inno.repository;

import com.inno.repository.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Page<Order> findAllByUserIdAndDeletedFalse(
            UUID userId,
            Pageable pageable
    );

    boolean existsByIdAndUserIdAndDeletedFalse(Long id, UUID userId);
}
