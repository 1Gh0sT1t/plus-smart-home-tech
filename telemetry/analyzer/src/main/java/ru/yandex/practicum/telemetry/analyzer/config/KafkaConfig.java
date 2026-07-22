package ru.yandex.practicum.telemetry.analyzer.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

@Configuration
public class KafkaConfig {

    @Bean
    public Consumer<String, SensorsSnapshotAvro> snapshotConsumer(AnalyzerKafkaProperties properties) {
        return new KafkaConsumer<>(properties.getSnapshotConsumerProperties());
    }

    @Bean
    public Consumer<String, HubEventAvro> hubEventConsumer(AnalyzerKafkaProperties properties) {
        return new KafkaConsumer<>(properties.getHubEventConsumerProperties());
    }
}
