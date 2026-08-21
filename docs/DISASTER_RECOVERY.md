# Disaster Recovery & Point-In-Time Restoration Runbook

This operational runbook provides step-by-step procedures for database recovery during an incident, data corruption event, or ransomware recovery scenario.

---

## 1. Finding the Target Backup Record ID

First, inspect the SQLite Audit Database to locate the desired restore point:

```bash
# Query history for a specific database profile
db-backup history --profile prod-mysql --limit 20
```

Sample output:
```
ID                     PROFILE      TYPE         STATUS     SIZE (B)     START TIME
-------------------------------------------------------------------------------------------
b-1723456890-a1b2c3d4  prod-mysql   INCREMENTAL  SUCCESS    15,420,112   2026-08-21 10:00:00
b-1723453290-e5f6g7h8  prod-mysql   INCREMENTAL  SUCCESS    12,104,800   2026-08-21 09:00:00
b-1723420800-x9y8z7w6  prod-mysql   FULL         SUCCESS    842,910,208  2026-08-21 02:00:00
```

---

## 2. Executing Automated Chain Restoration

To restore to the state as of `10:00:00` (`b-1723456890-a1b2c3d4`), simply execute:

```bash
db-backup restore \
  --backup-id "b-1723456890-a1b2c3d4" \
  --passphrase "SecretAesPassphrase2026" \
  --profile target-staging-mysql
```

### What Happens Automatically Behind the Scenes:
1. **Chain Traversal**: `RestoreOrchestrator` queries SQLite and discovers parent lineage:
   $$\text{b-1723420800 (FULL Base)} \longrightarrow \text{b-1723453290 (INC 1)} \longrightarrow \text{b-1723456890 (INC 2 / Target)}$$
2. **Sequential Step 1**: Downloads base FULL backup from S3 $\rightarrow$ Decrypts AES-256-GCM $\rightarrow$ Decompresses Gzip $\rightarrow$ Pipes into `mysql -h target-host -u root dbname`.
3. **Sequential Step 2**: Applies Incremental 1.
4. **Sequential Step 3**: Applies Incremental 2.
5. **Validation**: Verifies process exit codes and logs completion to Audit Log.

---

## 3. Restoring to an Alternate Database (Staging / Verification Instance)

To restore a production backup onto a local or staging database without overwriting production:

```bash
db-backup restore \
  --backup-id "b-1723456890-a1b2c3d4" \
  --passphrase "SecretAesPassphrase2026" \
  --host "staging-db.internal" \
  --port 3306 \
  --user "staging_admin" \
  --password "StagingPassword123" \
  --database "recovered_db"
```

---

## 4. Emergency Troubleshooting

### Issue: "Target backup record not found"
- Cause: The local SQLite audit DB at `~/.db-backup/audit.db` was lost or is running on a different machine.
- Solution: You can restore directly by passing the exact Storage URI directly or copying `audit.db` from the source backup node.

### Issue: "Bad Tag Exception / Decryption Failed"
- Cause: Incorrect passphrase provided or backup file was tampered with in storage.
- Solution: Verify the `--passphrase` provided matches the original encryption key.
