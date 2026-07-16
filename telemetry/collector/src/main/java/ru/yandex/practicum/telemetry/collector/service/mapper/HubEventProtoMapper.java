package ru.yandex.practicum.telemetry.collector.service.mapper;

import com.google.protobuf.Timestamp;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceAddedEventProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.ScenarioAddedEventProto;
import ru.yandex.practicum.grpc.telemetry.event.ScenarioConditionProto;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceActionAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HubEventProtoMapper {

    public static HubEventAvro toAvro(HubEventProto event) {
        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(toInstant(event))
                .setPayload(toPayload(event))
                .build();
    }

    public static Instant toInstant(HubEventProto event) {
        if (!event.hasTimestamp()) {
            return Instant.now();
        }
        Timestamp ts = event.getTimestamp();
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }

    private static Object toPayload(HubEventProto event) {
        return switch (event.getPayloadCase()) {
            case DEVICE_ADDED -> {
                DeviceAddedEventProto p = event.getDeviceAdded();
                yield DeviceAddedEventAvro.newBuilder()
                        .setId(p.getId())
                        .setType(DeviceTypeAvro.valueOf(p.getType().name()))
                        .build();
            }
            case DEVICE_REMOVED -> DeviceRemovedEventAvro.newBuilder()
                    .setId(event.getDeviceRemoved().getId())
                    .build();
            case SCENARIO_ADDED -> {
                ScenarioAddedEventProto p = event.getScenarioAdded();
                yield ScenarioAddedEventAvro.newBuilder()
                        .setName(p.getName())
                        .setConditions(p.getConditionList().stream()
                                .map(HubEventProtoMapper::toConditionAvro)
                                .toList())
                        .setActions(p.getActionList().stream()
                                .map(HubEventProtoMapper::toActionAvro)
                                .toList())
                        .build();
            }
            case SCENARIO_REMOVED -> ScenarioRemovedEventAvro.newBuilder()
                    .setName(event.getScenarioRemoved().getName())
                    .build();
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException(
                    "Не задан payload события хаба: " + event.getHubId());
        };
    }

    private static ScenarioConditionAvro toConditionAvro(ScenarioConditionProto condition) {
        Object value = switch (condition.getValueCase()) {
            case BOOL_VALUE -> condition.getBoolValue();
            case INT_VALUE -> condition.getIntValue();
            case VALUE_NOT_SET -> null;
        };
        return ScenarioConditionAvro.newBuilder()
                .setSensorId(condition.getSensorId())
                .setType(ConditionTypeAvro.valueOf(condition.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(condition.getOperation().name()))
                .setValue(value)
                .build();
    }

    private static DeviceActionAvro toActionAvro(DeviceActionProto action) {
        return DeviceActionAvro.newBuilder()
                .setSensorId(action.getSensorId())
                .setType(ActionTypeAvro.valueOf(action.getType().name()))
                .setValue(action.hasValue() ? action.getValue() : null)
                .build();
    }
}
