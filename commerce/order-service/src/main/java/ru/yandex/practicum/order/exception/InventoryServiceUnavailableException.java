package ru.yandex.practicum.order.exception;

public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(Long productId, String operation, Throwable cause) {
        super("Сервис склада недоступен при операции " + operation + " для товара с id=" + productId, cause);
    }
}
