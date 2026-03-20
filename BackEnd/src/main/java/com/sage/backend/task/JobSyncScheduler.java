package com.sage.backend.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobSyncScheduler {

    private final TaskService taskService;

    public JobSyncScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(initialDelayString = "${sage.job-sync.initial-delay-ms:5000}", fixedDelayString = "${sage.job-sync.fixed-delay-ms:2000}")
    public void syncJobs() {
        taskService.syncActiveJobs();
    }
}
