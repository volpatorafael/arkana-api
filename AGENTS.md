# Arkana API instructions

These instructions apply to every task in `arkana-api`. They refine the Arkana
workspace instructions for the Spring Boot implementation. Where the workspace
still describes Spring Boot as a future adapter or `arkana-supabase` as the
owner of new domain migrations, the transition rules in this file take
precedence for this project.

## Service responsibility

`arkana-api` is Arkana's Spring Boot HTTP API and the target implementation of
the product backend. It owns domain orchestration, authorization, persistence,
HTTP DTO mapping, application errors, and new domain database migrations.

The intended boundary is:

```text
arkana-web
  -> Supabase Auth only for sign-in and session refresh
  -> Authorization: Bearer <Supabase access token>
  -> arkana-api routes under /v1
     -> Arkana application and domain code
     -> PostgreSQL currently hosted by Supabase
```

The web application must never access Arkana domain tables through the
Supabase Data API. Do not use Supabase Edge Functions or PostgREST as an
internal persistence layer for this service. Supabase is temporarily the Auth
provider and PostgreSQL host, not Arkana's domain API.

## Contract-first HTTP API

- `../arkana-api-contract/openapi.yaml` is the source of truth for paths,
  methods, authentication, request and response DTOs, status codes,
  pagination, idempotency, and error shapes.
- Inspect the contract before implementing or changing an endpoint.
- Change the contract before, or in the same change set as, any externally
  observable API behavior.
- Implement every product route under `/v1`.
- Keep JSON DTOs camelCase and use the application-owned error codes defined by
  the contract.
- Never expose JPA entities, database rows, table names, PostgreSQL errors,
  Hibernate details, or Supabase response envelopes through HTTP.
- Never accept `ownerId`, `userId`, or equivalent ownership fields from a
  client request. Derive the caller identity from the verified JWT `sub`.
- Preserve backward compatibility unless the contract version is deliberately
  changed.

## Package and code organization

- The root Java package is `com.arkana`.
- Prefer package-by-feature, for example `clients`, `readings`, `profiles`,
  `catalog`, `billing`, and `waitlist`.
- Within a feature, keep HTTP adapters, application use cases, domain logic,
  and persistence details separated when the distinction adds value. Do not
  create empty architectural layers preemptively.
- Keep domain decisions out of controllers and repository implementations.
- Use explicit mapper code or dedicated mappers between HTTP DTOs, domain
  objects, and persistence entities.
- Prefer Java records for immutable request/response and value DTOs.
- Lombok is allowed, but do not use `@Data` on JPA entities. Avoid generated
  `equals`, `hashCode`, and `toString` methods that traverse lazy relationships.

## Authentication and authorization

- Use Spring Security OAuth2 Resource Server to validate Supabase access-token
  JWTs. Do not implement a custom JWT filter or parse tokens with hand-written
  code.
- Configure issuer, JWKS URI, and expected audience outside source code.
- Validate signature, `iss`, `exp`, `nbf`, and the expected audience. Treat
  `sub` as the authenticated Supabase user ID.
- The JWKS flow requires asymmetric Supabase signing keys. Do not copy or use a
  Supabase JWT secret for local HS256 verification.
- Keep the API stateless. Do not create application login sessions or duplicate
  Supabase Auth user credentials.
- Permit anonymous access only where the OpenAPI contract explicitly marks an
  operation as public. Require authentication by default.
- Authentication is not authorization. Enforce resource ownership, profile
  approval, billing access, and administrative permissions in application
  use cases and persistence queries.
- Never authorize from Supabase `user_metadata`. Authorization data belongs in
  protected Arkana tables or trusted `app_metadata`, with JWT staleness
  considered when claims are used.
- Return the contract's stable `401` and `403` error representations rather
  than Spring Security's default HTML or implementation-specific bodies.
- Keep CORS origins configuration-driven and narrowly scoped per environment.

## Database access

- PostgreSQL is the persistence technology. Keep application code portable so
  the database can move away from Supabase hosting without redesigning the
  domain or HTTP API.
- Connect with a dedicated least-privilege application login role. Never use
  `postgres`, `service_role`, a secret API key, or a role with `BYPASSRLS` for
  ordinary application traffic.
- Use separate credentials for schema migration and runtime access. The
  Liquibase role may perform required DDL; the application role receives only
  the necessary DML privileges.
- Configure Hibernate schema handling as `ddl-auto: validate`. Never use
  `create`, `create-drop`, or `update` outside an explicitly isolated test.
- Disable Open Session in View. Load the data required by a use case inside its
  transaction instead of relying on lazy loading from controllers.
- Keep database constraints authoritative for invariants that must survive an
  application implementation change.
- Map snake_case storage fields explicitly and keep them out of HTTP/domain
  DTOs.
- Prefer UUID identifiers and timezone-aware timestamps consistently with the
  existing schema and OpenAPI contract.

## RLS and the Data API

- Treat RLS as defense in depth, even though all normal domain access crosses
  this service.
- A JDBC connection does not automatically receive the PostgREST request JWT
  context used by `auth.uid()`. Do not assume existing caller-scoped Supabase
  policies protect queries made by Spring.
