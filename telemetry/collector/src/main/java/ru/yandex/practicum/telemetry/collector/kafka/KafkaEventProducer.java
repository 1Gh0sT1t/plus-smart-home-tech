package ru.yandex.practicum.telemetry.collector.kafka;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final Producer<String, SpecificRecordBase> producer;

    public void send(String topic, String key, Instant timestamp, SpecificRecordBase event) {
        ProducerRecord<String, SpecificRecordBase> record =
                new ProducerRecord<>(topic, null, timestamp.toEpochMilli(), key, event);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Не удалось отправить событие в топик [{}]: {}", topic, event, exception);
            } else {
                log.debug("Событие отправлено в топик [{}], partition={}, offset={}",
                        metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    @PreDestroy
    public void close() {
        log.info("Завершение работы продюсера: сброс буфера и закрытие");
        producer.flush();
        producer.close(Duration.ofSeconds(10));
    }
}
