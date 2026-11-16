package com.inno.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

public interface BaseController<T, R, K> {

    ResponseEntity<Page<R>> findAll(String search, Pageable pageable);

    ResponseEntity<R> findById(K id);

    ResponseEntity<R> create(T createRequest, Jwt jwt);

    ResponseEntity<R> update(K id, T updateRequest);

    ResponseEntity<Void> deleteById(K id);
}