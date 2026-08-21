package ru.yandex.practicum.inventory.exception;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
