package ru.yandex.practicum.telemetry.aggregator.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("aggregator.kafka")
public class AggregatorKafkaProperties {

    private final Map<String, String> consumer;

    private final Map<String, String> producer;

    private final Topics topics;

    private final Duration pollTimeout;

    public Properties getConsumerProperties() {
        return toProperties(consumer);
    }

    public Properties getProducerProperties() {
        return toProperties(producer);
    }

    private Properties toProperties(Map<String, String> source) {
        Properties properties = new Properties();
        properties.putAll(source);
        return properties;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Topics {
        private final String sensorEvents;
        private final String snapshots;
    }
}
