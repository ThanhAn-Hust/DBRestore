package com.dbbackup.retention;

/**
 * Retention rule configuration holding parameters for backup cleanup policies.
 *
 * @param keepLastCount Number of most recent backup cohorts/chains to retain. Must be positive if specified.
 * @param retentionDays Number of days to retain backup cohorts/chains. Must be non-negative if specified.
 */
public record RetentionRule(
    Integer keepLastCount,
    Integer retentionDays
) {
    public RetentionRule {
        if (keepLastCount != null && keepLastCount <= 0) {
            throw new IllegalArgumentException("keepLastCount must be greater than 0");
        }
        if (retentionDays != null && retentionDays < 0) {
            throw new IllegalArgumentException("retentionDays cannot be negative");
        }
    }

    public RetentionRule(Integer keepLastCount) {
        this(keepLastCount, null);
    }

    public static RetentionRule ofKeepLast(int count) {
        return new RetentionRule(count, null);
    }

    public static RetentionRule ofDays(int days) {
        return new RetentionRule(null, days);
    }
}
