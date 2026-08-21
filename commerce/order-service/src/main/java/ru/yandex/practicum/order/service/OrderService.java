package ru.yandex.practicum.order.service;

import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;

import java.util.List;

public interface OrderService {

    OrderDto create(CreateOrderRequest request);

    OrderDto getById(Long id);

    List<OrderDto> getAll();

    List<OrderDto> getByEmail(String email);
}
