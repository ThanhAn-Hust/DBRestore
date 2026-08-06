package com.dbbackup.daemon;

import com.dbbackup.config.ProfileConfigResolver;
import com.dbbackup.domain.model.*;
import com.dbbackup.retention.RetentionRule;
import com.dbbackup.service.BackupOrchestrator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@ShellComponent
@Command(command = "daemon", group = "Daemon Commands")
public class DaemonSchedulerService {

    private static final Logger LOGGER = Logger.getLogger(DaemonSchedulerService.class.getName());

    private final BackupOrchestrator backupOrchestrator;
    private final TaskScheduler taskScheduler;
    private final Map<String, ReentrantLock> jobLocks = new ConcurrentHashMap<>();
    private final Map<String, Thread> activeJobThreads = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    public DaemonSchedulerService() {
        this(new BackupOrchestrator(), createDefaultScheduler());
    }

    public DaemonSchedulerService(BackupOrchestrator backupOrchestrator) {
        this(backupOrchestrator, createDefaultScheduler());
    }

    public DaemonSchedulerService(BackupOrchestrator backupOrchestrator, TaskScheduler taskScheduler) {
        this.backupOrchestrator = backupOrchestrator != null ? backupOrchestrator : new BackupOrchestrator();
        this.taskScheduler = taskScheduler != null ? taskScheduler : createDefaultScheduler();
    }

