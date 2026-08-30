package ru.yandex.practicum.order.exception;

public class RemoteBusinessException extends RuntimeException {

    private final int status;

    public RemoteBusinessException(int status) {
        super("Соседний сервис вернул бизнес-отказ с HTTP-статусом " + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
