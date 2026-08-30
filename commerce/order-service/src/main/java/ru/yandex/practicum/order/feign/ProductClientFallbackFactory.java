package ru.yandex.practicum.order.feign;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.exception.RemoteBusinessException;

@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return productId -> {
            log.warn("Вызов product-service для товара с id={} завершился сбоем", productId, cause);
            if (isNotFound(cause)) {
                throw new OrderProcessingException("Товар с id=" + productId + " не найден", cause);
            }
            throw new ProductServiceUnavailableException(productId, cause);
        };
    }

    private boolean isNotFound(Throwable cause) {
        return cause instanceof RemoteBusinessException businessException && businessException.getStatus() == 404
                || cause instanceof FeignException feignException && feignException.status() == 404;
    }
}
