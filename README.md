# Arkana API

Current and exclusive Spring Boot implementation of the versioned Arkana domain
API. Login and token refresh remain outside this service. Supabase Auth is the
current external OAuth2/JWT issuer, and PostgreSQL may be hosted by Supabase;
neither detail changes the API's application-owned domain model, persistence,
or authorization.

## Code organization

The Java source follows the layer-based organization used by the x4 backends:

```text
com.arkana
├── controller        HTTP controllers
├── service           application services and authorizers
├── dto               HTTP request and response records by business area
│   ├── billing
│   ├── catalog
│   ├── client
│   ├── profile
│   └── reading
├── domain            JPA entities and persistent enums
├── repository        Spring Data repositories
├── integration       external-service ports
│   ├── abacatepay    AbacatePay adapter
│   └── asaas         Asaas adapter
├── exception         RFC 9457 exception translation
├── config
├── security
└── observability
```

Packages are organized by technical layer, not by endpoint. JPA entities use
the domain name directly, without an `Entity` suffix. HTTP DTOs use explicit
`Request` and `Response` suffixes where needed to avoid colliding with domain
types.

## Data ownership and isolation

Spring Security validates the configured JWT and the API derives the current
user ID exclusively from its `sub` claim. Controllers pass that trusted UUID to
application services; every private read and mutation must constrain ownership
through owner-scoped JPA queries such as `findByIdAndOwnerId` or an equivalent
specification. Requests never accept an `ownerId` or `userId` selector.

The datasource uses one technical database identity for application traffic.
PostgreSQL therefore does not know which HTTP user initiated a query, and
Arkana does not use Row Level Security to authorize API requests. RLS policies,
the Supabase Data API, PostgREST, RPCs, and Edge Functions are not persistence
or authorization mechanisms for this service.

Integration tests for owned resources use JUnit and MockMvc. The standard
isolation scenario persists two users and one resource for each, authenticates
as the first user, and proves that list endpoints return only that user's rows
and that direct reads or mutations of the second user's IDs fail without
changing their data.

## Local verification

Start the local PostgreSQL using the same Compose layout as the x4fare
backends:

```bash
docker compose up -d database
```

Then run the API with `SPRING_PROFILES_ACTIVE=local`. The local profile connects
to `jdbc:postgresql://localhost:5432/arkana` using `arkana` as username and
password. Liquibase applies every pending changeset automatically during
application startup, following the same runtime flow as the x4fare backends.

```bash
./gradlew test
```

The integration suite runs without Docker. It uses H2 in PostgreSQL mode,
Liquibase creates the schema, and Hibernate only validates mappings
(`ddl-auto: none` in tests). PostgreSQL-only changesets are deliberately skipped.

## Runtime configuration

The service follows the x4fare profile layout:

```text
application.yaml        common configuration
application-local.yaml  local PostgreSQL and development settings
application-qa.yaml     QA pool, URLs, and logging
application-prod.yaml   production pool, URLs, and logging
```

Run locally with `SPRING_PROFILES_ACTIVE=local`. Coolify production must set
`SPRING_PROFILES_ACTIVE=prod`; a QA deployment uses `qa`.

Required environment variables:

```text
ARKANA_DATABASE_URL
ARKANA_DATABASE_USERNAME
ARKANA_DATABASE_PASSWORD
OAUTH2_ISSUER_URI
OAUTH2_JWK_SET_URI
OAUTH2_AUDIENCE
```

Optional or environment-specific variables:

```text
ARKANA_ALLOWED_ORIGINS
BILLING_PROVIDER
RESEND_API_KEY
RESEND_FROM
ABACATEPAY_API_KEY
ABACATEPAY_WEBHOOK_SECRET
ABACATEPAY_WEBHOOK_HMAC_KEY
ASAAS_API_KEY
ASAAS_API_URL
ASAAS_WEBHOOK_TOKEN
ARKANA_RECONCILIATION_SECRET
```

`BILLING_PROVIDER` accepts `ASAAS` (the default) or `ABACATEPAY` and selects
the provider only for new checkouts. The provider used to create a paid
subscription is persisted, so cancellation, plan changes, and webhooks keep
using that provider after a configuration change.

During an active trial, Asaas can create a recurring card subscription with
its first charge scheduled for `trialEndsAt`. The Arkana account remains
`TRIALING` until a confirmed payment webhook arrives. AbacatePay remains
available for legacy subscriptions but does not support this deferred checkout.

Asaas uses its hosted recurring checkout with credit card only. Set
`ASAAS_API_URL=https://api-sandbox.asaas.com/v3` for sandbox or
`https://api.asaas.com/v3` for production. Configure the Asaas webhook at
`/v1/webhook/payment/asaas` with a dedicated authentication token sent in the
`asaas-access-token` header. Do not reuse `ASAAS_API_KEY` as that token.

`PIX_AUTOMATIC` is deliberately unavailable through the Asaas adapter. Asaas
ordinary Pix checkout is not equivalent to Pix Automático and is not exposed
under that payment-method name. With `BILLING_PROVIDER=ASAAS`, billing plans
and account overview advertise only `CARD`, and attempts to request
`PIX_AUTOMATIC` fail before any provider request is sent.

The local profile defaults to `jdbc:postgresql://localhost:5432/arkana` with
username and password `arkana`. QA and production receive datasource values
from `ARKANA_DATABASE_*`.

Liquibase uses the same datasource credentials as the application. There is no
second migration user or per-request PostgreSQL role. For the current Supabase
Auth configuration, set `OAUTH2_AUDIENCE=authenticated` in Coolify.

## Database migrations

Liquibase owns the domain schema through
`src/main/resources/db/changelog/db.changelog-master.yaml`. Portable relational
changes and deterministic catalog data run unchanged on H2 and PostgreSQL.
Profile creation and every domain behavior remain in Java; the Arkana schema
does not depend on provider-managed database schemas and does not define custom
functions, procedures, or triggers.

PostgreSQL array columns are represented as arrays in Liquibase and as typed
Java collections in JPA. In particular, `available_payment_methods` is a
`text[]`/`text array` column mapped to `List<String>`; seeds use the portable
SQL array constructor rather than CSV strings.

All automated backend verification belongs to the Java test suite and runs
through Gradle. Do not depend on scripts from a legacy adapter or on manual
`psql` scripts. If PostgreSQL-specific integration coverage is added, implement
it as an isolated JUnit suite rather than as another language or shell workflow.

## Production deployment with Coolify

Coolify is connected to this repository through its GitHub App. Pull requests
targeting `main` run `.github/workflows/continuous_integration.yml`. Pushes and
merges to `main` run `.github/workflows/production_deploy.yml`, which repeats
the checks and validates that the production Docker image can be built.

The actual deployment belongs to the Coolify GitHub App:

```text
merge or push to main
  -> Coolify receives the GitHub event
  -> Coolify builds /Dockerfile
  -> Coolify deploys the new container
```

Create a Git-based application in Coolify with:

```text
Repository: volpatorafael/arkana-api
Branch: main
Build pack: Dockerfile
Dockerfile location: /Dockerfile
Port: 8080
Health check path: /actuator/health
Auto Deploy: enabled
```

No `COOLIFY_TOKEN`, `COOLIFY_WEBHOOK`, GHCR configuration, or application secret
is required in GitHub Actions. Configure all values from the Runtime
configuration section as Coolify runtime environment variables. Do not expose
them as Docker build arguments.

Protect `main` and require the `Main - PR Check` status before merge. This makes
the PR check the deployment gate: Coolify only receives merged commits that
already passed validation. Use Coolify's deployment history for rollback.
