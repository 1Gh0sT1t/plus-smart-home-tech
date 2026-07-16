package ru.yandex.practicum.telemetry.collector.service.mapper;

import com.google.protobuf.Timestamp;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.yandex.practicum.grpc.telemetry.event.ClimateSensorProto;
import ru.yandex.practicum.grpc.telemetry.event.LightSensorProto;
import ru.yandex.practicum.grpc.telemetry.event.MotionSensorProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SwitchSensorProto;
import ru.yandex.practicum.grpc.telemetry.event.TemperatureSensorProto;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SensorEventProtoMapper {

    public static SensorEventAvro toAvro(SensorEventProto event) {
        Instant timestamp = toInstant(event);
        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(timestamp)
                .setPayload(toPayload(event, timestamp))
                .build();
    }

    private static Object toPayload(SensorEventProto event, Instant timestamp) {
        return switch (event.getPayloadCase()) {
            case MOTION_SENSOR -> {
                MotionSensorProto p = event.getMotionSensor();
                yield MotionSensorAvro.newBuilder()
                        .setLinkQuality(p.getLinkQuality())
                        .setMotion(p.getMotion())
                        .setVoltage(p.getVoltage())
                        .build();
            }
            case TEMPERATURE_SENSOR -> {
                TemperatureSensorProto p = event.getTemperatureSensor();
                yield TemperatureSensorAvro.newBuilder()
                        .setId(event.getId())
                        .setHubId(event.getHubId())
                        .setTimestamp(timestamp)
                        .setTemperatureC(p.getTemperatureC())
                        .setTemperatureF(p.getTemperatureF())
                        .build();
            }
            case LIGHT_SENSOR -> {
                LightSensorProto p = event.getLightSensor();
                yield LightSensorAvro.newBuilder()
                        .setLinkQuality(p.getLinkQuality())
                        .setLuminosity(p.getLuminosity())
                        .build();
            }
            case CLIMATE_SENSOR -> {
                ClimateSensorProto p = event.getClimateSensor();
                yield ClimateSensorAvro.newBuilder()
                        .setTemperatureC(p.getTemperatureC())
                        .setHumidity(p.getHumidity())
                        .setCo2Level(p.getCo2Level())
                        .build();
            }
            case SWITCH_SENSOR -> {
                SwitchSensorProto p = event.getSwitchSensor();
                yield SwitchSensorAvro.newBuilder()
                        .setState(p.getState())
                        .build();
            }
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException(
                    "Не задан payload события датчика: " + event.getId());
        };
    }

    private static Instant toInstant(SensorEventProto event) {
        if (!event.hasTimestamp()) {
            return Instant.now();
        }
        Timestamp ts = event.getTimestamp();
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }
}
