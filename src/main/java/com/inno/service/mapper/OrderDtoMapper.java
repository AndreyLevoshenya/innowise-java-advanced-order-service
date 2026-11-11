package com.inno.service.mapper;

import com.inno.repository.model.Order;
import com.inno.service.dto.OrderDtoRequest;
import com.inno.service.dto.OrderDtoResponse;
import com.inno.service.dto.UserDtoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = OrderItemDtoMapper.class
)
public interface OrderDtoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "status", constant = "NEW")
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    @Mapping(target = "userId", source = "userId")
    Order dtoToModel(OrderDtoRequest dtoRequest);

    @Mapping(target = "id", source = "model.id")
    @Mapping(target = "createdAt", source = "model.createdAt")
    @Mapping(target = "updatedAt", source = "model.updatedAt")
    @Mapping(target = "orderItems", source = "model.orderItems")
    @Mapping(target = "user", source = "user")
    OrderDtoResponse modelToDto(Order model, UserDtoResponse user);
}
