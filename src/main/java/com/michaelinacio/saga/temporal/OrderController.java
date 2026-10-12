package com.michaelinacio.saga.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final WorkflowClient workflowClient;

    public OrderController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody OrderRequest request) {
        if (request == null || blank(request.orderId()) || blank(request.customerId())
                || blank(request.sku()) || request.quantity() < 1
                || request.amount() == null || request.amount().signum() <= 0
                || blank(request.currency())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "orderId, customerId, sku, positive quantity/amount and currency are required"));
        }

        String workflowId = "order-saga-" + request.orderId();
        try {
            OrderSaga workflow = workflowClient.newWorkflowStub(
                    OrderSaga.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setTaskQueue(TemporalSagaApplication.TASK_QUEUE)
                            .build());
            WorkflowClient.start(workflow::placeOrder, request);
            return ResponseEntity.accepted().body(Map.of(
                    "workflowId", workflowId,
                    "status", "STARTED",
                    "message", "Sandbox workflow accepted"));
        } catch (io.temporal.client.WorkflowExecutionAlreadyStarted alreadyStarted) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "workflowId", workflowId,
                    "status", "ALREADY_EXISTS"));
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}