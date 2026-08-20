package ru.yandex.practicum.order.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public OrderDto create(CreateOrderRequest request) {
        Order order = new Order(request.customerName(), request.customerEmail());
        request.items().forEach(item -> order.addItem(new OrderItem(
                item.productId(), item.productName(), item.quantity(), item.price()
        )));
        return orderMapper.toDto(orderRepository.save(order));
    }

    public OrderDto getById(Long id) {
        return orderMapper.toDto(findById(id));
    }

    public List<OrderDto> getAll() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    public List<OrderDto> getByEmail(String email) {
        return orderRepository.findAllByCustomerEmailOrderByCreatedAtDesc(email).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    private Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заказ с id=" + id + " не найден"));
    }
}
