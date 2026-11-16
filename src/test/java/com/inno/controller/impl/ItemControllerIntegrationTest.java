
package com.inno.controller.impl;

import com.inno.repository.ItemRepository;
import com.inno.repository.model.Item;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.inno.controller.RestConstants.ITEMS_V1_API_PATH;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BY_ID = ITEMS_V1_API_PATH + "/{id}";
    private static final long NOT_EXISTING_ID = 999_999L;

    private static final String ITEM_NAME_1 = "Laptop";
    private static final String ITEM_NAME_2 = "Monitor";
    private static final String ITEM_NAME_3 = "Keyboard";

    private static final BigDecimal PRICE_1 = new BigDecimal("1200.00");
    private static final BigDecimal PRICE_2 = new BigDecimal("500.00");
    private static final BigDecimal PRICE_3 = new BigDecimal("100.00");

    @LocalServerPort
    private int port;

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        itemRepository.deleteAll();
    }

    // ---------- helpers ----------

    private Map<String, Object> itemBody(String name, BigDecimal price) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("price", price);
        return body;
    }

    private long createItemViaApi(String name, BigDecimal price) {
        return given()
                .contentType(ContentType.JSON)
                .body(itemBody(name, price))
                .when()
                .post(ITEMS_V1_API_PATH)
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    // ---------- POST /api/v1/items ----------

    @Nested
    @DisplayName("POST create")
    class Create {

        @Test
        @DisplayName("creates item and persists it to DB")
        void shouldCreateItem() {
            long id = given()
                    .contentType(ContentType.JSON)
                    .body(itemBody(ITEM_NAME_1, PRICE_1))
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .log().ifValidationFails()
                    .statusCode(201)
                    .body("id", notNullValue())
                    .body("name", equalTo(ITEM_NAME_1))
                    .body("price", equalTo(1200.0f))
                    .body("createdAt", notNullValue())
                    .body("updatedAt", notNullValue())
                    .extract()
                    .jsonPath()
                    .getLong("id");

            Item saved = itemRepository.findById(id).orElseThrow();

            assertEquals(ITEM_NAME_1, saved.getName());
            assertEquals(0, PRICE_1.compareTo(saved.getPrice()));
        }

        @Test
        @DisplayName("returns 400 when name is blank")
        void shouldReturn400WhenNameBlank() {
            given()
                    .contentType(ContentType.JSON)
                    .body(itemBody("", PRICE_1))
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, itemRepository.count());
        }

        @Test
        @DisplayName("returns 400 when name is missing")
        void shouldReturn400WhenNameMissing() {
            Map<String, Object> body = itemBody(ITEM_NAME_1, PRICE_1);
            body.remove("name");

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, itemRepository.count());
        }

        @Test
        @DisplayName("returns 400 when name exceeds 255 characters")
        void shouldReturn400WhenNameTooLong() {
            String name = "A".repeat(256);

            given()
                    .contentType(ContentType.JSON)
                    .body(itemBody(name, PRICE_1))
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, itemRepository.count());
        }

        @Test
        @DisplayName("returns 400 when price is missing")
        void shouldReturn400WhenPriceMissing() {
            Map<String, Object> body = itemBody(ITEM_NAME_1, PRICE_1);
            body.remove("price");

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, itemRepository.count());
        }

        @Test
        @DisplayName("returns 400 when price is zero")
        void shouldReturn400WhenPriceIsZero() {
            given()
                    .contentType(ContentType.JSON)
                    .body(itemBody(ITEM_NAME_1, BigDecimal.ZERO))
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, itemRepository.count());
        }

        @Test
        @DisplayName("returns 400 when price is negative")
        void shouldReturn400WhenPriceIsNegative() {
            given()
                    .contentType(ContentType.JSON)
                    .body(itemBody(ITEM_NAME_1, new BigDecimal("-10.00")))
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, itemRepository.count());
        }

        @Test
        @DisplayName("returns 400 when body is empty")
        void shouldReturn400WhenBodyEmpty() {
            given()
                    .contentType(ContentType.JSON)
                    .body("{}")
                    .when()
                    .post(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(400);

            assertEquals(0, itemRepository.count());
        }
    }

    // ---------- GET /api/v1/items/{id} ----------

    @Nested
    @DisplayName("GET findById")
    class FindById {

        @Test
        @DisplayName("returns item by id")
        void shouldReturnItem() {
            long id = createItemViaApi(ITEM_NAME_1, PRICE_1);

            given()
                    .when()
                    .get(BY_ID, id)
                    .then()
                    .statusCode(200)
                    .body("id", equalTo((int) id))
                    .body("name", equalTo(ITEM_NAME_1))
                    .body("price", equalTo(1200.0f))
                    .body("createdAt", notNullValue())
                    .body("updatedAt", notNullValue());
        }

        @Test
        @DisplayName("returns 404 when item does not exist")
        void shouldReturn404() {
            given()
                    .when()
                    .get(BY_ID, NOT_EXISTING_ID)
                    .then()
                    .statusCode(404);
        }
    }

    // ---------- GET /api/v1/items ----------

    @Nested
    @DisplayName("GET findAll")
    class FindAll {

        @Test
        @DisplayName("returns empty page when there are no items")
        void shouldReturnEmptyPage() {
            given()
                    .when()
                    .get(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", empty())
                    .body("totalElements", equalTo(0));
        }

        @Test
        @DisplayName("returns items sorted by name ASC by default")
        void shouldReturnSortedByNameByDefault() {
            createItemViaApi(ITEM_NAME_1, PRICE_1);
            createItemViaApi(ITEM_NAME_2, PRICE_2);
            createItemViaApi(ITEM_NAME_3, PRICE_3);

            given()
                    .when()
                    .get(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(3))
                    .body("content.name",
                            contains("Keyboard", "Laptop", "Monitor"));
        }

        @Test
        @DisplayName("supports pagination")
        void shouldPaginate() {
            createItemViaApi("Apple", PRICE_1);
            createItemViaApi("Banana", PRICE_2);
            createItemViaApi("Orange", PRICE_3);

            given()
                    .queryParam("page", 1)
                    .queryParam("size", 2)
                    .when()
                    .get(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", hasSize(1))
                    .body("content[0].name", equalTo("Orange"))
                    .body("totalElements", equalTo(3))
                    .body("totalPages", equalTo(2));
        }

        @Test
        @DisplayName("supports custom sorting")
        void shouldSortByCustomField() {
            createItemViaApi("Apple", PRICE_1);
            createItemViaApi("Banana", PRICE_2);
            createItemViaApi("Orange", PRICE_3);

            given()
                    .queryParam("sort", "price,desc")
                    .when()
                    .get(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content.price",
                            contains(1200.0f, 500.0f, 100.0f));
        }

        @Test
        @DisplayName("filters items by name")
        void shouldFilterByName() {
            createItemViaApi("Apple Laptop", PRICE_1);
            createItemViaApi("Samsung Monitor", PRICE_2);
            createItemViaApi("Apple Keyboard", PRICE_3);

            given()
                    .queryParam("name", "Apple")
                    .when()
                    .get(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", hasSize(2))
                    .body("content.name",
                            contains("Apple Keyboard", "Apple Laptop"))
                    .body("totalElements", equalTo(2));
        }

        @Test
        @DisplayName("returns empty page when search has no matches")
        void shouldReturnEmptyPageWhenNoMatches() {
            createItemViaApi("Laptop", PRICE_1);
            createItemViaApi("Monitor", PRICE_2);

            given()
                    .queryParam("name", "Tablet")
                    .when()
                    .get(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", empty())
                    .body("totalElements", equalTo(0));
        }

        @Test
        @DisplayName("ignores blank name search parameter")
        void shouldIgnoreBlankSearch() {
            createItemViaApi("Laptop", PRICE_1);
            createItemViaApi("Monitor", PRICE_2);

            given()
                    .queryParam("name", "   ")
                    .when()
                    .get(ITEMS_V1_API_PATH)
                    .then()
                    .statusCode(200)
                    .body("content", hasSize(2))
                    .body("totalElements", equalTo(2));
        }
    }

    // ---------- PUT /api/v1/items/{id} ----------

    @Nested
    @DisplayName("PUT update")
    class Update {

        @Test
        @DisplayName("updates item and persists changes to DB")
        void shouldUpdateItem() {
            long id = createItemViaApi(ITEM_NAME_1, PRICE_1);

            String newName = "Updated Laptop";
            BigDecimal newPrice = new BigDecimal("1500.00");

            given()
                    .contentType(ContentType.JSON)
                    .body(itemBody(newName, newPrice))
                    .when()
                    .put(BY_ID, id)
                    .then()
                    .statusCode(200)
                    .body("id", equalTo((int) id))
                    .body("name", equalTo(newName))
                    .body("price", equalTo(1500.0f));

            Item fromDb = itemRepository.findById(id).orElseThrow();

            assertEquals(newName, fromDb.getName());
            assertEquals(0, newPrice.compareTo(fromDb.getPrice()));
        }

        @Test
        @DisplayName("returns 404 when item does not exist")
        void shouldReturn404() {
            given()
                    .contentType(ContentType.JSON)
                    .body(itemBody("Updated Laptop", PRICE_1))
                    .when()
                    .put(BY_ID, NOT_EXISTING_ID)
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("returns 400 on invalid body and leaves DB unchanged")
        void shouldReturn400OnInvalidBody() {
            long id = createItemViaApi(ITEM_NAME_1, PRICE_1);

            given()
                    .contentType(ContentType.JSON)
                    .body(itemBody("", BigDecimal.ZERO))
                    .when()
                    .put(BY_ID, id)
                    .then()
                    .statusCode(400);

            Item fromDb = itemRepository.findById(id).orElseThrow();

            assertEquals(ITEM_NAME_1, fromDb.getName());
            assertEquals(0, PRICE_1.compareTo(fromDb.getPrice()));
        }
    }

    // ---------- DELETE /api/v1/items/{id} ----------

    @Nested
    @DisplayName("DELETE deleteById")
    class Delete {

        @Test
        @DisplayName("deletes item from DB")
        void shouldDeleteItem() {
            long id = createItemViaApi(ITEM_NAME_1, PRICE_1);

            given()
                    .when()
                    .delete(BY_ID, id)
                    .then()
                    .statusCode(204);

            assertFalse(itemRepository.existsById(id));
        }

        @Test
        @DisplayName("deletes only the requested item")
        void shouldDeleteOnlyRequestedItem() {
            long id1 = createItemViaApi(ITEM_NAME_1, PRICE_1);
            long id2 = createItemViaApi(ITEM_NAME_2, PRICE_2);

            given()
                    .when()
                    .delete(BY_ID, id1)
                    .then()
                    .statusCode(204);

            assertFalse(itemRepository.existsById(id1));
            assertTrue(itemRepository.existsById(id2));
        }

        @Test
        @DisplayName("returns 404 when item does not exist")
        void shouldReturn404() {
            given()
                    .when()
                    .delete(BY_ID, NOT_EXISTING_ID)
                    .then()
                    .statusCode(404);
        }
    }
}
