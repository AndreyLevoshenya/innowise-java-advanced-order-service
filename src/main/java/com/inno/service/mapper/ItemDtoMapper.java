package com.inno.service.mapper;

import com.inno.repository.model.Item;
import com.inno.service.dto.ItemDtoRequest;
import com.inno.service.dto.ItemDtoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ItemDtoMapper {
    ItemDtoResponse modelToDto(Item model);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Item dtoToModel(ItemDtoRequest dtoRequest);
}
