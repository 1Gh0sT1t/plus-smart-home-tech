package ru.yandex.practicum.telemetry.aggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.AggregatorKafkaProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {

    private static final int COMMIT_EVERY_RECORDS = 10;

    private final Consumer<String, SensorEventAvro> consumer;
    private final Producer<String, SpecificRecordBase> producer;
    private final AggregatorKafkaProperties properties;
    private final SnapshotAggregator aggregator;

    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));

        try {
            String sensorEventsTopic = properties.getTopics().getSensorEvents();
            consumer.subscribe(List.of(sensorEventsTopic));
            log.info("Агрегатор подписан на топик [{}]", sensorEventsTopic);

            while (true) {
                ConsumerRecords<String, SensorEventAvro> records =
                        consumer.poll(properties.getPollTimeout());

                int processed = 0;
                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    handleRecord(record.value());
                    manageOffsets(record, ++processed);
                }

                if (processed > 0) {
                    consumer.commitAsync();
                }
            }

        } catch (WakeupException ignored) {
            // игнорируем - закрываем консьюмер и продюсер в блоке finally
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий от датчиков", e);
        } finally {
            try {
                producer.flush();
                consumer.commitSync(currentOffsets);
            } finally {
                log.info("Закрываем консьюмер");
                consumer.close();
                log.info("Закрываем продюсер");
                producer.close();
            }
        }
    }

    private void handleRecord(SensorEventAvro event) {
        Optional<SensorsSnapshotAvro> updatedSnapshot = aggregator.updateState(event);
        updatedSnapshot.ifPresent(this::sendSnapshot);
    }

    private void sendSnapshot(SensorsSnapshotAvro snapshot) {
        ProducerRecord<String, SpecificRecordBase> record = new ProducerRecord<>(
                properties.getTopics().getSnapshots(),
                null,
                snapshot.getTimestamp().toEpochMilli(),
                snapshot.getHubId(),
                snapshot);

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Не удалось отправить снапшот хаба [{}]", snapshot.getHubId(), exception);
            } else {
                log.debug("Снапшот хаба [{}] отправлен, partition={}, offset={}",
                        snapshot.getHubId(), metadata.partition(), metadata.offset());
            }
        });
    }

    private void manageOffsets(ConsumerRecord<String, SensorEventAvro> record, int processed) {
        currentOffsets.put(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1));

        if (processed % COMMIT_EVERY_RECORDS == 0) {
            consumer.commitAsync(currentOffsets, (offsets, exception) -> {
                if (exception != null) {
                    log.warn("Не удалось зафиксировать смещения: {}", offsets, exception);
                }
            });
        }
    }
}
