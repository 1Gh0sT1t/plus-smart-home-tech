package ru.yandex.practicum.telemetry.collector.service.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.collector.model.sensor.ClimateSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.LightSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.MotionSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SwitchSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.TemperatureSensorEvent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SensorEventMapper {

    public static SensorEventAvro toAvro(SensorEvent event) {
        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp())
                .setPayload(toPayload(event))
                .build();
    }

    private static Object toPayload(SensorEvent event) {
        return switch (event) {
            case ClimateSensorEvent e -> ClimateSensorAvro.newBuilder()
                    .setTemperatureC(e.getTemperatureC())
                    .setHumidity(e.getHumidity())
                    .setCo2Level(e.getCo2Level())
                    .build();
            case LightSensorEvent e -> LightSensorAvro.newBuilder()
                    .setLinkQuality(e.getLinkQuality())
                    .setLuminosity(e.getLuminosity())
                    .build();
            case MotionSensorEvent e -> MotionSensorAvro.newBuilder()
                    .setLinkQuality(e.getLinkQuality())
                    .setMotion(e.isMotion())
                    .setVoltage(e.getVoltage())
                    .build();
            case SwitchSensorEvent e -> SwitchSensorAvro.newBuilder()
                    .setState(e.isState())
                    .build();
            case TemperatureSensorEvent e -> TemperatureSensorAvro.newBuilder()
                    .setId(e.getId())
                    .setHubId(e.getHubId())
                    .setTimestamp(e.getTimestamp())
                    .setTemperatureC(e.getTemperatureC())
                    .setTemperatureF(e.getTemperatureF())
                    .build();
            default -> throw new IllegalArgumentException(
                    "Неизвестный тип события датчика: " + event.getType());
        };
    }
}
