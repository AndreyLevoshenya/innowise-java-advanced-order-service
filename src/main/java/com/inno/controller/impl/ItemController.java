package com.inno.controller.impl;

import com.inno.controller.BaseController;
import com.inno.service.ItemService;
import com.inno.service.dto.ItemDtoRequest;
import com.inno.service.dto.ItemDtoResponse;
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

import static com.inno.controller.RestConstants.ITEMS_V1_API_PATH;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping(value = ITEMS_V1_API_PATH)
public class ItemController implements BaseController<ItemDtoRequest, ItemDtoResponse, Long> {
    private final ItemService itemService;

    @Autowired
    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Page<ItemDtoResponse>> findAll(
            @RequestParam(name = "name", required = false) String search,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        SearchingRequest searchingRequest = null;
        if (search != null && !search.isBlank()) {
            searchingRequest = new SearchingRequest(search);
        }
        Page<ItemDtoResponse> pageDtoResponse = itemService.findAll(searchingRequest, pageable);
        return ResponseEntity.ok(pageDtoResponse);
    }

    @Override
    @GetMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ItemDtoResponse> findById(@PathVariable Long id) {
        ItemDtoResponse itemDtoResponse = itemService.findById(id);
        return ResponseEntity.ok(itemDtoResponse);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ItemDtoResponse> create(@Valid @RequestBody ItemDtoRequest createRequest, @AuthenticationPrincipal Jwt jwt) {
        ItemDtoResponse itemDtoResponse = itemService.create(createRequest);
        return ResponseEntity.status(CREATED).body(itemDtoResponse);
    }

    @Override
    @PutMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ItemDtoResponse> update(@PathVariable Long id, @Valid @RequestBody ItemDtoRequest updateRequest) {
        ItemDtoResponse itemDtoResponse = itemService.update(id, updateRequest);
        return ResponseEntity.ok(itemDtoResponse);
    }

    @Override
    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        itemService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
