package com.inno.controller.impl;

import com.inno.controller.BaseController;
import com.inno.service.OrderService;
import com.inno.service.dto.OrderDtoRequest;
import com.inno.service.dto.OrderDtoResponse;
import com.inno.service.dto.SearchingRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.inno.controller.RestConstants.ORDERS_V1_API_PATH;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping(value = ORDERS_V1_API_PATH)
public class OrderController implements BaseController<OrderDtoRequest, OrderDtoResponse, Long> {
    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Page<OrderDtoResponse>> findAll(
            @RequestParam(name = "status", required = false) String search,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SearchingRequest searchingRequest = null;
        if (search != null && !search.isBlank()) {
            searchingRequest = new SearchingRequest(search);
        }
        Page<OrderDtoResponse> pageDtoResponse = orderService.findAll(searchingRequest, pageable);
        return ResponseEntity.ok(pageDtoResponse);
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
    public ResponseEntity<Page<OrderDtoResponse>> findCurrentUserOrders(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long userId = jwt.getClaim("userId");

        Page<OrderDtoResponse> pageDtoResponse = orderService.findAllByUserIdAndDeletedFalse(userId, pageable);
        return ResponseEntity.ok(pageDtoResponse);
    }

    @Override
    @GetMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<OrderDtoResponse> findById(@PathVariable Long id) {
        OrderDtoResponse orderDtoResponse = orderService.findById(id);
        return ResponseEntity.ok(orderDtoResponse);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER')")
    public ResponseEntity<OrderDtoResponse> create(
            @Valid @RequestBody OrderDtoRequest createRequest,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        OrderDtoRequest request = new OrderDtoRequest(userId, createRequest.orderItems());
        OrderDtoResponse response = orderService.create(request);

        return ResponseEntity.status(CREATED).body(response);
    }

    @Override
    @PutMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or @orderService.isOwner(#id, authentication.token.claims['userId'])")
    public ResponseEntity<OrderDtoResponse> update(@PathVariable Long id, @Valid @RequestBody OrderDtoRequest updateRequest) {
        OrderDtoResponse orderDtoResponse = orderService.update(id, updateRequest);
        return ResponseEntity.ok(orderDtoResponse);
    }

    @Override
    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or @orderService.isOwner(#id, authentication.token.claims['userId'])")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        orderService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
