package ru.yandex.practicum.telemetry.aggregator.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

@Configuration
public class KafkaConfig {

    @Bean
    public Consumer<String, SensorEventAvro> sensorEventConsumer(AggregatorKafkaProperties properties) {
        return new KafkaConsumer<>(properties.getConsumerProperties());
    }

    @Bean
    public Producer<String, SpecificRecordBase> snapshotProducer(AggregatorKafkaProperties properties) {
        return new KafkaProducer<>(properties.getProducerProperties());
    }
}
