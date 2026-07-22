package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.analyzer.config.AnalyzerKafkaProperties;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {

    private final Consumer<String, HubEventAvro> hubEventConsumer;
    private final AnalyzerKafkaProperties properties;
    private final HubEventService hubEventService;

    @Override
    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread(hubEventConsumer::wakeup));

        try {
            String topic = properties.getTopics().getHubEvents();
            hubEventConsumer.subscribe(List.of(topic));
            log.info("Обработчик событий хабов подписан на топик [{}]", topic);

            while (true) {
                ConsumerRecords<String, HubEventAvro> records =
                        hubEventConsumer.poll(properties.getPollTimeout());

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    try {
                        hubEventService.handle(record.value());
                    } catch (Exception e) {
                        log.error("Не удалось обработать событие хаба [{}]", record.key(), e);
                    }
                }
            }

        } catch (WakeupException ignored) {
            // игнорируем - закрываем консьюмер в блоке finally
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий хабов", e);
        } finally {
            log.info("Закрываем консьюмер событий хабов");
            hubEventConsumer.close();
        }
    }
}
