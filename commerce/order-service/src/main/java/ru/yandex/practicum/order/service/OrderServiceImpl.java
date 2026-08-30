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
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.feign.InventoryClient;
import ru.yandex.practicum.order.feign.ProductClient;
import ru.yandex.practicum.order.feign.ProductDto;
import ru.yandex.practicum.order.feign.ReserveRequest;
import ru.yandex.practicum.order.feign.ReserveResponse;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

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
        Map<Long, ProductDto> productsById = new LinkedHashMap<>();
        List<ReserveRequest> completedReservations = new ArrayList<>();

        try {
            for (Long productId : quantitiesByProduct.keySet()) {
                productsById.put(productId, getAvailableProduct(productId));
            }

            for (Map.Entry<Long, Integer> entry : quantitiesByProduct.entrySet()) {
                ReserveRequest reservation = new ReserveRequest(entry.getKey(), entry.getValue());
                reserve(reservation);
                completedReservations.add(reservation);
            }

            Order order = buildConfirmedOrder(request, productsById);
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

    private ProductDto getAvailableProduct(Long productId) {
        ProductDto product;
        try {
            product = productClient.getProductById(productId);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new OrderProcessingException("Товар с id=" + productId + " не найден", e);
            }
            throw new OrderProcessingException("Не удалось получить данные товара с id=" + productId, e);
        }

        if (product == null) {
            throw new OrderProcessingException("Не удалось получить данные товара с id=" + productId);
        }
        if (!Boolean.TRUE.equals(product.active())) {
            throw new OrderProcessingException("Товар с id=" + productId + " снят с продажи");
        }
        return product;
    }

    private void reserve(ReserveRequest request) {
        try {
            ReserveResponse response = inventoryClient.reserve(request);
            if (response == null || !response.success()) {
                throw new OrderProcessingException(
                        "Недостаточно товара с id=" + request.productId() + " для оформления заказа"
                );
            }
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
            throw new OrderProcessingException(
                    "Не удалось зарезервировать товар с id=" + request.productId(),
                    e
            );
        }
    }

    private Order buildConfirmedOrder(CreateOrderRequest request, Map<Long, ProductDto> productsById) {
        Order order = new Order(request.customerName(), request.customerEmail());
        request.items().forEach(item -> {
            ProductDto product = productsById.get(item.productId());
            order.addItem(new OrderItem(
                    product.id(),
                    product.name(),
                    item.quantity(),
                    product.price()
            ));
        });
        order.confirm();
        return order;
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
