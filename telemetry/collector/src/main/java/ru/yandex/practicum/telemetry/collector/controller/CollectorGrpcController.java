package ru.yandex.practicum.telemetry.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.yandex.practicum.grpc.telemetry.collector.CollectorControllerGrpc;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.telemetry.collector.service.CollectorService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class CollectorGrpcController extends CollectorControllerGrpc.CollectorControllerImplBase {

    private final CollectorService collectorService;

    @Override
    public void collectSensorEvent(SensorEventProto request, StreamObserver<Empty> responseObserver) {
        try {
            log.info("Получено gRPC-событие датчика: payload={}, id={}, hubId={}",
                    request.getPayloadCase(), request.getId(), request.getHubId());
            collectorService.collectSensorEvent(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка обработки gRPC-события датчика: {}", request, e);
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e)));
        }
    }

    @Override
    public void collectHubEvent(HubEventProto request, StreamObserver<Empty> responseObserver) {
        try {
            log.info("Получено gRPC-событие хаба: payload={}, hubId={}",
                    request.getPayloadCase(), request.getHubId());
            collectorService.collectHubEvent(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка обработки gRPC-события хаба: {}", request, e);
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e)));
        }
    }
}
