package com.inno.service.impl;

import com.inno.filter.EntitySpecification;
import com.inno.repository.ItemRepository;
import com.inno.repository.model.Item;
import com.inno.service.BaseService;
import com.inno.service.dto.ItemDtoRequest;
import com.inno.service.dto.ItemDtoResponse;
import com.inno.service.dto.SearchingRequest;
import com.inno.service.exception.NotFoundException;
import com.inno.service.mapper.ItemDtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.inno.service.exception.ExceptionErrorCodes.ITEM_DOES_NOT_EXIST;

@Service
public class ItemServiceImpl implements BaseService<ItemDtoRequest, ItemDtoResponse, Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemServiceImpl.class);
    private static final List<String> fieldsToSearch = List.of("name");
    private final ItemRepository itemRepository;
    private final ItemDtoMapper itemDtoMapper;

    @Autowired
    public ItemServiceImpl(ItemRepository itemRepository, ItemDtoMapper itemDtoMapper) {
        this.itemRepository = itemRepository;
        this.itemDtoMapper = itemDtoMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemDtoResponse> findAll(SearchingRequest searchingRequest, Pageable pageable) {
        if (searchingRequest == null) {
            LOGGER.info("Reading all items");
            return itemRepository.findAll(pageable).map(itemDtoMapper::modelToDto);
        }
        LOGGER.info("Reading all items for {}", searchingRequest.getValue());

        Specification<Item> specification = EntitySpecification.searchByFields(fieldsToSearch, searchingRequest.getValue());
        return itemRepository.findAll(specification, pageable).map(itemDtoMapper::modelToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemDtoResponse findById(Long id) {
        LOGGER.info("Reading item by id {}", id);
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> {
                    LOGGER.warn("Item with id {} not found", id);
                    return new NotFoundException(String.format(ITEM_DOES_NOT_EXIST.getErrorMessage(), id));
                });
        return itemDtoMapper.modelToDto(item);
    }

    @Override
    @Transactional
    public ItemDtoResponse create(ItemDtoRequest createRequest) {
        LOGGER.info("Creating item {}", createRequest.toString());

        Item model = itemDtoMapper.dtoToModel(createRequest);
        Item item = itemRepository.save(model);
        return itemDtoMapper.modelToDto(item);
    }

    @Override
    @Transactional
    public ItemDtoResponse update(Long id, ItemDtoRequest updateRequest) {
        LOGGER.info("Updating item with id {}", id);

        Item item = itemRepository.findById(id)
                .orElseThrow(() -> {
                    LOGGER.warn("Item with id {} not found. Unable to update item", id);
                    return new NotFoundException(String.format(ITEM_DOES_NOT_EXIST.getErrorMessage(), id));
                });

        item.setName(updateRequest.name());
        item.setPrice(updateRequest.price());
        return itemDtoMapper.modelToDto(itemRepository.save(item));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        LOGGER.info("Deleting item by id {}", id);
        if (!itemRepository.existsById(id)) {
            LOGGER.warn("Item with id {} not found. Unable to delete item", id);
            throw new NotFoundException(String.format(ITEM_DOES_NOT_EXIST.getErrorMessage(), id));
        }
        itemRepository.deleteById(id);
    }
}
