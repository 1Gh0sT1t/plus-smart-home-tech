package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotProcessor {

    private final Consumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final AnalyzerKafkaProperties properties;
    private final ScenarioService scenarioService;

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(snapshotConsumer::wakeup));

        try {
            String topic = properties.getTopics().getSnapshots();
            snapshotConsumer.subscribe(List.of(topic));
            log.info("Обработчик снапшотов подписан на топик [{}]", topic);

            while (true) {
                ConsumerRecords<String, SensorsSnapshotAvro> records =
                        snapshotConsumer.poll(properties.getPollTimeout());

                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    try {
                        scenarioService.analyze(record.value());
                    } catch (Exception e) {
                        log.error("Не удалось обработать снапшот хаба [{}]", record.key(), e);
                    }
                    commitOffset(record);
                }
            }

        } catch (WakeupException ignored) {
            // игнорируем - закрываем консьюмер в блоке finally
        } catch (Exception e) {
            log.error("Ошибка во время обработки снапшотов", e);
        } finally {
            log.info("Закрываем консьюмер снапшотов");
            snapshotConsumer.close();
        }
    }

    private void commitOffset(ConsumerRecord<String, SensorsSnapshotAvro> record) {
        snapshotConsumer.commitSync(Map.of(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)));
    }
}
