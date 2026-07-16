package ru.yandex.practicum.telemetry.collector.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("collector.kafka")
public class CollectorKafkaProperties {

    private final String bootstrapServers;

    private final Topics topics;

    @Getter
    @RequiredArgsConstructor
    public static class Topics {
        private final String sensorEvents;
        private final String hubEvents;
    }
}
