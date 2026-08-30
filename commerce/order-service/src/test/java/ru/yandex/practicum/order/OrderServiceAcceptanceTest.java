package ru.yandex.practicum.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.feign.InventoryClient;
import ru.yandex.practicum.order.feign.ProductClient;
import ru.yandex.practicum.order.feign.ProductDto;
import ru.yandex.practicum.order.feign.ReserveResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("unchecked")
class OrderServiceAcceptanceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private InventoryClient inventoryClient;

    @BeforeEach
    void configureRemoteServices() {
        when(productClient.getProductById(1L)).thenReturn(new ProductDto(
                1L, "Acceptance Smart Lamp", new BigDecimal("3490.00"), true
        ));
        when(productClient.getProductById(2L)).thenReturn(new ProductDto(
                2L, "Acceptance Smart Plug", new BigDecimal("1290.00"), true
        ));
        when(inventoryClient.reserve(any())).thenReturn(new ReserveResponse(
                true, 10, "Товар успешно зарезервирован"
        ));
        when(inventoryClient.release(any())).thenReturn(new ReserveResponse(
                true, 10, "Резерв успешно снят"
        ));
    }

    @Test
    void shouldCreateOrderStoreProductSnapshotAndFindOrderByIdAndEmail() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Acceptance Buyer",
                "acceptance-buyer@example.com",
                List.of(
                        new OrderItemRequest(1L, 2),
                        new OrderItemRequest(2L, 1)
                )
        );

        MvcResult createResponse = postJson("/api/orders", request);

        assertThat(status(createResponse))
                .as("POST /api/orders должен создавать заказ и возвращать HTTP 201 Created")
                .isEqualTo(201);
        Map<String, Object> created = readMap(createResponse);
        Long orderId = asLong(created.get("id"));
        assertThat(orderId)
                .as("Созданный заказ должен содержать поле id")
                .isNotNull();
        assertThat(created.get("status"))
                .as("Заказ после успешного резервирования должен сохраняться в статусе CONFIRMED")
                .isEqualTo("CONFIRMED");
        assertThat(asDecimal(created.get("totalPrice")))
                .as("order-service должен рассчитывать totalPrice по данным product-service")
                .isEqualByComparingTo("8270.00");
        assertThat((List<?>) created.get("items"))
                .as("Заказ должен хранить позиции заказа")
                .hasSize(2)
                .anySatisfy(item -> assertThat((Map<String, Object>) item)
                        .as("Позиция заказа должна хранить снимок названия и цены из product-service")
                        .containsEntry("productName", "Acceptance Smart Lamp"));

        MvcResult byIdResponse = mvc.perform(get("/api/orders/{id}", orderId)).andReturn();

        assertThat(status(byIdResponse))
                .as("GET /api/orders/{id} должен возвращать созданный заказ")
                .isEqualTo(200);
        assertThat(readMap(byIdResponse).get("customerEmail"))
                .as("GET /api/orders/{id} должен вернуть заказ с ожидаемым email клиента")
                .isEqualTo("acceptance-buyer@example.com");

        MvcResult byEmailResponse = mvc.perform(get("/api/orders/by-email")
                .param("email", "acceptance-buyer@example.com"))
                .andReturn();

        assertThat(status(byEmailResponse))
                .as("GET /api/orders/by-email?email=... должен возвращать заказы клиента")
                .isEqualTo(200);
        assertThat(readList(byEmailResponse))
                .as("Поиск заказов по email должен вернуть созданный заказ")
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("customerEmail", "acceptance-buyer@example.com"));
    }

    @Test
    void shouldReturnBadRequestForInvalidOrderPayload() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "",
                "not-an-email",
                List.of()
        );

        MvcResult response = postJson("/api/orders", invalidRequest);

        assertThat(status(response))
                .as("POST /api/orders с невалидным телом запроса должен возвращать HTTP 400 Bad Request")
                .isEqualTo(400);
        assertThat(readMap(response))
                .as("Ответ ошибки должен содержать сообщение и детали валидации")
                .containsKeys("message", "validationErrors");
    }

    @Test
    void shouldReturnBadRequestForMalformedOrderRequests() throws Exception {
        MvcResult malformedJsonResponse = mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andReturn();
        MvcResult wrongIdTypeResponse = mvc.perform(get("/api/orders/not-a-number")).andReturn();

        assertThat(status(malformedJsonResponse)).isEqualTo(400);
        assertThat(status(wrongIdTypeResponse)).isEqualTo(400);
    }

    @Test
    void shouldReturnUnprocessableEntityForInactiveProduct() throws Exception {
        when(productClient.getProductById(3L)).thenReturn(new ProductDto(
                3L, "Archived Product", new BigDecimal("100.00"), false
        ));
        CreateOrderRequest request = new CreateOrderRequest(
                "Acceptance Buyer",
                "acceptance-error@example.com",
                List.of(new OrderItemRequest(3L, 1))
        );

        MvcResult response = postJson("/api/orders", request);

        assertThat(status(response)).isEqualTo(422);
        assertThat(readMap(response).get("message"))
                .asString()
                .contains("снят с продажи");
    }

    private MvcResult postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andReturn();
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private Map<String, Object> readMap(MvcResult result) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> readList(MvcResult result) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() {
        });
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static BigDecimal asDecimal(Object value) {
        return new BigDecimal(value.toString());
    }
}
