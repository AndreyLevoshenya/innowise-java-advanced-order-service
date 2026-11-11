package com.inno.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.inno.repository.ItemRepository;
import com.inno.repository.OrderRepository;
import com.inno.repository.model.Item;
import com.inno.repository.model.Order;
import com.inno.service.client.UserServiceClient;
import com.inno.service.dto.OrderDtoRequest;
import com.inno.service.dto.OrderDtoResponse;
import com.inno.service.dto.OrderItemDtoRequest;
import com.inno.service.dto.SearchingRequest;
import com.inno.service.dto.UserDtoResponse;
import com.inno.service.exception.NotFoundException;
import com.inno.service.mapper.OrderDtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDtoMapper orderDtoMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;
    private Item item;
    private OrderDtoRequest request;
    private OrderDtoResponse response;
    private UserDtoResponse userResponse;

    @BeforeEach
    void setUp() {
        item = new Item();
        item.setName("Keyboard");
        item.setPrice(new BigDecimal("100.00"));

        order = new Order();
        order.setUserId(1L);

        request = new OrderDtoRequest(
                1L,
                List.of(new OrderItemDtoRequest(10L, 2))
        );

        response = mock(OrderDtoResponse.class);
        userResponse = mock(UserDtoResponse.class);
    }

    // findAll

    @Test
    void findAllWithoutSearchShouldReturnAllOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orders = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(pageable)).thenReturn(orders);
        when(userServiceClient.getUserById(1L)).thenReturn(userResponse);
        when(orderDtoMapper.modelToDto(order, userResponse)).thenReturn(response);

        Page<OrderDtoResponse> result =
                orderService.findAll(null, pageable);

        assertEquals(1, result.getTotalElements());
        assertSame(response, result.getContent().getFirst());

        verify(orderRepository).findAll(pageable);
        verify(userServiceClient).getUserById(1L);
        verify(orderDtoMapper).modelToDto(order, userResponse);

        verify(orderRepository, never())
                .findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void findAllWithSearchShouldReturnFilteredOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        SearchingRequest searchingRequest = mock(SearchingRequest.class);

        when(searchingRequest.getValue()).thenReturn("CREATED");

        Page<Order> orders = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(
                any(Specification.class), eq(pageable)
        )).thenReturn(orders);

        when(userServiceClient.getUserById(1L)).thenReturn(userResponse);
        when(orderDtoMapper.modelToDto(order, userResponse)).thenReturn(response);

        Page<OrderDtoResponse> result =
                orderService.findAll(searchingRequest, pageable);

        assertEquals(1, result.getTotalElements());
        assertSame(response, result.getContent().getFirst());

        verify(orderRepository).findAll(
                any(Specification.class), eq(pageable)
        );
        verify(orderRepository, never()).findAll(pageable);
    }

    @Test
    void findAllWithSearchShouldReturnEmptyPageWhenNoOrdersMatch() {
        Pageable pageable = PageRequest.of(0, 10);
        SearchingRequest searchingRequest = mock(SearchingRequest.class);

        when(searchingRequest.getValue()).thenReturn("UNKNOWN");

        when(orderRepository.findAll(
                any(Specification.class), eq(pageable)
        )).thenReturn(Page.empty(pageable));

        Page<OrderDtoResponse> result =
                orderService.findAll(searchingRequest, pageable);

        assertTrue(result.isEmpty());

        verifyNoInteractions(orderDtoMapper, userServiceClient);
    }

    // findById

    @Test
    void findByIdShouldReturnOrderWhenFound() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        when(userServiceClient.getUserById(1L)).thenReturn(userResponse);
        when(orderDtoMapper.modelToDto(order, userResponse)).thenReturn(response);

        OrderDtoResponse result = orderService.findById(10L);

        assertSame(response, result);

        verify(orderRepository).findById(10L);
        verify(userServiceClient).getUserById(1L);
        verify(orderDtoMapper).modelToDto(order, userResponse);
    }

    @Test
    void findByIdShouldThrowWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> orderService.findById(10L)
        );

        verifyNoInteractions(orderDtoMapper, userServiceClient);
    }

    // create

    @Test
    void createShouldSaveOrderAndReturnResponse() {
        when(orderDtoMapper.dtoToModel(request)).thenReturn(order);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(orderRepository.save(order)).thenReturn(order);

        when(userServiceClient.getUserById(1L)).thenReturn(userResponse);
        when(orderDtoMapper.modelToDto(order, userResponse)).thenReturn(response);

        OrderDtoResponse result = orderService.create(request);

        assertSame(response, result);
        assertEquals(new BigDecimal("200.00"), order.getTotalPrice());

        assertEquals(1, order.getOrderItems().size());
        assertSame(item, order.getOrderItems().getFirst().getItem());
        assertEquals(2, order.getOrderItems().getFirst().getQuantity());

        verify(orderDtoMapper).dtoToModel(request);
        verify(itemRepository).findById(10L);
        verify(orderRepository).save(order);
        verify(userServiceClient).getUserById(1L);
        verify(orderDtoMapper).modelToDto(order, userResponse);
    }

    @Test
    void createShouldThrowWhenItemDoesNotExist() {
        when(orderDtoMapper.dtoToModel(request)).thenReturn(order);
        when(itemRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> orderService.create(request)
        );

        verify(itemRepository).findById(10L);
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(userServiceClient);
    }

    // update

    @Test
    void updateShouldModifyExistingOrder() {
        Item updatedItem = new Item();
        updatedItem.setName("Mouse");
        updatedItem.setPrice(new BigDecimal("50.00"));

        OrderDtoRequest updateRequest = new OrderDtoRequest(
                2L,
                List.of(new OrderItemDtoRequest(20L, 3))
        );

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        when(itemRepository.findById(20L))
                .thenReturn(Optional.of(updatedItem));

        when(userServiceClient.getUserById(2L)).thenReturn(userResponse);
        when(orderDtoMapper.modelToDto(order, userResponse)).thenReturn(response);

        OrderDtoResponse result =
                orderService.update(10L, updateRequest);

        assertSame(response, result);

        assertEquals(2L, order.getUserId());
        assertEquals(new BigDecimal("150.00"), order.getTotalPrice());

        assertEquals(1, order.getOrderItems().size());
        assertSame(updatedItem, order.getOrderItems().getFirst().getItem());
        assertEquals(3, order.getOrderItems().getFirst().getQuantity());

        verify(orderRepository).findById(10L);
        verify(itemRepository).findById(20L);
        verify(userServiceClient).getUserById(2L);
        verify(orderDtoMapper).modelToDto(order, userResponse);

        // Update relies on JPA dirty checking.
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateShouldThrowWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> orderService.update(10L, request)
        );

        verifyNoInteractions(itemRepository, orderDtoMapper, userServiceClient);
    }

    @Test
    void updateShouldThrowWhenOrderIsDeleted() {
        order.setDeleted(true);

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        assertThrows(
                NotFoundException.class,
                () -> orderService.update(10L, request)
        );

        verifyNoInteractions(itemRepository, orderDtoMapper, userServiceClient);
    }

    @Test
    void updateShouldThrowWhenItemDoesNotExist() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        when(itemRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> orderService.update(10L, request)
        );

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(userServiceClient);
    }

    // deleteById

    @Test
    void deleteByIdShouldSoftDeleteExistingOrder() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        orderService.deleteById(10L);

        assertTrue(order.isDeleted());

        verify(orderRepository).findById(10L);
        verify(orderRepository, never()).deleteById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void deleteByIdShouldThrowWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> orderService.deleteById(10L)
        );

        verify(orderRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteByIdShouldThrowWhenOrderIsAlreadyDeleted() {
        order.setDeleted(true);

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        assertThrows(
                NotFoundException.class,
                () -> orderService.deleteById(10L)
        );

        verify(orderRepository, never()).deleteById(anyLong());
    }

    // findAllByUserIdAndDeletedFalse

    @Test
    void findAllByUserIdAndDeletedFalseShouldReturnUserOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orders = new PageImpl<>(List.of(order));

        when(orderRepository.findAllByUserIdAndDeletedFalse(1L, pageable))
                .thenReturn(orders);

        when(userServiceClient.getUserById(1L)).thenReturn(userResponse);
        when(orderDtoMapper.modelToDto(order, userResponse)).thenReturn(response);

        Page<OrderDtoResponse> result =
                orderService.findAllByUserIdAndDeletedFalse(1L, pageable);

        assertEquals(1, result.getTotalElements());
        assertSame(response, result.getContent().getFirst());

        verify(orderRepository).findAllByUserIdAndDeletedFalse(1L, pageable);
        verify(userServiceClient).getUserById(1L);
        verify(orderDtoMapper).modelToDto(order, userResponse);
    }

    @Test
    void findAllByUserIdAndDeletedFalseShouldReturnEmptyPageWhenNoOrdersExist() {
        Pageable pageable = PageRequest.of(0, 10);

        when(orderRepository.findAllByUserIdAndDeletedFalse(1L, pageable))
                .thenReturn(Page.empty(pageable));

        Page<OrderDtoResponse> result =
                orderService.findAllByUserIdAndDeletedFalse(1L, pageable);

        assertTrue(result.isEmpty());

        verifyNoInteractions(orderDtoMapper, userServiceClient);
    }

    // isOwner

    @Test
    void isOwnerShouldReturnTrueWhenUserOwnsOrder() {
        when(orderRepository.existsByIdAndUserIdAndDeletedFalse(10L, 1L))
                .thenReturn(true);

        boolean result = orderService.isOwner(10L, 1L);

        assertTrue(result);

        verify(orderRepository).existsByIdAndUserIdAndDeletedFalse(10L, 1L);
    }

    @Test
    void isOwnerShouldReturnFalseWhenUserDoesNotOwnOrder() {
        when(orderRepository.existsByIdAndUserIdAndDeletedFalse(10L, 2L))
                .thenReturn(false);

        boolean result = orderService.isOwner(10L, 2L);

        assertFalse(result);

        verify(orderRepository).existsByIdAndUserIdAndDeletedFalse(10L, 2L);
    }

    @Test
    void create_shouldCalculateTotalPriceForMultipleItems() {
        // Arrange
        Long userId = 1L;

        Item item1 = new Item("item1", new BigDecimal("100.00"));

        Item item2 = new Item("item2", new BigDecimal("50.00"));

        List<OrderItemDtoRequest> orderItems = List.of(
                new OrderItemDtoRequest(1L, 2),
                new OrderItemDtoRequest(2L, 3)
        );

        OrderDtoRequest request = new OrderDtoRequest(userId, orderItems);

        Order order = new Order();
        order.setUserId(userId);

        UserDtoResponse user = mock(UserDtoResponse.class);
        OrderDtoResponse expectedResponse = mock(OrderDtoResponse.class);

        when(orderDtoMapper.dtoToModel(request)).thenReturn(order);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userServiceClient.getUserById(userId)).thenReturn(user);
        when(orderDtoMapper.modelToDto(order, user)).thenReturn(expectedResponse);

        // Act
        OrderDtoResponse result = orderService.create(request);

        // Assert
        assertEquals(expectedResponse, result);
        assertEquals(new BigDecimal("350.00"), order.getTotalPrice());

        verify(itemRepository).findById(1L);
        verify(itemRepository).findById(2L);
        verify(orderRepository).save(order);
    }
}
