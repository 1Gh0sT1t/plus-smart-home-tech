package ru.yandex.practicum.order.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.NotFoundException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderPersistenceService orderPersistenceService;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    @Override
    public OrderDto create(CreateOrderRequest request) {
        Map<Long, Integer> quantitiesByProduct = aggregateQuantities(request);
        Map<Long, ServiceCallResult<ProductDto>> productsById = new LinkedHashMap<>();
        List<ReserveRequest> completedReservations = new ArrayList<>();
        boolean degraded = false;

        try {
            for (Long productId : quantitiesByProduct.keySet()) {
                ServiceCallResult<ProductDto> productResult = getAvailableProduct(productId);
                productsById.put(productId, productResult);
                degraded |= productResult.degraded();
            }

            for (Map.Entry<Long, Integer> entry : quantitiesByProduct.entrySet()) {
                ReserveRequest reservation = new ReserveRequest(entry.getKey(), entry.getValue());
                ServiceCallResult<Void> reservationResult = reserve(reservation);
                degraded |= reservationResult.degraded();
                if (!reservationResult.degraded()) {
                    completedReservations.add(reservation);
                }
            }

            Order order = buildOrder(request, productsById, degraded);
            return orderPersistenceService.save(order);
        } catch (OrderProcessingException e) {
            releaseReservations(completedReservations);
            throw e;
        } catch (RuntimeException e) {
            releaseReservations(completedReservations);
            throw new OrderProcessingException("Не удалось завершить создание заказа", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getById(Long id) {
        return orderMapper.toDto(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getByEmail(String email) {
        return orderRepository.findAllByCustomerEmailOrderByCreatedAtDesc(email).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    private Map<Long, Integer> aggregateQuantities(CreateOrderRequest request) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        request.items().forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        return quantities;
    }

    private ServiceCallResult<ProductDto> getAvailableProduct(Long productId) {
        ProductDto product;
        try {
            product = productClient.getProductById(productId);
        } catch (ProductServiceUnavailableException e) {
            log.warn("Каталог недоступен, товар с id={} будет сохранён для ручной проверки", productId, e);
            return ServiceCallResult.degraded(placeholderProduct(productId));
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new OrderProcessingException("Товар с id=" + productId + " не найден", e);
            }
            if (isTechnicalFailure(e)) {
                log.warn("Каталог недоступен, товар с id={} будет сохранён для ручной проверки", productId, e);
                return ServiceCallResult.degraded(placeholderProduct(productId));
            }
            throw new OrderProcessingException("Не удалось получить данные товара с id=" + productId, e);
        }

        if (product == null) {
            throw new OrderProcessingException("Не удалось получить данные товара с id=" + productId);
        }
        if (!Boolean.TRUE.equals(product.active())) {
            throw new OrderProcessingException("Товар с id=" + productId + " снят с продажи");
        }
        return ServiceCallResult.success(product);
    }

    private ServiceCallResult<Void> reserve(ReserveRequest request) {
        try {
            ReserveResponse response = inventoryClient.reserve(request);
            if (response == null || !response.success()) {
                throw new OrderProcessingException(
                        "Недостаточно товара с id=" + request.productId() + " для оформления заказа"
                );
            }
            return ServiceCallResult.success(null);
        } catch (InventoryServiceUnavailableException e) {
            log.warn(
                    "Склад недоступен, резерв товара с id={} требует ручной проверки",
                    request.productId(),
                    e
            );
            return ServiceCallResult.degraded(null);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new OrderProcessingException(
                        "Складская запись для товара с id=" + request.productId() + " не найдена",
                        e
                );
            }
            if (e.status() == 409) {
                throw new OrderProcessingException(
                        "Недостаточно товара с id=" + request.productId() + " для оформления заказа",
                        e
                );
            }
            if (isTechnicalFailure(e)) {
                log.warn(
                        "Склад недоступен, резерв товара с id={} требует ручной проверки",
                        request.productId(),
                        e
                );
                return ServiceCallResult.degraded(null);
            }
            throw new OrderProcessingException(
                    "Не удалось зарезервировать товар с id=" + request.productId(),
                    e
            );
        }
    }

    private Order buildOrder(
            CreateOrderRequest request,
            Map<Long, ServiceCallResult<ProductDto>> productsById,
            boolean degraded
    ) {
        Order order = new Order(request.customerName(), request.customerEmail());
        request.items().forEach(item -> {
            ProductDto product = productsById.get(item.productId()).value();
            order.addItem(new OrderItem(
                    product.id(),
                    product.name(),
                    item.quantity(),
                    product.price()
            ));
        });
        if (degraded) {
            order.markPendingConfirmation();
        } else {
            order.confirm();
        }
        return order;
    }

    private ProductDto placeholderProduct(Long productId) {
        return new ProductDto(
                productId,
                "Товар #" + productId + " (ожидает проверки)",
                BigDecimal.ZERO,
                true
        );
    }

    private boolean isTechnicalFailure(FeignException exception) {
        return exception.status() < 0 || exception.status() >= 500;
    }

    private void releaseReservations(List<ReserveRequest> completedReservations) {
        for (int i = completedReservations.size() - 1; i >= 0; i--) {
            ReserveRequest reservation = completedReservations.get(i);
            try {
                inventoryClient.release(reservation);
            } catch (RuntimeException releaseError) {
                log.error(
                        "Не удалось снять резерв товара с id={} в количестве {}",
                        reservation.productId(),
                        reservation.quantity(),
                        releaseError
                );
            }
        }
    }

    private Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заказ с id=" + id + " не найден"));
    }
}
