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

    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs() {
        String target = System.getenv().getOrDefault("TEMPORAL_TARGET", "localhost:7233");
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs service) {
        return WorkflowClient.newInstance(service);
    }

    @Bean(destroyMethod = "shutdown")
    WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    CommandLineRunner temporalWorker(WorkerFactory factory) {
        return args -> {
            Worker worker = factory.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(OrderSagaWorkflow.class);
            worker.registerActivitiesImplementations(new SandboxOrderActivities());
            factory.start();
        };
    }
}
