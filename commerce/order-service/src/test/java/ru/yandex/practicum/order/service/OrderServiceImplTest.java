package ru.yandex.practicum.order.service;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.feign.InventoryClient;
import ru.yandex.practicum.order.feign.ProductClient;
import ru.yandex.practicum.order.feign.ProductDto;
import ru.yandex.practicum.order.feign.ReserveRequest;
import ru.yandex.practicum.order.feign.ReserveResponse;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void configureSuccessfulInventory() {
        lenient().when(inventoryClient.reserve(any())).thenReturn(successfulReservation());
        lenient().when(inventoryClient.release(any())).thenReturn(successfulReservation());
        lenient().when(orderPersistenceService.save(any())).thenAnswer(invocation ->
                new OrderMapper().toDto(invocation.getArgument(0, Order.class))
        );
    }

    @Test
    void shouldCreateConfirmedOrderFromRemoteProductData() {
        when(productClient.getProductById(1L)).thenReturn(product(1L, "Smart Lamp", "100.00", true));
        CreateOrderRequest request = request(List.of(new OrderItemRequest(1L, 2)));

        OrderDto result = orderService.create(request);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.totalPrice()).isEqualByComparingTo("200.00");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Smart Lamp");
            assertThat(item.price()).isEqualByComparingTo("100.00");
            assertThat(item.quantity()).isEqualTo(2);
        });
        verify(inventoryClient).reserve(new ReserveRequest(1L, 2));
        verify(orderPersistenceService).save(any(Order.class));
    }

    @Test
    void shouldReuseProductAndReserveAggregatedQuantity() {
        when(productClient.getProductById(1L)).thenReturn(product(1L, "Smart Lamp", "100.00", true));
        CreateOrderRequest request = request(List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(1L, 1)
        ));

        OrderDto result = orderService.create(request);

        verify(productClient).getProductById(1L);
        verify(inventoryClient).reserve(new ReserveRequest(1L, 3));
        assertThat(result.items()).hasSize(2);
        assertThat(result.totalPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void shouldRejectInactiveProductBeforeReservation() {
        when(productClient.getProductById(1L)).thenReturn(product(1L, "Archived Lamp", "100.00", false));

        assertThatThrownBy(() -> orderService.create(request(List.of(new OrderItemRequest(1L, 1)))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("снят с продажи");

        verifyNoInteractions(inventoryClient);
        verify(orderPersistenceService, never()).save(any());
    }

    @Test
    void shouldSavePendingOrderWithPlaceholderWhenProductServiceIsUnavailable() {
        when(productClient.getProductById(1L)).thenThrow(new ProductServiceUnavailableException(
                1L,
                new IllegalStateException("product-service unavailable")
        ));

        OrderDto result = orderService.create(request(List.of(new OrderItemRequest(1L, 2))));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(result.statusDetails()).contains("ручной проверки");
        assertThat(result.totalPrice()).isEqualByComparingTo("0.00");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(1L);
            assertThat(item.productName()).isEqualTo("Товар #1 (ожидает проверки)");
            assertThat(item.price()).isEqualByComparingTo("0.00");
        });
        verify(inventoryClient).reserve(new ReserveRequest(1L, 2));
        verify(orderPersistenceService).save(any(Order.class));
    }

    @Test
    void shouldSavePendingOrderWhenInventoryServiceIsUnavailable() {
        when(productClient.getProductById(1L)).thenReturn(product(1L, "Smart Lamp", "100.00", true));
        when(inventoryClient.reserve(new ReserveRequest(1L, 2))).thenThrow(
                new InventoryServiceUnavailableException(
                        1L,
                        "резервирования",
                        new IllegalStateException("inventory-service unavailable")
                )
        );

        OrderDto result = orderService.create(request(List.of(new OrderItemRequest(1L, 2))));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(result.statusDetails()).contains("ручной проверки");
        assertThat(result.totalPrice()).isEqualByComparingTo("200.00");
        verify(orderPersistenceService).save(any(Order.class));
        verify(inventoryClient, never()).release(any());
    }

    @Test
    void shouldTreatServerErrorAsTechnicalDegradation() {
        FeignException serviceUnavailable = feignException(503);
        when(productClient.getProductById(1L)).thenThrow(serviceUnavailable);

        OrderDto result = orderService.create(request(List.of(new OrderItemRequest(1L, 1))));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        verify(orderPersistenceService).save(any(Order.class));
    }

    @Test
    void shouldNotSaveOrderWhenReservationFails() {
        when(productClient.getProductById(1L)).thenReturn(product(1L, "Smart Lamp", "100.00", true));
        FeignException conflict = feignException(409);
        when(inventoryClient.reserve(new ReserveRequest(1L, 5))).thenThrow(conflict);

        assertThatThrownBy(() -> orderService.create(request(List.of(new OrderItemRequest(1L, 5)))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("Недостаточно товара");

        verify(orderPersistenceService, never()).save(any());
        verify(inventoryClient, never()).release(any());
    }

    @Test
    void shouldReleaseCompletedReservationWhenLaterReservationFails() {
        when(productClient.getProductById(1L)).thenReturn(product(1L, "Smart Lamp", "100.00", true));
        when(productClient.getProductById(2L)).thenReturn(product(2L, "Smart Plug", "50.00", true));
        FeignException conflict = feignException(409);
        when(inventoryClient.reserve(new ReserveRequest(2L, 1))).thenThrow(conflict);
        CreateOrderRequest request = request(List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 1)
        ));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("Недостаточно товара");

        verify(inventoryClient).release(new ReserveRequest(1L, 2));
        verify(inventoryClient, never()).release(new ReserveRequest(2L, 1));
        verify(orderPersistenceService, never()).save(any());
    }

    @Test
    void shouldReleaseReservationWhenSavingOrderFails() {
        when(productClient.getProductById(1L)).thenReturn(product(1L, "Smart Lamp", "100.00", true));
        doThrow(new IllegalStateException("database unavailable"))
                .when(orderPersistenceService).save(any());

        assertThatThrownBy(() -> orderService.create(request(List.of(new OrderItemRequest(1L, 2)))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessage("Не удалось завершить создание заказа");

        verify(inventoryClient).release(new ReserveRequest(1L, 2));
    }

    private static CreateOrderRequest request(List<OrderItemRequest> items) {
        return new CreateOrderRequest("Test Buyer", "buyer@example.com", items);
    }

    private static ProductDto product(Long id, String name, String price, boolean active) {
        return new ProductDto(id, name, new BigDecimal(price), active);
    }

    private static ReserveResponse successfulReservation() {
        return new ReserveResponse(true, 10, "success");
    }

    private static FeignException feignException(int status) {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(status);
        return exception;
    }
}
