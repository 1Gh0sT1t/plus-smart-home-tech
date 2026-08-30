package ru.yandex.practicum.order.service;

public record ServiceCallResult<T>(T value, boolean degraded) {

    public static <T> ServiceCallResult<T> success(T value) {
        return new ServiceCallResult<>(value, false);
    }

    public static <T> ServiceCallResult<T> degraded(T fallbackValue) {
        return new ServiceCallResult<>(fallbackValue, true);
    }
}
