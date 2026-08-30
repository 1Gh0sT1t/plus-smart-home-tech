package ru.yandex.practicum.order.feign;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.RemoteBusinessException;

@Slf4j
@Component
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        return new InventoryClient() {
            @Override
            public ReserveResponse reserve(ReserveRequest request) {
                log.warn(
                        "Вызов inventory-service для резервирования товара с id={} завершился сбоем",
                        request.productId(),
                        cause
                );
                throw classifyReserveFailure(request.productId(), cause);
            }

            @Override
            public ReserveResponse release(ReserveRequest request) {
                log.warn(
                        "Вызов inventory-service для снятия резерва товара с id={} завершился сбоем",
                        request.productId(),
                        cause
                );
                throw new InventoryServiceUnavailableException(request.productId(), "снятия резерва", cause);
            }
        };
    }

    private RuntimeException classifyReserveFailure(Long productId, Throwable cause) {
        if (cause instanceof RemoteBusinessException businessException) {
            return classifyBusinessFailure(productId, businessException.getStatus(), cause);
        }
        if (cause instanceof FeignException feignException) {
            return classifyBusinessFailure(productId, feignException.status(), cause);
        }
        return new InventoryServiceUnavailableException(productId, "резервирования", cause);
    }

    private RuntimeException classifyBusinessFailure(Long productId, int status, Throwable cause) {
        if (status == 404) {
            return new OrderProcessingException(
                    "Складская запись для товара с id=" + productId + " не найдена",
                    cause
            );
        }
        if (status == 409) {
            return new OrderProcessingException(
                    "Недостаточно товара с id=" + productId + " для оформления заказа",
                    cause
            );
        }
        return new InventoryServiceUnavailableException(productId, "резервирования", cause);
    }
}
