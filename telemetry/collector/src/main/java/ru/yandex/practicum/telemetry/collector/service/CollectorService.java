package ru.yandex.practicum.telemetry.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.config.CollectorKafkaProperties;
import ru.yandex.practicum.telemetry.collector.kafka.KafkaEventProducer;
import ru.yandex.practicum.telemetry.collector.model.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.service.mapper.HubEventMapper;
import ru.yandex.practicum.telemetry.collector.service.mapper.HubEventProtoMapper;
import ru.yandex.practicum.telemetry.collector.service.mapper.SensorEventMapper;
import ru.yandex.practicum.telemetry.collector.service.mapper.SensorEventProtoMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

    private final KafkaEventProducer producer;
    private final CollectorKafkaProperties properties;

    public void collectSensorEvent(SensorEvent event) {
        SensorEventAvro avroEvent = SensorEventMapper.toAvro(event);
        producer.send(properties.getTopics().getSensorEvents(),
                event.getHubId(), event.getTimestamp(), avroEvent);
    }

    public void collectHubEvent(HubEvent event) {
        HubEventAvro avroEvent = HubEventMapper.toAvro(event);
        producer.send(properties.getTopics().getHubEvents(),
                event.getHubId(), event.getTimestamp(), avroEvent);
    }

    public void collectSensorEvent(SensorEventProto event) {
        SensorEventAvro avroEvent = SensorEventProtoMapper.toAvro(event);
        producer.send(properties.getTopics().getSensorEvents(),
                avroEvent.getHubId(), avroEvent.getTimestamp(), avroEvent);
    }

    public void collectHubEvent(HubEventProto event) {
        HubEventAvro avroEvent = HubEventProtoMapper.toAvro(event);
        producer.send(properties.getTopics().getHubEvents(),
                avroEvent.getHubId(), avroEvent.getTimestamp(), avroEvent);
    }
}