- Any design that propagates caller identity into a PostgreSQL session must use
  transaction-local state, work correctly with pooled connections, be covered
  by integration tests, and receive an explicit security review.
- Every application query and mutation must independently constrain ownership;
  RLS is not a substitute for application authorization.
- Do not grant domain-table access to `anon` or `authenticated` merely to make
  Spring persistence work. Use the dedicated application role.
- When the Edge Function cutover is complete, revoke obsolete Data API grants
  and stop exposing Arkana domain tables through the Data API.

## Liquibase ownership

- Liquibase in `arkana-api` is the only source of truth for all new Arkana
  domain schema changes.
- Keep the root changelog at
  `src/main/resources/db/changelog/db.changelog-master.yaml`.
- Prefer reviewed PostgreSQL SQL changesets included by the root YAML file,
  especially for constraints, indexes, grants, policies, triggers, and
  functions.
- Never edit a changeset that may already have run. Add a new forward-only
  changeset.
- Never use Hibernate schema generation as a migration mechanism.
- Never create, alter, or drop objects in Supabase-managed `auth`, `storage`,
  or `realtime` schemas. Arkana-owned helpers belong in an Arkana-owned,
  non-exposed schema.
- Do not add parallel domain migrations to `arkana-supabase` after Liquibase
  ownership begins.
- Before enabling Liquibase against an existing environment, convert and verify
  the historical Supabase domain migrations, reproduce a fresh database from
  the Liquibase changelog, compare it with the existing schema, and explicitly
  baseline the existing database. Never let an unverified initial changelog run
  destructively against existing data.
- Avoid pinning PostgreSQL extension versions in new migrations unless the
  hosting environment explicitly requires it.

## JPA conventions

- Keep persistence entities separate from HTTP DTOs.
- Be explicit about column names, nullability, lengths, unique constraints,
  indexes, enum persistence, and relationship ownership.
- Avoid broad bidirectional associations. Prefer aggregate-oriented loading and
  explicit queries.
- Do not use cascade remove or orphan removal without checking ownership and
  lifecycle semantics.
- Prevent N+1 queries with targeted fetch joins, entity graphs, projections, or
  purpose-built repository queries; do not make every association eager.
- Use optimistic locking where concurrent edits would otherwise silently
  overwrite user data.
- Paginated database reads must have deterministic ordering consistent with the
  contract.

## Errors, validation, and observability

- Validate request syntax at the HTTP boundary and enforce business invariants
  in application/domain code.
- Centralize exception-to-`ApiError` mapping. Do not return stack traces,
  internal messages, SQL details, or secrets.
- Include a request/correlation identifier in logs and error responses when the
  contract provides for one.
- Use structured, privacy-conscious logging. Never log bearer tokens, cookies,
  database credentials, private client notes, full reading content, or personal
  data unnecessarily.
- Expose only the required Actuator endpoints. Health probes may be public only
  when deployment infrastructure requires it; protect diagnostic and metrics
  endpoints.
- Keep secrets in environment variables or an external secret manager. Commit
  safe defaults and placeholders only.

## Testing and verification

- Every behavior change requires tests at the narrowest useful level.
- Add contract tests for each implemented endpoint and keep them portable so
  they can validate the OpenAPI behavior independently of the old Edge Function.
- Test authenticated endpoints with valid, expired, malformed, wrong-issuer,
  and wrong-audience JWTs where relevant.
- Add authorization tests proving one user cannot read or mutate another
  user's resources and that pending, rejected, or blocked profiles cannot use
  approved-only operations.
- Test persistence and Liquibase behavior against PostgreSQL. Do not rely on H2
  for PostgreSQL SQL, constraints, RLS, functions, or migration tests.
- Verify new migrations both on an empty database and as an upgrade from the
  previous application version.
- Run at minimum `./gradlew test` before handing off code. Run any contract and
  integration suites relevant to the changed slice.

## Build and dependency discipline

- The baseline is Java 21, Spring Boot 4.1, Gradle Wrapper, and Kotlin Gradle
  DSL. Do not lower or upgrade these baselines incidentally.
- Prefer Spring Boot dependency management. Add explicit dependency versions
  only when Boot does not manage them or a documented compatibility reason
  requires an override.
- Keep the Gradle wrapper and dependency lock/configuration files committed.
- Avoid adding Spring Cloud, messaging, caching, AI, or other infrastructure
  until a concrete product slice requires it.

## Change workflow

1. Read the relevant OpenAPI operation and schemas.
2. Identify the feature's authorization and ownership rules.
3. Change the contract in the same change set if external behavior changes.
4. Add a forward-only Liquibase changeset if persistence changes.
5. Implement DTO mapping, use case, domain logic, and persistence explicitly.
6. Add security, contract, and integration tests proportional to the risk.
7. Run the relevant Gradle checks and review logs for leaked sensitive data.

If a request would bypass the versioned HTTP contract, expose domain tables to
the browser, duplicate Supabase Auth, use privileged database credentials for
ordinary traffic, or introduce a second migration source of truth, call out
the conflict before implementing it.
