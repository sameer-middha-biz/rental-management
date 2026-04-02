# Deployment Guide

> **Living document** — updated after each implementation phase.
> Last updated: Phase 3 (Tenant & User Modules).

This guide covers deploying the Holiday Rental Management Platform in two environments:
- **Primary (MVP):** Coolify on Hostinger VPS (self-hosted)
- **Alternative:** AWS (post-MVP consideration)

---

## Table of Contents

1. [Environment Variables Reference](#environment-variables-reference)
2. [Secret Generation](#secret-generation)
3. [Coolify Deployment](#coolify-deployment)
4. [AWS Deployment](#aws-deployment)
5. [Post-Deploy Verification](#post-deploy-verification)
6. [Phase Update Log](#phase-update-log)

---

## Environment Variables Reference

All variables follow the `PMS_*` prefix convention. Variables marked **Required** have no safe default and must be set before startup. Variables marked **Optional** have a sensible default shown.

### Phase 1 — Core Infrastructure

| Variable | Required | Default | Description |
|---|---|---|---|
| `PMS_DATASOURCE_URL` | Required | — | PostgreSQL JDBC URL. Format: `jdbc:postgresql://host:5432/dbname` |
| `PMS_DATASOURCE_USERNAME` | Required | — | Database user |
| `PMS_DATASOURCE_PASSWORD` | Required | — | Database password |
| `PMS_REDIS_HOST` | Required | — | Redis hostname or IP |
| `PMS_REDIS_PORT` | Optional | `6379` | Redis port |
| `PMS_REDIS_PASSWORD` | Optional | _(empty)_ | Redis password (leave empty if no auth) |
| `PMS_ENCRYPTION_KEY` | Required | — | AES-256 key — **exactly 32 ASCII characters**. Used to encrypt OAuth tokens, 2FA secrets, webhook secrets at rest. |
| `PMS_MAIL_HOST` | Required | — | SMTP hostname |
| `PMS_MAIL_PORT` | Required | — | SMTP port (typically `587` for STARTTLS) |
| `PMS_MAIL_USERNAME` | Optional | _(empty)_ | SMTP username |
| `PMS_MAIL_PASSWORD` | Optional | _(empty)_ | SMTP password |
| `PMS_MAIL_SMTP_AUTH` | Optional | `true` | Enable SMTP authentication |
| `PMS_MAIL_SMTP_STARTTLS` | Optional | `true` | Enable STARTTLS |
| `PMS_S3_REGION` | Required | — | S3-compatible storage region (e.g. `ap-southeast-2`) |
| `PMS_S3_BUCKET_NAME` | Required | — | Bucket name for property photos and documents |
| `PMS_S3_ENDPOINT` | Optional | _(empty)_ | Custom endpoint for S3-compatible storage (e.g. MinIO, Cloudflare R2) |
| `PMS_S3_ACCESS_KEY` | Optional | _(empty)_ | S3 access key (leave empty if using IAM role) |
| `PMS_S3_SECRET_KEY` | Optional | _(empty)_ | S3 secret key (leave empty if using IAM role) |
| `PMS_HIKARI_MAX_POOL_SIZE` | Optional | `20` (`30` prod) | Max DB connection pool size |
| `PMS_HIKARI_MIN_IDLE` | Optional | `5` | Min idle connections |
| `PMS_SERVER_PORT` | Optional | `8080` | HTTP port the app listens on |
| `PMS_LOG_LEVEL_ROOT` | Optional | `INFO` | Root log level |
| `PMS_LOG_LEVEL_APP` | Optional | `INFO` | App-specific log level (`com.rental.pms`) |

### Phase 2 — Security & Auth Foundation

| Variable | Required | Default | Description |
|---|---|---|---|
| `PMS_JWT_PRIVATE_KEY_LOCATION` | Required | — | Path to RSA private key PEM file. Use `file:/run/secrets/jwt_private.pem` in containers or `file:/etc/pms/keys/private.pem` on VPS. |
| `PMS_JWT_PUBLIC_KEY_LOCATION` | Required | — | Path to RSA public key PEM file. Must match the private key above. |
| `PMS_JWT_ACCESS_TOKEN_EXPIRY` | Optional | `15m` | JWT access token lifetime. Format: `15m`, `1h`. Keep short — refresh tokens handle re-auth. |
| `PMS_JWT_REFRESH_TOKEN_EXPIRY` | Optional | `7d` | Refresh token lifetime. Format: `7d`, `30d`. |
| `PMS_CORS_ALLOWED_ORIGINS` | Required | — | Comma-separated list of allowed frontend origins. Example: `https://app.yourdomain.com` |
| `PMS_RATE_LIMIT_ENABLED` | Optional | `true` | Set `false` only in local dev or test environments. |
| `PMS_RATE_LIMIT_TENANT_RPM` | Optional | `100` | Requests per minute per authenticated tenant. |
| `PMS_RATE_LIMIT_ANONYMOUS_RPM` | Optional | `20` | Requests per minute per anonymous IP. |

### Phase 3 — Tenant & User Modules

| Variable | Required | Default | Description |
|---|---|---|---|
| `PMS_INVITATION_EXPIRY_DAYS` | Optional | `7` | Number of days before a team invitation token expires. |
| `PMS_PASSWORD_RESET_EXPIRY_HOURS` | Optional | `1` | Number of hours before a password reset token expires. |

> **Note:** Phase 3 introduced no new required infrastructure variables. The invitation and password reset expiry values have sensible defaults and are optional to override.

---

## Secret Generation

Run these commands once per environment. Store outputs in a secrets manager (not in source control).

### RSA Key Pair (JWT signing — RS256)

```bash
# Generate private key (PKCS#8 format required)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem

# Extract public key
openssl rsa -in private.pem -pubout -out public.pem

# Verify key format — should print "BEGIN PRIVATE KEY" (PKCS#8)
head -1 private.pem
```

Store `private.pem` and `public.pem` in a secrets manager or mount as container secrets.

### Encryption Key (AES-256)

```bash
# Generate a random 32-character key
openssl rand -base64 24 | tr -d '=' | cut -c1-32
```

Paste the output as `PMS_ENCRYPTION_KEY`. It must be **exactly 32 characters**.

---

## Coolify Deployment

> Primary deployment target for MVP.

### Prerequisites

- Coolify installed on Hostinger VPS (or any VPS with Docker)
- PostgreSQL 16 and Redis 7 services created in Coolify (or external managed instances)
- Domain pointed to the VPS IP with SSL termination handled by Coolify/Caddy

### Steps

#### 1. Create a new Coolify Application

1. In Coolify dashboard → **New Resource** → **Application** → **Docker Image** or **Dockerfile**
2. Set build pack to **Dockerfile** (or Nixpacks if using the auto-detect)
3. Set the root directory to the repo root

#### 2. Build Configuration

```
Build Command:   mvn clean package -DskipTests -P prod
Start Command:   java -jar -Dspring.profiles.active=prod target/rental-pms-*.jar
```

Or with a Dockerfile (recommended — add one in Phase 7):

```dockerfile
# To be added in docs/docker/Dockerfile during Phase 7
```

#### 3. Mount RSA Keys as Persistent Volume

In Coolify, under **Storages/Volumes**:

```
Mount path (container): /run/secrets/keys
Host path (VPS):        /opt/pms/secrets/keys
```

Upload `private.pem` and `public.pem` to `/opt/pms/secrets/keys/` on the VPS via SSH.

Then set:
```
PMS_JWT_PRIVATE_KEY_LOCATION = file:/run/secrets/keys/private.pem
PMS_JWT_PUBLIC_KEY_LOCATION  = file:/run/secrets/keys/public.pem
```

#### 4. Set Environment Variables

In Coolify → **Environment Variables**, add all **Required** variables from the table above.

Use the **Secret** toggle for sensitive values (`PMS_DATASOURCE_PASSWORD`, `PMS_REDIS_PASSWORD`, `PMS_ENCRYPTION_KEY`).

#### 5. Database Migrations

Flyway runs automatically on startup (`spring.flyway.enabled=true`). Ensure the DB user has `CREATE TABLE` and `ALTER TABLE` privileges on the target schema.

To verify migrations ran:
```bash
# SSH into VPS
docker exec -it <container_name> sh
# Check Flyway history
curl http://localhost:8080/actuator/health
```

#### 6. Health Check

Coolify health check configuration:
```
Health check path: /actuator/health
Interval:          30s
Timeout:           10s
Retries:           3
```

---

## AWS Deployment

> Post-MVP option. Document this section fully before going multi-region or multi-tenant at scale.

### Recommended Services

| Component | AWS Service |
|---|---|
| Application | ECS Fargate or EC2 (t3.medium minimum) |
| Database | RDS PostgreSQL 16 (db.t3.medium) |
| Cache | ElastiCache Redis 7 (cache.t3.micro) |
| Secrets | AWS Secrets Manager |
| Object Storage | S3 |
| Load Balancer | ALB with ACM SSL certificate |
| Container Registry | ECR |

### RSA Keys on AWS

Store keys in AWS Secrets Manager:

```bash
aws secretsmanager create-secret \
  --name pms/jwt/private-key \
  --secret-string file://private.pem

aws secretsmanager create-secret \
  --name pms/jwt/public-key \
  --secret-string file://public.pem
```

Mount secrets into ECS task definition as environment variables or files. Then set:

```
PMS_JWT_PRIVATE_KEY_LOCATION = file:/run/secrets/jwt_private.pem
PMS_JWT_PUBLIC_KEY_LOCATION  = file:/run/secrets/jwt_public.pem
```

### IAM Role for S3

Rather than `PMS_S3_ACCESS_KEY` / `PMS_S3_SECRET_KEY`, attach an IAM task role to the ECS task with:

```json
{
  "Effect": "Allow",
  "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
  "Resource": "arn:aws:s3:::your-bucket-name/*"
}
```

Leave `PMS_S3_ACCESS_KEY` and `PMS_S3_SECRET_KEY` empty — the SDK will use the instance role automatically.

### Environment Variables on AWS

Set via ECS Task Definition environment variables or Systems Manager Parameter Store. Mark sensitive values as `valueFrom` referencing Secrets Manager ARNs.

---

## Post-Deploy Verification

Run this checklist after each deployment:

```
[ ] GET /actuator/health returns {"status":"UP"}
[ ] Flyway schema history table exists: SELECT * FROM flyway_schema_history;
[ ] Flyway shows migrations V1.0 through V2.6 applied successfully
[ ] POST /api/v1/auth/register creates tenant + user, returns 201 + JWT tokens
[ ] POST /api/v1/auth/login with valid credentials returns 200 + JWT
[ ] POST /api/v1/auth/refresh with valid refresh token returns 200 + new token pair
[ ] POST /api/v1/auth/logout with refresh token returns 204
[ ] GET /api/v1/users/me with valid access token returns current user profile
[ ] GET /api/v1/users (protected) without token returns 401
[ ] GET /api/v1/users (protected) with valid token returns 200
[ ] Exceed rate limit (21 anonymous requests in 1 min) — verify 429 with Retry-After header
[ ] SELECT count(*) FROM roles returns 6 (seeded system roles)
[ ] SELECT count(*) FROM permissions returns 39 (seeded permissions)
```

---

## Phase Update Log

| Phase | Status | What was added |
|---|---|---|
| Phase 1 — Project Setup & Shared Kernel | Done | Core infrastructure variables: DB, Redis, Encryption, Mail, S3, async pool |
| Phase 2 — Security & Auth Foundation | Done | JWT RSA keys, CORS origins, rate limit config, secret generation steps |
| Phase 3 — Tenant & User Modules | Done | Two optional config vars (`PMS_INVITATION_EXPIRY_DAYS`, `PMS_PASSWORD_RESET_EXPIRY_HOURS`). No new infra/secrets required. Flyway migrations V2.0–V2.6 seed roles/permissions. |
| Phase 4 — Property & Subscription Modules | Pending | _(update when complete — expect: S3 bucket usage confirmed)_ |
| Phase 5 — Guest & Booking Modules | Pending | _(update when complete)_ |
| Phase 6 — Payment & Channel Sync | Pending | _(update when complete — expect: Stripe keys, webhook signing secret)_ |
| Phase 7 — Housekeeping & Maintenance | Pending | _(update when complete — expect: Dockerfile, Docker Compose)_ |
| Phase 8 — Cross-Cutting Features | Pending | _(update when complete — expect: SMS provider keys, push notification config)_ |