    private static TaskScheduler createDefaultScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("dbbackup-cron-");
        scheduler.initialize();
        return scheduler;
    }

    @ShellMethod(key = "daemon start", value = "Start daemon schedule runner")
    @Command(command = "start", description = "Start background cron daemon")
    public String startDaemon(
        @ShellOption(value = {"--config", "-c"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "config", shortNames = 'c', defaultValue = "") String configPath
    ) {
        Path path = (configPath != null && !configPath.isBlank())
            ? Paths.get(configPath.replace("~", System.getProperty("user.home")))
            : Paths.get(System.getProperty("user.home"), ".db-backup", "schedule.yml");

        int count = loadAndScheduleJobs(path);
        return "Daemon started. Scheduled " + count + " job(s) from " + path.toAbsolutePath();
    }

    public int loadAndScheduleJobs(Path scheduleConfigPath) {
        stopAllJobs();
        if (scheduleConfigPath == null || !Files.exists(scheduleConfigPath)) {
            LOGGER.warning("Schedule YAML configuration file not found at: " + scheduleConfigPath);
            return 0;
        }

        List<JobConfig> jobs = parseScheduleYaml(scheduleConfigPath);
        int count = 0;
        for (JobConfig job : jobs) {
            if (job.cron() == null || job.cron().isBlank()) {
                LOGGER.warning("Job '" + job.id() + "' missing cron expression. Skipping.");
                continue;
            }
            try {
                String cron = normalizeCron(job.cron());
                ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeJobWithOverlapPolicy(job),
                    new CronTrigger(cron)
                );
                scheduledFutures.put(job.id(), future);
                count++;
                LOGGER.info("Scheduled job '" + job.id() + "' with cron '" + cron + "'");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to schedule job '" + job.id() + "'", e);
            }
        }
        return count;
    }

    public void stopAllJobs() {
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledFutures.entrySet()) {
            try {
                entry.getValue().cancel(true);
            } catch (Exception ignored) {
            }
        }
        scheduledFutures.clear();
    }

    public void executeJobWithOverlapPolicy(JobConfig job) {
        String jobId = job.id();
        ReentrantLock lock = jobLocks.computeIfAbsent(jobId, k -> new ReentrantLock());
        String overlapPolicy = job.onOverlap() != null ? job.onOverlap().toUpperCase() : "SKIP";

        switch (overlapPolicy) {
            case "CANCEL_PREVIOUS":
                Thread previousThread = activeJobThreads.get(jobId);
                if (previousThread != null && previousThread.isAlive()) {
                    LOGGER.info("Cancelling previous execution thread for job '" + jobId + "'");
                    previousThread.interrupt();
                }
                lock.lock();
                try {
                    runJob(job);
                } finally {
                    lock.unlock();
                }
                break;

            case "QUEUE":
                lock.lock();
                try {
                    runJob(job);
                } finally {
                    lock.unlock();
                }
                break;

            case "SKIP":
            default:
                if (lock.tryLock()) {
                    try {
                        runJob(job);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    LOGGER.info("Job '" + jobId + "' skipped due to overlap policy SKIP.");
                }
                break;
        }
    }

    private void runJob(JobConfig job) {
        String jobId = job.id();
        activeJobThreads.put(jobId, Thread.currentThread());
        try {
            LOGGER.info("Starting execution of job '" + jobId + "'");
            DbConnectionConfig pConn = ProfileConfigResolver.resolveProfile(job.profile());
            String dbType = (pConn != null && pConn.dbType() != null) ? pConn.dbType() : "mysql";
            String host = (pConn != null && pConn.host() != null) ? pConn.host() : "localhost";
            int port = (pConn != null && pConn.port() > 0) ? pConn.port() : (dbType.equalsIgnoreCase("postgresql") ? 5432 : 3306);
            String user = (pConn != null && pConn.username() != null) ? pConn.username() : "root";
            String pass = (pConn != null && pConn.password() != null) ? pConn.password() : "";
            String dbName = (pConn != null && pConn.databaseName() != null) ? pConn.databaseName() : "mydb";

            DbConnectionConfig dbConn = new DbConnectionConfig(dbType, host, port, user, pass, dbName);
            BackupType backupType = parseBackupType(job.backupType());

            RetentionRule retentionRule = null;
            if (job.retention() != null && !job.retention().isEmpty()) {
                Object keepLastObj = job.retention().get("keep-last");
                Object daysObj = job.retention().get("retention-days");
                int keepLast = (keepLastObj instanceof Number n) ? n.intValue() : 0;
                int days = (daysObj instanceof Number n) ? n.intValue() : 0;
                if (keepLast > 0 || days > 0) {
                    retentionRule = new RetentionRule(keepLast > 0 ? keepLast : 1, days);
                }
            }

            String destUri = (job.output() != null && !job.output().isBlank()) ? job.output() : "file:///backups/" + dbName + ".sql.gz";
            BackupConfig config = new BackupConfig(
                dbConn,
                backupType,
                DumpFormat.PLAIN_SQL,
                job.tables() != null ? job.tables() : List.of(),
                true,
                job.encrypt(),
                job.passphrase(),
                destUri,
                null,
                null,
                job.singleTransaction() ? Map.of("single-transaction", true) : Map.of()
            );

            backupOrchestrator.executeBackup(config);
            LOGGER.info("Job '" + jobId + "' executed successfully.");
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Error executing job '" + jobId + "'", t);
        } finally {
            activeJobThreads.remove(jobId, Thread.currentThread());
        }
    }

    @SuppressWarnings("unchecked")
    public List<JobConfig> parseScheduleYaml(Path path) {
        List<JobConfig> result = new ArrayList<>();
        try (InputStream in = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            if (data == null || !data.containsKey("jobs")) {
                return result;
            }
            List<Map<String, Object>> jobList = (List<Map<String, Object>>) data.get("jobs");
            if (jobList == null) return result;

            for (Map<String, Object> map : jobList) {
                String id = (String) map.get("id");
                String profile = (String) map.get("profile");
                String cron = (String) map.get("cron");
                String backupType = (String) map.getOrDefault("backup-type", map.get("backupType"));
                boolean singleTx = Boolean.TRUE.equals(map.get("single-transaction")) || Boolean.TRUE.equals(map.get("singleTransaction"));
                String onOverlap = (String) map.getOrDefault("on-overlap", map.getOrDefault("onOverlap", "SKIP"));
                String misfireInstruction = (String) map.getOrDefault("misfire-instruction", map.get("misfireInstruction"));
                boolean encrypt = Boolean.TRUE.equals(map.get("encrypt"));
                String passphrase = (String) map.get("passphrase");
                String output = (String) map.get("output");

                List<String> tables = (List<String>) map.get("tables");
                List<String> notifications = (List<String>) map.get("notifications");
                Map<String, Object> retention = (Map<String, Object>) map.get("retention");

                result.add(new JobConfig(
                    id != null ? id : UUID.randomUUID().toString(),
                    profile,
                    cron,
                    backupType != null ? backupType : "FULL",
                    tables != null ? tables : List.of(),
                    singleTx,
                    onOverlap != null ? onOverlap : "SKIP",
                    misfireInstruction,
                    encrypt,
                    passphrase,
                    output,
                    retention != null ? retention : Map.of(),
                    notifications != null ? notifications : List.of()
                ));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to parse schedule YAML file: " + path, e);
        }
        return result;
    }

    private String normalizeCron(String cron) {
        if (cron == null) return "0 0 * * * *";
        String trimmed = cron.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 5) {
            return "0 " + trimmed;
        }
        return trimmed;
    }

    private BackupType parseBackupType(String typeStr) {
        if (typeStr == null) return BackupType.FULL;
        try {
            return BackupType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            return BackupType.FULL;
        }
    }

    public record JobConfig(
        String id,
        String profile,
        String cron,
        String backupType,
        List<String> tables,
        boolean singleTransaction,
        String onOverlap,
        String misfireInstruction,
        boolean encrypt,
        String passphrase,
        String output,
        Map<String, Object> retention,
        List<String> notifications
    ) {}
}
