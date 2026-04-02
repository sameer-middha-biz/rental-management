# CodeRabbit Review Findings

**Date:** 2026-03-18
**Branch:** main
**Review type:** All changes

---

## Critical / Security

### 1. Raw password reset token logged at INFO level
- **File:** `src/main/java/com/rental/pms/modules/user/service/PasswordResetService.java:54-55`
- **Risk:** Anyone with log access could reset any user's password.
- **Fix:** Gate token logging behind a `devMode` flag or downgrade to `DEBUG` level.

```java
// Add to PasswordResetService
@Value("${pms.dev-mode:false}")
private boolean devMode;

// Replace unconditional log.info with:
if (devMode) {
    log.warn("DEV MODE: Password reset token for {}: {}", request.email(), rawToken);
}
```

---

### 2. Sensitive invitation token logged at INFO level
- **File:** `src/main/java/com/rental/pms/modules/user/service/InvitationService.java:99-100`
- **Risk:** Token-bearing accept URL is exposed in production logs.
- **Fix:** Change `log.info` to `log.debug` or gate behind a dev flag.

---

### 3. User email (PII) logged on deletion
- **File:** `src/main/java/com/rental/pms/modules/user/service/UserService.java:157`
- **Risk:** Logging user email on deletion is a GDPR/compliance concern.
- **Fix:** Log only the user ID, not the email.

```java
// Before
log.info("User deleted: userId={}, email={}", id, user.getEmail());

// After
log.info("User deleted: userId={}", id);
```

---

## Potential Issues / Bugs

### 4. Unhandled `IllegalArgumentException` from `TenantStatus.valueOf()`
- **File:** `src/main/java/com/rental/pms/modules/tenant/service/TenantService.java:69`
- **Risk:** Invalid enum string causes an unhandled 500 instead of a proper 400 response.
- **Fix:** Wrap in a try/catch and throw a `BadRequestException`.

```java
private TenantStatus parseStatus(String status) {
    try {
        return TenantStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid tenant status: " + status);
    }
}
```

---

### 5. Unhandled `IllegalArgumentException` from `UserStatus.valueOf()`
- **File:** `src/main/java/com/rental/pms/modules/user/service/UserService.java:92`
- **Risk:** Same issue as finding #4 — invalid status string causes unhandled 500.
- **Fix:** Same pattern: validate or catch and return a 400 response.

---

### 6. Infinite loop risk in slug generation
- **File:** `src/main/java/com/rental/pms/modules/tenant/service/TenantRegistrationService.java:124-133`
- **Risk:** If all slug variants are taken, `generateUniqueSlug` loops indefinitely.
- **Fix:** Add a max iteration guard.

```java
private String generateUniqueSlug(String name) {
    String baseSlug = SlugGenerator.generateSlug(name);
    String slug = baseSlug;
    int suffix = 1;
    int maxAttempts = 1000;
    while (tenantRepository.existsBySlug(slug)) {
        if (suffix > maxAttempts) {
            throw new IllegalStateException("Unable to generate unique slug after " + maxAttempts + " attempts");
        }
        slug = baseSlug + "-" + suffix;
        suffix++;
    }
    return slug;
}
```

---

## Suggestions / Docs

### 7. Test count inconsistency in task doc
- **File:** `docs/tasks/03-tenant-and-user-modules.md:184-188`
- **Issue:** Table shows "Total: 77" but the Definition of Done says "All 167 tests pass".
- **Fix:** Reconcile both numbers to the correct current test count.

---

## Fix Status

| # | File | Finding | Status |
|---|------|---------|--------|
| 1 | `PasswordResetService.java` | Raw reset token in logs | [ ] |
| 2 | `InvitationService.java` | Invitation token in logs | [ ] |
| 3 | `UserService.java` | PII (email) in deletion log | [ ] |
| 4 | `TenantService.java` | `TenantStatus.valueOf()` unhandled | [ ] |
| 5 | `UserService.java` | `UserStatus.valueOf()` unhandled | [ ] |
| 6 | `TenantRegistrationService.java` | Infinite loop in slug generation | [ ] |
| 7 | `docs/tasks/03-tenant-and-user-modules.md` | Test count inconsistency | [ ] |
