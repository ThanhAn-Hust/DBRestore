package com.dbbackup.config;

import com.dbbackup.audit.JsonStateTracker;
import com.dbbackup.audit.SqliteAuditLogService;
import com.dbbackup.domain.port.AuditLogService;
import com.dbbackup.domain.port.StateTracker;
import com.dbbackup.retention.RetentionService;
import com.dbbackup.security.StartupCleanupSweep;
import com.dbbackup.storage.StorageProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AppConfig {

    @Bean
    public AuditLogService auditLogService() {
        SqliteAuditLogService service = new SqliteAuditLogService();
        service.initSchema();
        return service;
    }

    @Bean
    public StateTracker stateTracker() {
        return new JsonStateTracker();
    }

    @Bean
    public StorageProviderFactory storageProviderFactory() {
        return new StorageProviderFactory();
    }

    @Bean
    public RetentionService retentionService() {
        return new RetentionService();
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("dbbackup-scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onStartupSweep() {
        StartupCleanupSweep.performSweep();
    }
}
