# Local Development Environment Setup

This project uses a containerized local environment to mirror production.

## 1. Prerequisites
* Java 21 JDK (Temurin or Amazon Corretto)
* Docker & Docker Compose
* Maven 3.9+
* Git
* OpenSSL (for RSA key generation)

## 2. Docker Compose Infrastructure
The local environment relies on the following `docker-compose.yml` services:
* **PostgreSQL 16:** Exposed on port 5432. (Database name: `pmsdb`)
* **Redis 7:** Exposed on port 6379. (Used for Bucket4j rate limits, ShedLock, and caching)
* **LocalStack:** Exposed on port 4566. (Used to emulate AWS S3 locally for property photos and guest documents)
* **MailHog:** Exposed on ports 1025 (SMTP) and 8025 (Web UI). (Captures local transactional emails)

## 3. Application Properties (`application-local.yml`)
When generating configuration files or interacting with external services, assume the `local` Spring profile uses these standard URIs:
* JDBC: `jdbc:postgresql://localhost:5432/pmsdb`
* Redis: `redis://localhost:6379`
* AWS S3 Endpoint: `http://localhost:4566` (Path-style access enabled)
* SMTP: `localhost:1025`

## 4. Build Commands
* Compile & Test: `mvn clean verify` (executes Unit and Integration tests via Testcontainers)
* Run Application: `mvn spring-boot:run -Dspring-boot.run.profiles=local`

---

## 5. RSA Key Generation for JWT (RS256)

The application uses RS256 (RSA-signed) JWTs. You must generate a key pair for local development:

```bash
# Create keys directory
mkdir -p src/main/resources/keys

# Generate 2048-bit RSA private key
openssl genrsa -out src/main/resources/keys/private.pem 2048

# Extract public key
openssl rsa -in src/main/resources/keys/private.pem -pubout -out src/main/resources/keys/public.pem
```

**Important:** Add `keys/` to `.gitignore`. Never commit private keys to Git.

In production (Coolify), the keys are injected as environment variables (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`) -- see `ARCHITECTURE.md` Section 22 for the full env var table.

---

## 6. Environment Variables (Local Development)

For local development, environment variables can be set in your IDE run configuration or as system env vars. The `application-local.yml` uses defaults for most, but these are required:

| Variable | Example Value | Notes |
|----------|--------------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pmsdb` | Default in `application-local.yml` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Default Docker Compose user |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Default Docker Compose password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Default |
| `AWS_ACCESS_KEY_ID` | `test` | LocalStack accepts any value |
| `AWS_SECRET_ACCESS_KEY` | `test` | LocalStack accepts any value |
| `AWS_S3_BUCKET` | `pms-files-local` | Created on LocalStack startup |
| `STRIPE_SECRET_KEY` | `sk_test_...` | Use Stripe test mode key |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | From Stripe CLI or dashboard |
| `GOOGLE_CLIENT_ID` | *(optional for local)* | Can skip OAuth locally |
| `GOOGLE_CLIENT_SECRET` | *(optional for local)* | Can skip OAuth locally |

**Tip:** Create a `.env.local` file (gitignored) to store these values. Your IDE can load them automatically.

---

## 7. IDE Setup (IntelliJ IDEA)

### Enable Annotation Processing
Required for **Lombok** and **MapStruct** to work:
1. `Settings` > `Build, Execution, Deployment` > `Compiler` > `Annotation Processors`
2. Check **Enable annotation processing**
3. Select **Obtain processors from project classpath**

### Install Lombok Plugin
1. `Settings` > `Plugins` > Search "Lombok" > Install
2. Restart IntelliJ

### Set Java SDK
1. `Project Structure` > `Project` > `SDK` > Select Java 21
2. `Project Structure` > `Project` > `Language Level` > 21

### Spring Boot Run Configuration
1. Create a "Spring Boot" run config
2. Main class: `com.rental.pms.PmsApplication`
3. Active profiles: `local`
4. Environment variables: load from `.env.local` or set manually

---

## 8. Running Tests

| Command | What It Does | Docker Required? |
|---------|-------------|-----------------|
| `mvn test` | Unit tests only (Surefire) | No |
| `mvn verify` | Unit + Integration tests (Surefire + Failsafe) | Yes (Testcontainers) |
| `mvn test -pl :pms-api -Dtest=BookingServiceTest` | Single test class | No |
| `mvn verify -DskipUnitTests` | Integration tests only | Yes |

**Note:** Integration tests use Testcontainers which automatically starts PostgreSQL and Redis containers. Docker must be running.

---

## 9. Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| Port 5432 already in use | Another PostgreSQL instance running | Stop it or change the Docker Compose port mapping |
| Port 6379 already in use | Another Redis instance running | Stop it or change the Docker Compose port mapping |
| Lombok not recognized (red underlines) | Annotation processing not enabled | See IDE Setup section above |
| MapStruct mappers not generated | Missing `lombok-mapstruct-binding` | Check `pom.xml` annotation processor paths |
| Testcontainers fails to start | Docker not running | Start Docker Desktop |
| `ClassNotFoundException: org.testcontainers...` | Missing test dependency | Ensure Testcontainers is in `pom.xml` with `<scope>test</scope>` |
| Flyway migration fails | Schema already exists from previous run | Drop and recreate `pmsdb`: `docker compose down -v && docker compose up -d` |
| S3 upload fails locally | LocalStack S3 bucket not created | Run: `aws --endpoint-url=http://localhost:4566 s3 mb s3://pms-files-local` |
