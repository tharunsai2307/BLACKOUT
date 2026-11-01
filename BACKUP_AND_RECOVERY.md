# BLACKOUT — Backup, Monitoring, Disaster Recovery & Cost Analysis

## 1. Operational Reliability & Disaster Recovery Policy

As an emergency-response and civil protection system, **BLACKOUT** must guarantee high availability, prompt incident recovery, and data resilience. This document specifies the backup cadences, monitoring telemetry, failover playbooks, and hosting cost breakdown.

---

## 2. Database Backup & Recovery Plan

### 2.1 Backup Cadence & Retention
| Backup Level | Frequency | Retention Window | Storage Tier | Managed Service |
|---|---|---|---|---|
| **Daily Snapshot** | Every 24 hours (02:00 UTC) | 30 Days | Encrypted Cloud Storage (GCS / AWS S3) | Supabase Managed Backup |
| **Point-In-Time Recovery (PITR)** | Continuous Write-Ahead Log (WAL) streaming | 7 Days (up to 28 days on Enterprise) | Dedicated Object Store | Supabase Pro PITR |
| **Weekly Logical Dump** | Weekly (Sunday 00:00 UTC) | 90 Days | Air-gapped cold storage | Automated GitHub Action / cron job using `pg_dump` |

### 2.2 Point-In-Time Recovery (PITR) Execution Procedure
If a database corruption or catastrophic operator error occurs:
1. **Freeze Traffic**: Update Operations Portal maintenance banner; temporarily disable Edge Function writes by rotating function keys or setting maintenance mode.
2. **Initiate Restoration**: In Supabase Console $\to$ Database $\to$ Backups $\to$ Point-in-Time, select timestamp $T_{-5\text{ mins}}$ before the incident.
3. **Spin up Staging Replica**: Restore the backup to a temporary staging project.
4. **Data Integrity Audit**: Execute SQL consistency checks:
   ```sql
   SELECT count(*) FROM emergency_alerts;
   SELECT count(*) FROM public_warnings;
   SELECT count(*) FROM gateway_upload_history;
   ```
5. **Switch Traffic**: Repoint Edge Functions and Operations Portal `SUPABASE_URL` to restored instance.

### 2.3 Local Client-Side Recovery (Room Database)
Mobile clients retain their own local SQLite databases independently of cloud infrastructure:
- **Room Migration Safety**: Sequential non-destructive migrations (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`) prevent SQLite wipe on update.
- **Corrupt SQLite File Fallback**: If SQLite detects hardware disk corruption, Room re-initializes schema while logging `SQLiteDatabaseCorruptException`.

---

## 3. Telemetry & Monitoring Architecture

```
+-------------------------------------------------------------+
|                     Android Mobile Fleet                    |
|  - WorkManager Retry Failure Metrics                        |
|  - Nearby Connections Disconnect Counter                     |
|  - Sentry / Logcat (PII-Stripped / No GPS coordinates)      |
+-------------------------------------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|                  Supabase Edge Functions Ingress            |
|  - Latency & 5xx Error Spike Alarms                         |
|  - Signature Verification Failure Alerts                     |
|  - Cloudflare / Supabase Log Drains                         |
+-------------------------------------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|                  Operations Portal Frontend                 |
|  - Realtime WebSocket Disconnect Telemetry                  |
|  - Synthetic Healthcheck: Uptime Kuma / Better Stack        |
|  - Role-based Login Failure Rate Monitoring                 |
+-------------------------------------------------------------+
```

### 3.1 Error Thresholds & Escalation
- **Critical Alert Trigger**: Edge Function 5xx error rate $> 2\%$ over a 5-minute window $\to$ Auto-alert on-call engineer via PagerDuty / Telegram.
- **Warning Trigger**: Spike in `tampered_warning_detected` audit logs ($> 5$ within 1 hour) indicates active spoofing attack in the mesh.
- **Privacy Rule**: Error logs must NEVER include plain-text civilian SOS descriptions, phone numbers, or exact GPS coordinates. Only aggregate error codes and masked UUIDs are recorded.

---

## 4. Emergency Rollback Playbooks

### 4.1 Backend Edge Function Rollback
If a newly deployed Edge Function introduces regression:
```bash
# Re-deploy previous verified function code tag
supabase functions deploy emergency-alerts --project-ref <PROJECT_ID>
supabase functions deploy warnings --project-ref <PROJECT_ID>
```

### 4.2 Operations Portal Rollback
When hosted on Cloudflare Pages or Vercel:
1. Open Cloudflare Pages / Vercel Dashboard $\to$ Deployments.
2. Select previous stable deployment hash (e.g. `Deploy #42`).
3. Click **Rollback to this deployment**. Instant zero-downtime rollback completed within $< 5$ seconds.

### 4.3 Mobile Client Rollback
Because native Android APKs cannot be forcibly rolled back once installed on user devices:
- All breaking changes must be gated by backend API protocol versioning (`protocolVersion: 1`).
- The backend continues accepting `protocolVersion: 1` payloads while transitioning to future protocols.

---

## 5. Hosting & Operational Cost Breakdown

To support informed decision-making without unexpected expenditure, costs are detailed across deployment tiers:

| Tier | Component | Provider | Scope & Inclusions | Monthly Cost |
|---|---|---|---|---|
| **Pilot / Free Tier** | Database & Edge Functions | Supabase Free | 500 MB PostgreSQL, 50,000 monthly active users, 500K Edge Function invocations, 7-day log retention | **$0.00 / mo** |
| | Portal Hosting | Cloudflare Pages | Unlimited static bandwidth, global Anycast CDN, automatic free SSL, DDoS protection | **$0.00 / mo** |
| | Crash Reporting | Sentry Developer / Firebase | 5,000 crash events / mo | **$0.00 / mo** |
| | Uptime Monitoring | Better Stack Free | 10 uptime monitors, 3-minute checks | **$0.00 / mo** |
| | **Total Pilot Cost** | | | **$0.00 / mo** |
| **Production / Pro Tier** | Database & Edge Functions | Supabase Pro | 8 GB PostgreSQL, Point-In-Time Recovery (PITR 7 days), no project auto-pausing, 2M Edge Function invocations, daily backups | **$25.00 / mo** |
| | Custom Domain | Cloudflare Registrar | `.network` or `.org` domain registration | **~$1.00 / mo** ($12/yr) |
| | Dedicated KMS | AWS KMS / GCP KMS | Customer-managed Ed25519 root signing key storage (per-key fee + API calls) | **~$1.00 - $3.00 / mo** |
| | **Total Hardened Cost**| | | **~$27.00 - $29.00 / mo** |

> [!NOTE]
> Transitioning from Free to Pro tier requires user approval and credit card configuration in Supabase. The platform is fully operational on the Free tier for testing and pilot field exercises.

