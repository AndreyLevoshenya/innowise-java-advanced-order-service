package com.inno.service.mapper;

import com.inno.repository.model.OrderItem;
import com.inno.service.dto.OrderItemDtoRequest;
import com.inno.service.dto.OrderItemDtoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = ItemDtoMapper.class
)
public interface OrderItemDtoMapper {
    OrderItemDtoResponse modelToDto(OrderItem model);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "item", ignore = true)
    OrderItem dtoToModel(OrderItemDtoRequest dtoRequest);
}
