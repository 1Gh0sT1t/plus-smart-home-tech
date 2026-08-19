package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.kafka.telemetry.event.DeviceActionAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;
import ru.yandex.practicum.telemetry.analyzer.model.Action;
import ru.yandex.practicum.telemetry.analyzer.model.ActionType;
import ru.yandex.practicum.telemetry.analyzer.model.Condition;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionOperation;
import ru.yandex.practicum.telemetry.analyzer.model.ConditionType;
import ru.yandex.practicum.telemetry.analyzer.model.Scenario;
import ru.yandex.practicum.telemetry.analyzer.model.Sensor;
import ru.yandex.practicum.telemetry.analyzer.repository.ActionRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.ConditionRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.telemetry.analyzer.repository.SensorRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class HubEventService {

    private final SensorRepository sensorRepository;
    private final ScenarioRepository scenarioRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    public void handle(HubEventAvro event) {
        switch (event.getPayload()) {
            case DeviceAddedEventAvro payload -> addSensor(event.getHubId(), payload);
            case DeviceRemovedEventAvro payload -> removeSensor(event.getHubId(), payload);
            case ScenarioAddedEventAvro payload -> addScenario(event.getHubId(), payload);
            case ScenarioRemovedEventAvro payload -> removeScenario(event.getHubId(), payload);
            default -> throw new IllegalArgumentException(
                    "Неизвестный тип события хаба: " + event.getPayload());
        }
    }

    private void addSensor(String hubId, DeviceAddedEventAvro payload) {
        if (sensorRepository.existsById(payload.getId())) {
            log.debug("Датчик [{}] уже зарегистрирован, событие пропущено", payload.getId());
            return;
        }
        sensorRepository.save(new Sensor(payload.getId(), hubId));
        log.info("В хабе [{}] зарегистрирован датчик [{}]", hubId, payload.getId());
    }

    private void removeSensor(String hubId, DeviceRemovedEventAvro payload) {
        Optional<Sensor> sensor = sensorRepository.findByIdAndHubId(payload.getId(), hubId);
        if (sensor.isEmpty()) {
            log.debug("Датчик [{}] в хабе [{}] не найден, событие пропущено", payload.getId(), hubId);
            return;
        }

        scenarioRepository.findByHubId(hubId).stream()
                .filter(scenario -> scenario.getConditions().containsKey(sensor.get())
                        || scenario.getActions().containsKey(sensor.get()))
                .forEach(scenario -> {
                    log.info("Сценарий [{}] удалён вместе с датчиком [{}]",
                            scenario.getName(), payload.getId());
                    deleteScenario(scenario);
                });

        sensorRepository.delete(sensor.get());
        log.info("Из хаба [{}] удалён датчик [{}]", hubId, payload.getId());
    }

    private void addScenario(String hubId, ScenarioAddedEventAvro payload) {
        Set<String> sensorIds = new HashSet<>();
        payload.getConditions().forEach(condition -> sensorIds.add(condition.getSensorId()));
        payload.getActions().forEach(action -> sensorIds.add(action.getSensorId()));

        Map<String, Sensor> sensors = sensorRepository.findAllById(sensorIds).stream()
                .filter(sensor -> hubId.equals(sensor.getHubId()))
                .collect(Collectors.toMap(Sensor::getId, Function.identity()));

        if (sensorIds.isEmpty() || sensors.size() < sensorIds.size()) {
            log.warn("Сценарий [{}] пропущен: не все датчики зарегистрированы в хабе [{}]",
                    payload.getName(), hubId);
            return;
        }

        Map<Sensor, Condition> conditions = new HashMap<>();
        for (ScenarioConditionAvro condition : payload.getConditions()) {
            conditions.put(sensors.get(condition.getSensorId()), toCondition(condition));
        }

        Map<Sensor, Action> actions = new HashMap<>();
        for (DeviceActionAvro action : payload.getActions()) {
            actions.put(sensors.get(action.getSensorId()), toAction(action));
        }

        scenarioRepository.findByHubIdAndName(hubId, payload.getName()).ifPresent(this::deleteScenario);

        Scenario scenario = Scenario.builder()
                .hubId(hubId)
                .name(payload.getName())
                .conditions(conditions)
                .actions(actions)
                .build();
        scenarioRepository.save(scenario);
        log.info("В хабе [{}] сохранён сценарий [{}]", hubId, payload.getName());
    }

    private void removeScenario(String hubId, ScenarioRemovedEventAvro payload) {
        scenarioRepository.findByHubIdAndName(hubId, payload.getName())
                .ifPresentOrElse(
                        scenario -> {
                            deleteScenario(scenario);
                            log.info("Из хаба [{}] удалён сценарий [{}]", hubId, payload.getName());
                        },
                        () -> log.debug("Сценарий [{}] в хабе [{}] не найден, событие пропущено",
                                payload.getName(), hubId));
    }

    private void deleteScenario(Scenario scenario) {
        List<Condition> conditions = new ArrayList<>(scenario.getConditions().values());
        List<Action> actions = new ArrayList<>(scenario.getActions().values());

        scenarioRepository.delete(scenario);
        scenarioRepository.flush();

        conditionRepository.deleteAll(conditions);
        actionRepository.deleteAll(actions);
    }

    private Condition toCondition(ScenarioConditionAvro condition) {
        return Condition.builder()
                .type(ConditionType.valueOf(condition.getType().name()))
                .operation(ConditionOperation.valueOf(condition.getOperation().name()))
                .value(toConditionValue(condition.getValue()))
                .build();
    }

    private Integer toConditionValue(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean booleanValue -> booleanValue ? 1 : 0;
            case Integer intValue -> intValue;
            default -> throw new IllegalArgumentException(
                    "Неподдерживаемый тип значения условия: " + value.getClass());
        };
    }

    private Action toAction(DeviceActionAvro action) {
        return Action.builder()
                .type(ActionType.valueOf(action.getType().name()))
                .value(action.getValue())
                .build();
    }
}
