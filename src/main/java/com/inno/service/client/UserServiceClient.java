package com.inno.service.client;

import com.inno.service.dto.UserDtoResponse;
import com.inno.service.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import static com.inno.service.exception.ExceptionErrorCodes.USER_SERVICE_UNAVAILABLE_EXCEPTION;

@Service
public class UserServiceClient {

    private final RestClient userServiceRestClient;

    @Autowired
    public UserServiceClient(RestClient userServiceRestClient) {
        this.userServiceRestClient = userServiceRestClient;
    }

    @CircuitBreaker(
            name = "userService",
            fallbackMethod = "getUserByEmailFallback"
    )
    public UserDtoResponse getUserByEmail(String email) {
        return userServiceRestClient.get()
                .uri("/api/v1/users/email/{email}", email)
                .retrieve()
                .body(UserDtoResponse.class);
    }

    @CircuitBreaker(
            name = "userService",
            fallbackMethod = "getUserByIdFallback"
    )
    public UserDtoResponse getUserById(Long id) {
        return userServiceRestClient.get()
                .uri("/api/v1/users/{id}", id)
                .retrieve()
                .body(UserDtoResponse.class);
    }

    private UserDtoResponse getUserByEmailFallback(
            String email,
            Throwable exception
    ) {
        throw new UserServiceUnavailableException(
                USER_SERVICE_UNAVAILABLE_EXCEPTION.getErrorMessage(),
                exception
        );
    }

    private UserDtoResponse getUserByIdFallback(
            Long id,
            Throwable exception
    ) {
        throw new UserServiceUnavailableException(
                USER_SERVICE_UNAVAILABLE_EXCEPTION.getErrorMessage(),
                exception
        );
    }
}
