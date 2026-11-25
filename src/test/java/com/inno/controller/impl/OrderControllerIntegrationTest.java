package com.inno.controller.impl;

import com.inno.repository.ItemRepository;
import com.inno.repository.OrderRepository;
import com.inno.repository.model.Item;
import com.inno.repository.model.Order;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.inno.controller.RestConstants.ORDERS_V1_API_PATH;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BY_ID = ORDERS_V1_API_PATH + "/{id}";
    private static final String MY_ORDERS = ORDERS_V1_API_PATH + "/my";

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long NOT_EXISTING_ID = 999_999L;

    private static final String USER_SERVICE_RESPONSE = """
            {
                "id": 1,
                "name": "Ivan",
                "surname": "Petrov",
                "birthDate": "1998-05-20",
                "email": "ivan@example.com",
                "active": true,
                "createdAt": "2026-01-01T10:00:00",
                "updatedAt": "2026-01-01T10:00:00"
            }
            """;

    /*
     * A small HTTP server replaces the external User Service.
     * UserServiceClient and CircuitBreaker remain real.
     */
    private static final AtomicReference<Integer> USER_SERVICE_STATUS =
            new AtomicReference<>(200);

    private static final AtomicInteger USER_SERVICE_REQUESTS =
            new AtomicInteger();

    private static final HttpServer USER_SERVICE_SERVER = startUserServiceServer();
    @LocalServerPort
    private int port;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private long itemId;

    private static HttpServer startUserServiceServer() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("localhost", 0),
                    0
            );

            server.createContext("/", exchange -> {
                USER_SERVICE_REQUESTS.incrementAndGet();

                int status = USER_SERVICE_STATUS.get();
                byte[] response = (
                        status == 200
                                ? USER_SERVICE_RESPONSE
                                : "{\"message\":\"User Service unavailable\"}"
                ).getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type",
                        "application/json"
                );

                exchange.sendResponseHeaders(status, response.length);

                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });

            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void configureUserService(DynamicPropertyRegistry registry) {
        registry.add(
                "services.user-service.url",
                () -> "http://localhost:" + USER_SERVICE_SERVER.getAddress().getPort()
        );
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        orderRepository.deleteAll();
        itemRepository.deleteAll();

        USER_SERVICE_STATUS.set(200);
        USER_SERVICE_REQUESTS.set(0);

        circuitBreakerRegistry
                .circuitBreaker("userService")
                .reset();

        itemId = createItemInDb("Test item", new BigDecimal("10.00"));
    }

    // ---------- helpers ----------

    private long createItemInDb(String name, BigDecimal price) {
        Item item = new Item();
        item.setName(name);
        item.setPrice(price);

        return itemRepository.save(item).getId();
    }

    private Map<String, Object> orderItemBody(long id, int quantity) {
        Map<String, Object> item = new HashMap<>();
        item.put("itemId", id);
        item.put("quantity", quantity);
        return item;
    }

    private Map<String, Object> orderBody(
            UUID userId,
            long itemId,
            int quantity
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("orderItems", List.of(orderItemBody(itemId, quantity)));
        return body;
    }

    private long createOrderViaApi() {
        return given()
                .contentType(ContentType.JSON)
                .body(orderBody(TEST_USER_ID, itemId, 2))
                .when()
                .post(ORDERS_V1_API_PATH)
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    // ---------- POST /api/v1/orders ----------

    @Nested
    @DisplayName("POST create")
    class Create {

        @Test
        @DisplayName("creates order and returns user information")
        void shouldCreateOrder() {
            long id = given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, itemId, 2))
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .log().ifValidationFails()
                    .statusCode(201)
                    .body("id", notNullValue())
                    .body("status", equalTo("NEW"))
                    .body("totalPrice", equalTo(20.00f))
                    .body("orderItems", hasSize(1))
                    .body("user.id", equalTo(TEST_USER_ID))
                    .body("user.email", equalTo("ivan@example.com"))
                    .extract()
                    .jsonPath()
                    .getLong("id");

            Order saved = orderRepository.findById(id).orElseThrow();

            assertEquals(TEST_USER_ID, saved.getUserId());
            assertEquals(new BigDecimal("20.00"), saved.getTotalPrice());
            assertFalse(saved.isDeleted());
        }

        @Test
        @DisplayName("returns 400 when orderItems is empty")
        void shouldReturn400WhenOrderItemsEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", TEST_USER_ID);
            body.put("orderItems", List.of());

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, orderRepository.count());
        }

        @Test
        @DisplayName("returns 400 when orderItems is missing")
        void shouldReturn400WhenOrderItemsMissing() {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", TEST_USER_ID);

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, orderRepository.count());
        }

        @Test
        @DisplayName("returns 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() {
            Map<String, Object> body = orderBody(
                    TEST_USER_ID,
                    itemId,
                    2
            );
            body.remove("userId");

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, orderRepository.count());
        }

        @Test
        @DisplayName("returns 400 when itemId is invalid")
        void shouldReturn400WhenItemIdInvalid() {
            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, 0, 2))
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, orderRepository.count());
        }

        @Test
        @DisplayName("returns 400 when quantity is zero")
        void shouldReturn400WhenQuantityIsZero() {
            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, itemId, 0))
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, orderRepository.count());
        }

        @Test
        @DisplayName("returns 404 when item does not exist")
        void shouldReturn404WhenItemDoesNotExist() {
            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, NOT_EXISTING_ID, 2))
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(404);

            assertEquals(0, orderRepository.count());
        }
    }

    // ---------- GET /api/v1/orders/{id} ----------

    @Nested
    @DisplayName("GET findById")
    class FindById {

        @Test
        @DisplayName("returns order with user information")
        void shouldReturnOrder() {
            long id = createOrderViaApi();

            given()
                    .when()
                    .get(BY_ID, id)
                    .then()
                    .statusCode(200)
                    .body("id", equalTo((int) id))
                    .body("status", equalTo("NEW"))
                    .body("totalPrice", equalTo(20.00f))
                    .body("orderItems", hasSize(1))
                    .body("user.id", equalTo(TEST_USER_ID))
                    .body("user.email", equalTo("ivan@example.com"));
        }

        @Test
        @DisplayName("returns 404 when order does not exist")
        void shouldReturn404() {
            given()
                    .when()
                    .get(BY_ID, NOT_EXISTING_ID)
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("does not return soft-deleted order")
        void shouldNotReturnDeletedOrder() {
            long id = createOrderViaApi();

            given()
                    .when()
                    .delete(BY_ID, id)
                    .then()
                    .statusCode(204);

            given()
                    .when()
                    .get(BY_ID, id)
                    .then()
                    .statusCode(404);
        }
    }

    // ---------- GET /api/v1/orders ----------

    @Nested
    @DisplayName("GET findAll")
    class FindAll {

        @Test
        @DisplayName("returns empty page when there are no orders")
        void shouldReturnEmptyPage() {
            given()
                    .when()
                    .get(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", empty())
                    .body("totalElements", equalTo(0));
        }

        @Test
        @DisplayName("returns orders with user information")
        void shouldReturnOrdersWithUsers() {
            createOrderViaApi();
            createOrderViaApi();

            given()
                    .when()
                    .get(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2))
                    .body("content", hasSize(2))
                    .body("content[0].user.email", equalTo("ivan@example.com"))
                    .body("content[1].user.email", equalTo("ivan@example.com"));
        }

        @Test
        @DisplayName("supports pagination")
        void shouldPaginate() {
            createOrderViaApi();
            createOrderViaApi();
            createOrderViaApi();

            given()
                    .queryParam("page", 0)
                    .queryParam("size", 2)
                    .when()
                    .get(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", hasSize(2))
                    .body("totalElements", equalTo(3))
                    .body("totalPages", equalTo(2));
        }

        @Test
        @DisplayName("supports custom sorting")
        void shouldSortByCustomField() {
            createOrderViaApi();
            createOrderViaApi();

            given()
                    .queryParam("sort", "createdAt,asc")
                    .when()
                    .get(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", hasSize(2));
        }

        @Test
        @DisplayName("does not include soft-deleted orders")
        void shouldExcludeDeletedOrders() {
            long id = createOrderViaApi();
            createOrderViaApi();

            given()
                    .when()
                    .delete(BY_ID, id)
                    .then()
                    .statusCode(204);

            given()
                    .when()
                    .get(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1));
        }

        @Test
        @DisplayName("filters orders by status")
        void shouldFilterByStatus() {
            createOrderViaApi();

            given()
                    .queryParam("status", "NEW")
                    .when()
                    .get(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", hasSize(1));
        }

        @Test
        @DisplayName("ignores blank status search")
        void shouldIgnoreBlankStatusSearch() {
            createOrderViaApi();
            createOrderViaApi();

            given()
                    .queryParam("status", "   ")
                    .when()
                    .get(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", hasSize(2));
        }
    }

    // ---------- GET /api/v1/orders/my ----------

    @Nested
    @DisplayName("GET findCurrentUserOrders")
    class FindCurrentUserOrders {

        @Test
        @DisplayName("returns current user's orders with user information")
        void shouldReturnCurrentUserOrders() {
            createOrderViaApi();
            createOrderViaApi();

            given()
                    .when()
                    .get(MY_ORDERS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2))
                    .body("content", hasSize(2))
                    .body("content[0].user.id", equalTo(TEST_USER_ID));
        }

        @Test
        @DisplayName("returns empty page when current user has no orders")
        void shouldReturnEmptyPage() {
            given()
                    .when()
                    .get(MY_ORDERS)
                    .then()
                    .statusCode(200)
                    .body("content", empty())
                    .body("totalElements", equalTo(0));
        }

        @Test
        @DisplayName("does not return soft-deleted orders")
        void shouldExcludeDeletedOrders() {
            long id = createOrderViaApi();

            given()
                    .when()
                    .delete(BY_ID, id)
                    .then()
                    .statusCode(204);

            given()
                    .when()
                    .get(MY_ORDERS)
                    .then()
                    .statusCode(200)
                    .body("content", empty())
                    .body("totalElements", equalTo(0));
        }
    }

    // ---------- PUT /api/v1/orders/{id} ----------

    @Nested
    @DisplayName("PUT update")
    class Update {

        @Test
        @DisplayName("updates order items and recalculates total price")
        void shouldUpdateOrder() {
            long id = createOrderViaApi();

            long secondItemId = createItemInDb(
                    "Second item",
                    new BigDecimal("15.00")
            );

            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, secondItemId, 3))
                    .when()
                    .put(BY_ID, id)
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .body("id", equalTo((int) id))
                    .body("totalPrice", equalTo(45.00f))
                    .body("orderItems", hasSize(1))
                    .body("user.id", equalTo(TEST_USER_ID));

            Order updated = orderRepository.findById(id).orElseThrow();

            assertEquals(
                    0,
                    updated.getTotalPrice().compareTo(new BigDecimal("45.00"))
            );
        }

        @Test
        @DisplayName("returns 404 when order does not exist")
        void shouldReturn404() {
            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, itemId, 2))
                    .when()
                    .put(BY_ID, NOT_EXISTING_ID)
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("returns 400 when request contains invalid quantity")
        void shouldReturn400WhenQuantityInvalid() {
            long id = createOrderViaApi();

            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, itemId, 0))
                    .when()
                    .put(BY_ID, id)
                    .then()
                    .statusCode(400);

            Order unchanged = orderRepository.findById(id).orElseThrow();

            assertEquals(
                    0,
                    unchanged.getTotalPrice().compareTo(new BigDecimal("20.00"))
            );
        }

        @Test
        @DisplayName("returns 400 when orderItems is empty")
        void shouldReturn400WhenOrderItemsEmpty() {
            long id = createOrderViaApi();

            Map<String, Object> body = new HashMap<>();
            body.put("userId", TEST_USER_ID);
            body.put("orderItems", List.of());

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .put(BY_ID, id)
                    .then()
                    .statusCode(400);
        }
    }

    // ---------- DELETE /api/v1/orders/{id} ----------

    @Nested
    @DisplayName("DELETE deleteById")
    class Delete {

        @Test
        @DisplayName("soft-deletes order")
        void shouldSoftDeleteOrder() {
            long id = createOrderViaApi();

            given()
                    .when()
                    .delete(BY_ID, id)
                    .then()
                    .statusCode(204);

            Order deleted = orderRepository.findById(id).orElseThrow();

            assertTrue(deleted.isDeleted());
        }

        @Test
        @DisplayName("does not physically delete order from database")
        void shouldKeepOrderInDatabase() {
            long id = createOrderViaApi();

            given()
                    .when()
                    .delete(BY_ID, id)
                    .then()
                    .statusCode(204);

            assertTrue(orderRepository.existsById(id));
        }

        @Test
        @DisplayName("returns 404 when order does not exist")
        void shouldReturn404() {
            given()
                    .when()
                    .delete(BY_ID, NOT_EXISTING_ID)
                    .then()
                    .statusCode(404);
        }
    }

    // ---------- Circuit Breaker ----------

    @Nested
    @DisplayName("Circuit Breaker: User Service")
    class CircuitBreakerTests {

        @Test
        @DisplayName("returns 503 when User Service is unavailable")
        void shouldReturn503WhenUserServiceUnavailable() {
            USER_SERVICE_STATUS.set(500);

            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, itemId, 2))
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(503);

            assertEquals(1, USER_SERVICE_REQUESTS.get());
            assertEquals(0, orderRepository.count());
        }

        @Test
        @DisplayName("opens circuit after reaching failure rate threshold")
        void shouldOpenCircuitAfterFailures() {
            USER_SERVICE_STATUS.set(500);

            /*
             * Configuration:
             * minimumNumberOfCalls = 5
             * slidingWindowSize = 10
             * failureRateThreshold = 50
             *
             * Five failed calls produce a 100% failure rate.
             */
            for (int i = 0; i < 5; i++) {
                given()
                        .contentType(ContentType.JSON)
                        .body(orderBody(TEST_USER_ID, itemId, 2))
                        .when()
                        .post(ORDERS_V1_API_PATH)
                        .then()
                        .statusCode(503);
            }

            assertEquals(5, USER_SERVICE_REQUESTS.get());

            assertEquals(
                    io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN,
                    circuitBreakerRegistry
                            .circuitBreaker("userService")
                            .getState()
            );
        }

        @Test
        @DisplayName("does not call User Service when circuit is open")
        void shouldNotCallUserServiceWhenCircuitIsOpen() {
            USER_SERVICE_STATUS.set(500);

            for (int i = 0; i < 5; i++) {
                given()
                        .contentType(ContentType.JSON)
                        .body(orderBody(TEST_USER_ID, itemId, 2))
                        .when()
                        .post(ORDERS_V1_API_PATH)
                        .then()
                        .statusCode(503);
            }

            assertEquals(5, USER_SERVICE_REQUESTS.get());

            // The sixth request should fail fast without an HTTP call.
            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, itemId, 2))
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(503);

            assertEquals(5, USER_SERVICE_REQUESTS.get());
        }

        @Test
        @DisplayName("calls User Service again after circuit is reset")
        void shouldCallUserServiceAfterCircuitReset() {
            USER_SERVICE_STATUS.set(500);

            for (int i = 0; i < 5; i++) {
                given()
                        .contentType(ContentType.JSON)
                        .body(orderBody(TEST_USER_ID, itemId, 2))
                        .when()
                        .post(ORDERS_V1_API_PATH)
                        .then()
                        .statusCode(503);
            }

            circuitBreakerRegistry
                    .circuitBreaker("userService")
                    .reset();

            USER_SERVICE_STATUS.set(200);

            given()
                    .contentType(ContentType.JSON)
                    .body(orderBody(TEST_USER_ID, itemId, 2))
                    .when()
                    .post(ORDERS_V1_API_PATH)
                    .then()
                    .statusCode(201);

            assertEquals(6, USER_SERVICE_REQUESTS.get());
        }
    }
}
