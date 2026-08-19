package ru.yandex.practicum.telemetry.analyzer.service;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc.HubRouterControllerBlockingStub;
import ru.yandex.practicum.telemetry.analyzer.model.Action;
import ru.yandex.practicum.telemetry.analyzer.model.Scenario;
import ru.yandex.practicum.telemetry.analyzer.model.Sensor;

import java.time.Instant;

@Slf4j
@Service
public class HubRouterClient {

    private final HubRouterControllerBlockingStub hubRouterClient;

    public HubRouterClient(@GrpcClient("hub-router") HubRouterControllerBlockingStub hubRouterClient) {
        this.hubRouterClient = hubRouterClient;
    }

    public void sendActions(Scenario scenario, Instant timestamp) {
        scenario.getActions().forEach((sensor, action) -> sendAction(scenario, sensor, action, timestamp));
    }

    private void sendAction(Scenario scenario, Sensor sensor, Action action, Instant timestamp) {
        DeviceActionProto.Builder actionBuilder = DeviceActionProto.newBuilder()
                .setSensorId(sensor.getId())
                .setType(ActionTypeProto.valueOf(action.getType().name()));
        if (action.getValue() != null) {
            actionBuilder.setValue(action.getValue());
        }

        DeviceActionRequest request = DeviceActionRequest.newBuilder()
                .setHubId(scenario.getHubId())
                .setScenarioName(scenario.getName())
                .setAction(actionBuilder)
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(timestamp.getEpochSecond())
                        .setNanos(timestamp.getNano())
                        .build())
                .build();

        hubRouterClient.handleDeviceAction(request);
        log.debug("Устройству [{}] отправлено действие [{}] сценария [{}]",
                sensor.getId(), action.getType(), scenario.getName());
    }
}
