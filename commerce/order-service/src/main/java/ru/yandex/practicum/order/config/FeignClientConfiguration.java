package ru.yandex.practicum.order.config;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.order.exception.RemoteBusinessException;

@Configuration
public class FeignClientConfiguration {

    @Bean
    public RequestInterceptor sourceServiceHeaderInterceptor() {
        return template -> template.header("X-Source-Service", "order-service");
    }

    @Bean
    public ErrorDecoder businessErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            if (response.status() == 404 || response.status() == 409) {
                return new RemoteBusinessException(response.status());
            }
            return defaultDecoder.decode(methodKey, response);
        };
    }
}
