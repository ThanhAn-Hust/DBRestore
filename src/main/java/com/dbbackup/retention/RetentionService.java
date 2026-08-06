package com.dbbackup.retention;

import com.dbbackup.domain.model.BackupHistoryRecord;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service evaluating backup records against retention rules.
 * Enforces chain-aware cohort retention:
 * - A FULL base backup MUST NOT be marked for deletion while any dependent INCREMENTAL or DIFFERENTIAL child backup in its chain remains active.
 * - Backups within a chain are only eligible for deletion when ALL backups in that chain cohort exceed the retention threshold.
 */
public class RetentionService {

    public List<String> evaluateDeletions(List<BackupHistoryRecord> records, RetentionRule rule) {
        return evaluateDeletions(records, rule, LocalDateTime.now());
    }

    public List<String> evaluateDeletions(List<BackupHistoryRecord> records, int keepLastCount) {
        return evaluateDeletions(records, new RetentionRule(keepLastCount, null), LocalDateTime.now());
    }

    public List<String> evaluateDeletions(List<BackupHistoryRecord> records, int keepLastCount, LocalDateTime now) {
        return evaluateDeletions(records, new RetentionRule(keepLastCount, null), now);
    }

    public List<String> evaluateDeletions(List<BackupHistoryRecord> records, RetentionRule rule, LocalDateTime now) {
        return evaluateDeletionRecords(records, rule, now).stream()
                .map(BackupHistoryRecord::id)
                .toList();
    }

    public List<BackupHistoryRecord> evaluateDeletionRecords(List<BackupHistoryRecord> records, RetentionRule rule) {
        return evaluateDeletionRecords(records, rule, LocalDateTime.now());
    }

    public List<BackupHistoryRecord> evaluateDeletionRecords(List<BackupHistoryRecord> records, int keepLastCount) {
        return evaluateDeletionRecords(records, new RetentionRule(keepLastCount, null), LocalDateTime.now());
    }

    public List<BackupHistoryRecord> evaluateDeletionRecords(List<BackupHistoryRecord> records, RetentionRule rule, LocalDateTime now) {
        if (records == null || records.isEmpty() || rule == null) {
            return Collections.emptyList();
        }

        if (rule.keepLastCount() == null && rule.retentionDays() == null) {
            return Collections.emptyList();
        }

        // Map records by ID for parent lookup
        Map<String, BackupHistoryRecord> recordMap = records.stream()
                .filter(r -> r.id() != null)
                .collect(Collectors.toMap(BackupHistoryRecord::id, r -> r, (existing, replacement) -> existing));

        // Group records by (dbName, effectiveChainId)
        Map<CohortKey, List<BackupHistoryRecord>> cohortMap = new LinkedHashMap<>();
        for (BackupHistoryRecord record : records) {
            String db = record.dbName() != null ? record.dbName() : "default";
            String effectiveChainId = resolveEffectiveChainId(record, recordMap);
            CohortKey key = new CohortKey(db, effectiveChainId);
            cohortMap.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        // Evaluate retention for cohorts per dbName
        Map<String, List<CohortInfo>> dbCohorts = new HashMap<>();
        for (Map.Entry<CohortKey, List<BackupHistoryRecord>> entry : cohortMap.entrySet()) {
            CohortKey key = entry.getKey();
            List<BackupHistoryRecord> cohortRecords = entry.getValue();

            LocalDateTime latestTimestamp = cohortRecords.stream()
                    .map(this::getRecordTimestamp)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.MIN);

            CohortInfo info = new CohortInfo(key, cohortRecords, latestTimestamp);
            dbCohorts.computeIfAbsent(key.dbName(), k -> new ArrayList<>()).add(info);
        }

        Set<BackupHistoryRecord> toDelete = new LinkedHashSet<>();

        for (List<CohortInfo> cohorts : dbCohorts.values()) {
            // Sort cohorts descending by latest timestamp (newest first)
            cohorts.sort((c1, c2) -> c2.latestTimestamp().compareTo(c1.latestTimestamp()));

            Set<CohortInfo> retainedCohorts = new HashSet<>();

            // 1. keepLastCount rule
            if (rule.keepLastCount() != null && rule.keepLastCount() > 0) {
                int limit = Math.min(rule.keepLastCount(), cohorts.size());
                for (int i = 0; i < limit; i++) {
                    retainedCohorts.add(cohorts.get(i));
                }
            }

            // 2. retentionDays rule
            if (rule.retentionDays() != null && rule.retentionDays() >= 0) {
                LocalDateTime cutoff = now.minusDays(rule.retentionDays());
                for (CohortInfo cohort : cohorts) {
                    // Retain if latest timestamp is on or after cutoff
                    if (!cohort.latestTimestamp().isBefore(cutoff)) {
                        retainedCohorts.add(cohort);
                    }
                }
            }

            // Any cohort not in retainedCohorts is marked for deletion
            for (CohortInfo cohort : cohorts) {
                if (!retainedCohorts.contains(cohort)) {
                    toDelete.addAll(cohort.records());
                }
            }
        }

        return new ArrayList<>(toDelete);
    }

    private String resolveEffectiveChainId(BackupHistoryRecord record, Map<String, BackupHistoryRecord> recordMap) {
        if (record.chainId() != null && !record.chainId().isBlank()) {
            return record.chainId();
        }

        Set<String> visited = new HashSet<>();
        BackupHistoryRecord curr = record;
        while (curr != null) {
            if (curr.chainId() != null && !curr.chainId().isBlank()) {
                return curr.chainId();
            }
            if (curr.parentId() == null || curr.parentId().isBlank()) {
                return curr.id();
            }
            visited.add(curr.id());
            curr = recordMap.get(curr.parentId());
            if (curr == null || visited.contains(curr.id())) {
                break;
            }
        }
        return record.parentId() != null ? record.parentId() : record.id();
    }

    private LocalDateTime getRecordTimestamp(BackupHistoryRecord record) {
        if (record.endTime() != null) {
            return record.endTime();
        }
        if (record.startTime() != null) {
            return record.startTime();
        }
        return LocalDateTime.MIN;
    }

    private record CohortKey(String dbName, String chainId) {}

    private record CohortInfo(
            CohortKey key,
            List<BackupHistoryRecord> records,
            LocalDateTime latestTimestamp
    ) {}
}
