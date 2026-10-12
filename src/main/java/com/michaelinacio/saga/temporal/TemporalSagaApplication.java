package com.michaelinacio.saga.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TemporalSagaApplication {
    public static final String TASK_QUEUE = "order-saga-task-queue";

    public static void main(String[] args) {
        SpringApplication.run(TemporalSagaApplication.class, args);
    }

    @Bean
    CommandLineRunner temporalWorker() {
        return args -> {
            String target = System.getenv().getOrDefault("TEMPORAL_TARGET", "localhost:7233");
            WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(target)
                    .build();
            WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(options);
            WorkflowClient client = WorkflowClient.newInstance(service);
            WorkerFactory factory = WorkerFactory.newInstance(client);
            Worker worker = factory.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(OrderSagaWorkflow.class);
            worker.registerActivitiesImplementations(new SandboxOrderActivities());
            factory.start();
        };
    }
}
