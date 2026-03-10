# keycloak-extensions

Keycloak SPI extensions for unique user attribute enforcement. Built for [Keycloak 26.0](https://www.keycloak.org/).

## What's Inside

### `unique-attribute` — User Profile Validator

Generic validator that ensures a user attribute value is unique across all users in the realm. Can be applied to any attribute in the User Profile configuration.

- Provider ID: `unique-attribute`
- Default error key: `error-attribute-not-unique`

#### Configuration

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `error-message` | string | `error-attribute-not-unique` | Message key returned on duplicate. Override to use attribute-specific messages (e.g. `error-display-name-already-exists`). |
| `lookup-by-username` | boolean | `false` | When `true`, checks uniqueness via `username` lookup on `value.toLowerCase()` instead of attribute search. Useful when paired with the `attribute-sync` listener for case-insensitive uniqueness. |

#### Modes

**Attribute search** (default) — queries users by attribute value using `searchForUserByUserAttributeStream`. Exact match.

**Username lookup** (`lookup-by-username: true`) — looks up `value.toLowerCase()` as a username. Enables case-insensitive uniqueness when paired with an event listener that syncs the attribute to username (e.g. `attribute-sync`).

### `attribute-sync` — Event Listener

Syncs a source user attribute to a target field (with optional transformation) whenever a user registers, updates their profile, or is updated by an admin.

- Provider ID: `attribute-sync`
- Triggers on: `REGISTER`, `UPDATE_PROFILE` user events and `UPDATE` admin events on `USER` resources
- Default: syncs `display_name` → `username` with `lowercase` transformation

#### SPI Configuration

Set via Keycloak SPI config (e.g. environment variables or CLI options):

| Option | Default | Description |
|--------|---------|-------------|
| `sourceAttribute` | `display_name` | Source user attribute to read from |
| `targetField` | `username` | Target: `username`, `email`, `firstName`, `lastName`, or attribute name |
| `transformation` | `lowercase` | `lowercase` or `none` — applied to the source value before writing to target |

### `sync-target-validator` — User Profile Validator

Pre-save validator that checks whether syncing a source attribute to a target field would cause a conflict. Mirrors the transformation logic of `attribute-sync` and validates **before** the profile is saved, preventing silent sync failures.

- Provider ID: `sync-target-validator`
- Default error key: `error-sync-target-conflict`

#### Configuration

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `target-field` | string | `username` | Target field: `username`, `email`, `firstName`, `lastName`, or an attribute name |
| `transformation` | string | `lowercase` | `lowercase` or `none` — must match sync listener config |
| `error-message` | string | `error-sync-target-conflict` | Custom error message key |

#### Target field behavior

| Target | Check | Notes |
|--------|-------|-------|
| `username` | `getUserByUsername(realm, transformed)` | Fails if a different user owns it |
| `email` | `getUserByEmail(realm, transformed)` | Fails if a different user owns it |
| `firstName` / `lastName` | no-op | No uniqueness constraints |
| *(other)* | `searchForUserByUserAttributeStream` | Fails if a different user owns it |

### Bundled Translations

Error message translations are bundled in the JAR via `theme-resources/messages/` and automatically merged into the active theme — no separate theme installation needed.

| Key | EN | ES | PL |
|-----|----|----|----|
| `error-attribute-not-unique` | This value is already taken. Please choose a different one. | Este valor ya está en uso. Por favor, elige otro. | Ta wartość jest już zajęta. Wybierz inną. |
| `error-display-name-already-exists` | Display name is already taken. Please choose a different one. | El nombre para mostrar ya está en uso. Por favor, elige otro. | Nazwa wyświetlana jest już zajęta. Wybierz inną. |
| `error-sync-target-conflict` | This value conflicts with an existing {0}. Please choose a different one. | Este valor entra en conflicto con un {0} existente. Por favor, elige otro. | Ta wartość koliduje z istniejącym {0}. Wybierz inną. |

## Requirements

- Java 17+ (compile time)
- Maven 3.9+ (compile time)
- Keycloak 26.0 (runtime)

## Build

Build the JAR using Maven in Docker (no local Maven installation required):

```bash
docker run --rm -v "$(pwd)":/build -w /build maven:3.9-eclipse-temurin-17 mvn clean package
```

Output: `validator/target/keycloak-extensions-spi-1.0.0.jar`

## Install

Copy the JAR to Keycloak's `providers/` directory and rebuild:

```bash
cp validator/target/keycloak-extensions-spi-1.0.0.jar /opt/keycloak/providers/
/opt/keycloak/bin/kc.sh build
```

## Docker Deployment

### Multi-stage build

Build the SPI and package it into a Keycloak image in a single Dockerfile:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -q

FROM quay.io/keycloak/keycloak:26.0
COPY --from=builder /build/validator/target/keycloak-extensions-spi-1.0.0.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
```

### Docker Compose

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak
    volumes:
      - pgdata:/var/lib/postgresql/data

  keycloak:
    build: .
    command: start
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak
      KC_HOSTNAME: localhost
      KC_HTTP_ENABLED: "true"
      KC_HOSTNAME_STRICT: "false"
      # Enable the attribute-sync event listener
      KC_SPI_EVENTS_LISTENER_ATTRIBUTE_SYNC_ENABLED: "true"
    ports:
      - "8080:8080"
    depends_on:
      - postgres

volumes:
  pgdata:
```

Build and start:

```bash
docker compose up --build
```

## Configuration

After deploying the JAR, configure the providers in the Keycloak Admin Console.

### Step 1: Enable the event listener

Go to **Realm Settings > Events > Event Listeners** and add `attribute-sync`.

By default it syncs `display_name` → `username` with `lowercase` transformation. To customize, set environment variables:

```bash
# Change source attribute
KC_SPI_EVENTS_LISTENER_ATTRIBUTE_SYNC_SOURCE_ATTRIBUTE=my_attr

# Change target field (username, email, firstName, lastName, or any attribute)
KC_SPI_EVENTS_LISTENER_ATTRIBUTE_SYNC_TARGET_FIELD=email

# Change transformation (lowercase or none)
KC_SPI_EVENTS_LISTENER_ATTRIBUTE_SYNC_TRANSFORMATION=none
```

### Step 2: Add validators to the source attribute

Go to **Realm Settings > User Profile**, select the source attribute (e.g. `display_name`), and add validators:

**`sync-target-validator`** — prevents saving if the sync would cause a conflict:

| Option | Value | Notes |
|--------|-------|-------|
| `target-field` | `username` | Must match the listener's `targetField` |
| `transformation` | `lowercase` | Must match the listener's `transformation` |
| `error-message` | `error-sync-target-conflict` | Optional, customize error key |

**`unique-attribute`** — prevents duplicate attribute values:

| Option | Value | Notes |
|--------|-------|-------|
| `lookup-by-username` | `true` | Use `true` when paired with `attribute-sync` targeting username |
| `error-message` | `error-display-name-already-exists` | Optional, customize error key |

### Configuration examples

#### Unique display name with sync protection (recommended)

All three providers working together for case-insensitive unique display names:

1. Enable `attribute-sync` event listener (default config: `display_name` → `username`, `lowercase`)
2. On `display_name` attribute, add validators:
   - `sync-target-validator` with `target-field: username`, `transformation: lowercase`
   - `unique-attribute` with `lookup-by-username: true`, `error-message: error-display-name-already-exists`

The `sync-target-validator` rejects values that would cause a username collision **before** the save, while `unique-attribute` handles the attribute-level uniqueness check.

#### Unique display name (without sync protection)

Simpler setup — syncs and validates, but won't catch conflicts before save:

1. Enable `attribute-sync` event listener
2. On `display_name`, add `unique-attribute` with `lookup-by-username: true`

#### Unique attribute (exact match, no sync)

For simple exact-match uniqueness on any attribute without syncing:

1. On the attribute, add `unique-attribute` validator
2. Optionally set a custom `error-message`

No event listener needed — the validator queries the attribute directly.

## How It Works

### Registration / profile update flow

```
User submits form
  │
  ├─ sync-target-validator: Would lowercased value conflict with another username?
  │   └─ YES → reject with error (before save)
  │
  ├─ unique-attribute: Is this attribute value already taken?
  │   └─ YES → reject with error (before save)
  │
  ├─ Profile saved
  │
  └─ attribute-sync (event listener): Sync display_name.toLowerCase() → username
```

### Attribute search mode (unique-attribute, default)

1. User registers or updates profile
2. Validator calls `searchForUserByUserAttributeStream(realm, attribute, value)`
3. If another user has the same value, validation fails

### Username lookup mode (unique-attribute, lookup-by-username: true)

1. User registers or updates profile with a `display_name`
2. Validator looks up `value.toLowerCase()` as a username
3. If another user already has that username, validation fails

The username lookup mode gives case-insensitive uniqueness without requiring a custom database index.

## Project Structure

```
keycloak-extensions/
├── pom.xml                          # Maven parent POM
└── validator/
    ├── pom.xml
    └── src/main/
        ├── java/network/undefined/keycloak/validator/
        │   ├── UniqueAttributeValidatorFactory.java
        │   ├── SyncTargetValidatorFactory.java
        │   └── AttributeSyncListenerFactory.java
        └── resources/
            ├── META-INF/services/
            │   ├── org.keycloak.validate.ValidatorFactory
            │   └── org.keycloak.events.EventListenerProviderFactory
            └── theme-resources/messages/
                ├── messages_en.properties
                ├── messages_es.properties
                └── messages_pl.properties
```
