package com.michaelinacio.saga.temporal;

import io.temporal.activity.Activity;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderSagaWorkflowTest {
    private TestWorkflowEnvironment environment;
    private final RecordingActivities activities = new RecordingActivities();

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        Worker worker = environment.newWorker(TemporalSagaApplication.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrderSagaWorkflow.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void confirmsOrderWhenAllStepsSucceed() {
        OrderSaga workflow = newWorkflowStub();
        String result = workflow.placeOrder(request("SKU-1"));

        assertEquals("CONFIRMED:order-1", result);
        assertEquals(List.of("reserve-inventory", "charge-payment", "reserve-delivery"),
                activities.events);
    }

    @Test
    void compensatesInReverseOrderWhenDeliveryFails() {
        activities.failDelivery = true;
        OrderSaga workflow = newWorkflowStub();

        assertThrows(RuntimeException.class, () -> workflow.placeOrder(request("FAIL_DELIVERY")));
        assertEquals(List.of("reserve-inventory", "charge-payment", "reserve-delivery",
                "cancel-delivery", "refund-payment", "release-inventory"), activities.events);
    }

    private OrderSaga newWorkflowStub() {
        return environment.getWorkflowClient().newWorkflowStub(
                OrderSaga.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TemporalSagaApplication.TASK_QUEUE)
                        .setWorkflowId("test-order-" + UUID.randomUUID())
                        .build());
    }

    private static OrderRequest request(String sku) {
        return new OrderRequest("order-1", "customer-1", sku, 1,
                new BigDecimal("19.99"), "BRL");
    }

    static class RecordingActivities implements OrderActivities {
        final List<String> events = new ArrayList<>();
        boolean failDelivery;

        public void reserveInventory(OrderRequest request) { events.add("reserve-inventory"); }
        public void releaseInventory(OrderRequest request) { events.add("release-inventory"); }
        public void chargePayment(OrderRequest request) { events.add("charge-payment"); }
        public void refundPayment(OrderRequest request) { events.add("refund-payment"); }
        public void reserveDelivery(OrderRequest request) {
            events.add("reserve-delivery");
            if (failDelivery) {
                throw Activity.wrap(new IllegalStateException("Simulated delivery failure"));
            }
        }
        public void cancelDelivery(OrderRequest request) { events.add("cancel-delivery"); }
    }
}
