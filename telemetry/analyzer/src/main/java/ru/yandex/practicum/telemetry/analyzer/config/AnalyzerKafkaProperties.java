package ru.yandex.practicum.telemetry.analyzer.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("analyzer.kafka")
public class AnalyzerKafkaProperties {

    private final Map<String, String> snapshotConsumer;

    private final Map<String, String> hubEventConsumer;

    private final Topics topics;

    private final Duration pollTimeout;

    public Properties getSnapshotConsumerProperties() {
        return toProperties(snapshotConsumer);
    }

    public Properties getHubEventConsumerProperties() {
        return toProperties(hubEventConsumer);
    }

    private Properties toProperties(Map<String, String> source) {
        Properties properties = new Properties();
        properties.putAll(source);
        return properties;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Topics {
        private final String snapshots;
        private final String hubEvents;
    }
}
