package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.analyzer.model.Condition;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionType;
import ru.yandex.practicum.telemetry.analyzer.model.Scenario;
import ru.yandex.practicum.telemetry.analyzer.repository.ScenarioRepository;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final HubRouterClient hubRouterClient;

    @Transactional(readOnly = true)
    public void analyze(SensorsSnapshotAvro snapshot) {
        scenarioRepository.findByHubId(snapshot.getHubId()).stream()
                .filter(scenario -> isTriggered(scenario, snapshot))
                .forEach(scenario -> {
                    log.info("В хабе [{}] сработал сценарий [{}]",
                            snapshot.getHubId(), scenario.getName());
                    hubRouterClient.sendActions(scenario, snapshot.getTimestamp());
                });
    }

    private boolean isTriggered(Scenario scenario, SensorsSnapshotAvro snapshot) {
        Map<String, SensorStateAvro> sensorsState = snapshot.getSensorsState();
        return !scenario.getConditions().isEmpty()
                && scenario.getConditions().entrySet().stream()
                .allMatch(entry -> checkCondition(
                        entry.getValue(), sensorsState.get(entry.getKey().getId())));
    }

    private boolean checkCondition(Condition condition, SensorStateAvro state) {
        if (state == null) {
            return false;
        }
        return extractValue(state.getData(), condition.getType())
                .map(value -> compare(value, condition))
                .orElse(false);
    }

    private Optional<Integer> extractValue(Object data, ConditionType type) {
        return switch (type) {
            case MOTION -> data instanceof MotionSensorAvro sensor
                    ? Optional.of(sensor.getMotion() ? 1 : 0)
                    : Optional.empty();
            case LUMINOSITY -> data instanceof LightSensorAvro sensor
                    ? Optional.of(sensor.getLuminosity())
                    : Optional.empty();
            case SWITCH -> data instanceof SwitchSensorAvro sensor
                    ? Optional.of(sensor.getState() ? 1 : 0)
                    : Optional.empty();
            case TEMPERATURE -> switch (data) {
                case TemperatureSensorAvro sensor -> Optional.of(sensor.getTemperatureC());
                case ClimateSensorAvro sensor -> Optional.of(sensor.getTemperatureC());
                default -> Optional.empty();
            };
            case CO2LEVEL -> data instanceof ClimateSensorAvro sensor
                    ? Optional.of(sensor.getCo2Level())
                    : Optional.empty();
            case HUMIDITY -> data instanceof ClimateSensorAvro sensor
                    ? Optional.of(sensor.getHumidity())
                    : Optional.empty();
        };
    }

    private boolean compare(int sensorValue, Condition condition) {
        Integer expected = condition.getValue();
        if (expected == null) {
            return false;
        }
        return switch (condition.getOperation()) {
            case EQUALS -> sensorValue == expected;
            case GREATER_THAN -> sensorValue > expected;
            case LOWER_THAN -> sensorValue < expected;
        };
    }
}
