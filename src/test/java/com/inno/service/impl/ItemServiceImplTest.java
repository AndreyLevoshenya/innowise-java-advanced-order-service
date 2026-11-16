package com.inno.service.impl;

import com.inno.repository.ItemRepository;
import com.inno.repository.model.Item;
import com.inno.service.dto.ItemDtoRequest;
import com.inno.service.dto.ItemDtoResponse;
import com.inno.service.dto.SearchingRequest;
import com.inno.service.exception.NotFoundException;
import com.inno.service.mapper.ItemDtoMapper;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemDtoMapper itemDtoMapper;

    @InjectMocks
    private ItemServiceImpl itemService;

    private Item item;
    private ItemDtoRequest request;
    private ItemDtoResponse response;

    @BeforeEach
    void setUp() {
        item = new Item();
        item.setName("Laptop");
        item.setPrice(new BigDecimal("1000.00"));

        request = new ItemDtoRequest(
                "Laptop",
                new BigDecimal("1000.00")
        );

        response = mock(ItemDtoResponse.class);
    }

    // findAll

    @Test
    void findAll_shouldReturnAllItemsWhenSearchIsNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Item> items = new PageImpl<>(List.of(item));

        when(itemRepository.findAll(pageable)).thenReturn(items);
        when(itemDtoMapper.modelToDto(item)).thenReturn(response);

        Page<ItemDtoResponse> result = itemService.findAll(null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().getFirst());

        verify(itemRepository).findAll(pageable);
        verify(itemRepository, never())
                .findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void findAll_shouldReturnFilteredItemsWhenSearchIsProvided() {
        Pageable pageable = PageRequest.of(0, 10);
        SearchingRequest searchingRequest = mock(SearchingRequest.class);

        when(searchingRequest.getValue()).thenReturn("Laptop");

        Page<Item> items = new PageImpl<>(List.of(item));

        when(itemRepository.findAll(
                any(Specification.class),
                eq(pageable)
        )).thenReturn(items);

        when(itemDtoMapper.modelToDto(item)).thenReturn(response);

        Page<ItemDtoResponse> result =
                itemService.findAll(searchingRequest, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().getFirst());

        verify(itemRepository).findAll(
                any(Specification.class),
                eq(pageable)
        );
    }

    @Test
    void findAll_shouldReturnEmptyPageWhenNoItemsFound() {
        Pageable pageable = PageRequest.of(0, 10);

        when(itemRepository.findAll(pageable))
                .thenReturn(Page.empty(pageable));

        Page<ItemDtoResponse> result = itemService.findAll(null, pageable);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());

        verify(itemRepository).findAll(pageable);
        verifyNoInteractions(itemDtoMapper);
    }

    // findById

    @Test
    void findById_shouldReturnItemWhenItemExists() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemDtoMapper.modelToDto(item)).thenReturn(response);

        ItemDtoResponse result = itemService.findById(1L);

        assertEquals(response, result);

        verify(itemRepository).findById(1L);
        verify(itemDtoMapper).modelToDto(item);
    }

    @Test
    void findById_shouldThrowNotFoundExceptionWhenItemDoesNotExist() {
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> itemService.findById(1L)
        );

        verify(itemRepository).findById(1L);
        verifyNoInteractions(itemDtoMapper);
    }

    // create

    @Test
    void create_shouldSaveAndReturnItem() {
        when(itemDtoMapper.dtoToModel(request)).thenReturn(item);
        when(itemRepository.save(item)).thenReturn(item);
        when(itemDtoMapper.modelToDto(item)).thenReturn(response);

        ItemDtoResponse result = itemService.create(request);

        assertEquals(response, result);

        verify(itemDtoMapper).dtoToModel(request);
        verify(itemRepository).save(item);
        verify(itemDtoMapper).modelToDto(item);
    }

    // update

    @Test
    void update_shouldUpdateAndReturnItem() {
        ItemDtoRequest updateRequest = new ItemDtoRequest(
                "Updated laptop",
                new BigDecimal("1500.00")
        );

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);
        when(itemDtoMapper.modelToDto(item)).thenReturn(response);

        ItemDtoResponse result = itemService.update(1L, updateRequest);

        assertEquals(response, result);
        assertEquals("Updated laptop", item.getName());
        assertEquals(new BigDecimal("1500.00"), item.getPrice());

        verify(itemRepository).findById(1L);
        verify(itemRepository).save(item);
        verify(itemDtoMapper).modelToDto(item);
    }

    @Test
    void update_shouldThrowNotFoundExceptionWhenItemDoesNotExist() {
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        ItemDtoRequest updateRequest = new ItemDtoRequest(
                "Updated laptop",
                new BigDecimal("1500.00")
        );

        assertThrows(
                NotFoundException.class,
                () -> itemService.update(1L, updateRequest)
        );

        verify(itemRepository).findById(1L);
        verify(itemRepository, never()).save(any(Item.class));
        verifyNoInteractions(itemDtoMapper);
    }

    // deleteById

    @Test
    void deleteById_shouldDeleteItemWhenItemExists() {
        when(itemRepository.existsById(1L)).thenReturn(true);

        assertDoesNotThrow(() -> itemService.deleteById(1L));

        verify(itemRepository).existsById(1L);
        verify(itemRepository).deleteById(1L);
    }

    @Test
    void deleteById_shouldThrowNotFoundExceptionWhenItemDoesNotExist() {
        when(itemRepository.existsById(1L)).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () -> itemService.deleteById(1L)
        );

        verify(itemRepository).existsById(1L);
        verify(itemRepository, never()).deleteById(anyLong());
    }
}