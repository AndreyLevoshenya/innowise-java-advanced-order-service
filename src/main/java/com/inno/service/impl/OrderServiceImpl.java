package com.inno.service.impl;

import com.inno.filter.EntitySpecification;
import com.inno.repository.ItemRepository;
import com.inno.repository.OrderRepository;
import com.inno.repository.model.Item;
import com.inno.repository.model.Order;
import com.inno.repository.model.OrderItem;
import com.inno.service.OrderService;
import com.inno.service.client.UserServiceClient;
import com.inno.service.dto.OrderDtoRequest;
import com.inno.service.dto.OrderDtoResponse;
import com.inno.service.dto.OrderItemDtoRequest;
import com.inno.service.dto.SearchingRequest;
import com.inno.service.dto.UserDtoResponse;
import com.inno.service.exception.NotFoundException;
import com.inno.service.mapper.OrderDtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.inno.service.exception.ExceptionErrorCodes.ITEM_DOES_NOT_EXIST;
import static com.inno.service.exception.ExceptionErrorCodes.ORDER_DOES_NOT_EXIST;

@Service
public class OrderServiceImpl implements OrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final List<String> fieldsToSearch = List.of("name", "status");

    private final OrderRepository orderRepository;
    private final OrderDtoMapper orderDtoMapper;
    private final UserServiceClient userServiceClient;
    private final ItemRepository itemRepository;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository, OrderDtoMapper orderDtoMapper, UserServiceClient userServiceClient, ItemRepository itemRepository) {
        this.orderRepository = orderRepository;
        this.orderDtoMapper = orderDtoMapper;
        this.userServiceClient = userServiceClient;
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDtoResponse> findAll(SearchingRequest searchingRequest, Pageable pageable) {
        Page<Order> orders;
        if (searchingRequest == null) {
            LOGGER.info("Reading all orders");
             orders = orderRepository.findAll(pageable);
        } else {
            LOGGER.info("Reading all orders for {}", searchingRequest.getValue());
            Specification<Order> specification = EntitySpecification.searchByFields(fieldsToSearch, searchingRequest.getValue());
            orders = orderRepository.findAll(specification, pageable);
        }

        return orders.map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDtoResponse findById(Long id) {
        LOGGER.info("Reading order by id {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> {
                    LOGGER.warn("Order with id {} not found", id);
                    return new NotFoundException(String.format(ORDER_DOES_NOT_EXIST.getErrorMessage(), id));
                });
        return toDto(order);
    }

    @Override
    @Transactional
    public OrderDtoResponse create(OrderDtoRequest createRequest) {
        LOGGER.info("Creating order {}", createRequest.toString());

        Order model = toModel(createRequest);

        Order order = orderRepository.save(model);
        return toDto(order);
    }

    @Override
    @Transactional
    public OrderDtoResponse update(Long id, OrderDtoRequest updateRequest) {
        LOGGER.info("Updating order with id {}", id);

        Order order = orderRepository.findById(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> new NotFoundException(
                        String.format(ORDER_DOES_NOT_EXIST.getErrorMessage(), id)
                ));

        updateModel(order, updateRequest);

        return toDto(order);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        LOGGER.info("Deleting order with id {}", id);

        Order order = orderRepository.findById(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> new NotFoundException(
                        String.format(ORDER_DOES_NOT_EXIST.getErrorMessage(), id)
                ));

        order.setDeleted(true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDtoResponse> findAllByUserIdAndDeletedFalse(UUID userId, Pageable pageable) {
        LOGGER.info("Reading all orders for user with id {}", userId);

        return orderRepository.findAllByUserIdAndDeletedFalse(userId, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwner(Long orderId, UUID userId) {
        return orderRepository.existsByIdAndUserIdAndDeletedFalse(
                orderId, userId
        );
    }

    private Order toModel(OrderDtoRequest request) {
        Order order = orderDtoMapper.dtoToModel(request);

        populateOrderItems(order, request.orderItems());
        order.setTotalPrice(calculateTotalPrice(order));

        return order;
    }

    private OrderDtoResponse toDto(Order order) {
        UserDtoResponse user = userServiceClient.getUserById(order.getUserId());
        return orderDtoMapper.modelToDto(order, user);
    }

    private BigDecimal calculateTotalPrice(Order order) {
        return order.getOrderItems().stream()
                .map(item -> item.getItem().getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void populateOrderItems(Order order, List<OrderItemDtoRequest> requests) {
        for (var request : requests) {
            Item item = itemRepository.findById(request.itemId())
                    .orElseThrow(() -> {
                        LOGGER.warn("Item with id {} not found", request.itemId());
                        return new NotFoundException(
                                String.format(ITEM_DOES_NOT_EXIST.getErrorMessage(), request.itemId())
                        );
                    });

            order.addOrderItem(new OrderItem(item, request.quantity()));
        }
    }

    private void updateModel(Order order, OrderDtoRequest request) {
        order.setUserId(request.userId());

        order.clearOrderItems();

        populateOrderItems(order, request.orderItems());

        order.setTotalPrice(calculateTotalPrice(order));
    }
}
