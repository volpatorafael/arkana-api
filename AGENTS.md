# Arkana API instructions

These instructions apply to every task in `arkana-api`. They refine the Arkana
workspace instructions for the current Spring Boot domain API.

## Service responsibility

`arkana-api` is Arkana's current and exclusive domain backend. It owns domain
orchestration, authorization, persistence, HTTP DTO mapping, application
errors, and domain database migrations.

The intended boundary is:

```text
arkana-web
  -> external identity provider for sign-in and session refresh
  -> Authorization: Bearer <access token>
  -> arkana-api routes under /v1
     -> Arkana application and domain code
     -> PostgreSQL
```

Supabase Auth is currently the external token issuer, and PostgreSQL may be
hosted by Supabase. These are infrastructure facts only. The web application
must never access Arkana domain tables directly, and this service must not use
RLS policies, the Supabase Data API, PostgREST, RPCs, or Edge Functions for
domain persistence or authorization.

Ordinary `arkana-api` work involving entities, JPA, JDBC, Liquibase, ownership,
authorization, or integration tests does not trigger Supabase-specific skills
or workflows. Use Supabase-specific guidance only when a request explicitly
concerns the current Auth/JWKS integration or Supabase hosting infrastructure.

## Contract-first HTTP API

- `../arkana-api-contract/openapi.yaml` is the source of truth for paths,
  methods, authentication, request and response DTOs, status codes,
  pagination, idempotency, and error shapes.
- Inspect the contract before implementing or changing an endpoint.
- Change the contract before, or in the same change set as, any externally
  observable API behavior.
- Implement every product route under `/v1`.
- Keep JSON DTOs camelCase. Return errors as unextended RFC 9457 Problem
  Details using `application/problem+json`.
- Never expose JPA entities, database rows, table names, PostgreSQL errors,
  Hibernate details, or infrastructure-specific envelopes through HTTP.
- Never accept `ownerId`, `userId`, or equivalent ownership fields from a
  client request. Derive the caller identity from the verified JWT `sub`.
- Preserve backward compatibility unless the contract version is deliberately
  changed.

## Package and code organization

- The root Java package is `com.arkana`.
- Organize application code by technical layer, following the x4 backend
  convention. Do not create a root package per endpoint or feature.
- Keep HTTP controllers in `com.arkana.controller`.
- Keep application services and authorizers in `com.arkana.service`.
- Keep HTTP request and response records in `com.arkana.dto`, grouped into
  business-area subpackages such as `dto.billing`, `dto.catalog`,
  `dto.client`, `dto.profile`, `dto.reading`, and `dto.waitlist`.
- Keep all conversions between domain objects and HTTP DTOs in dedicated
  mappers under `com.arkana.mapper`. Controllers and services must not
  construct response DTOs or response maps directly.
- Create one reusable mapper per principal domain entity. Aggregate mappers
  must compose the entity mappers instead of duplicating mappings for nested
  entities.
- Keep all JPA entities and persistent enums in `com.arkana.domain`, and all
  Spring Data interfaces in `com.arkana.repository`.
- Name JPA entities after the domain concept without an `Entity` suffix, for
  example `Client`, `Reading`, and `BillingAccount`.
- Keep integration ports in `com.arkana.integration` and provider adapters in
  provider-specific subpackages such as `integration.abacatepay` and
  `integration.resend`.
- Keep cross-cutting configuration, security, exception translation, and
  observability in `config`, `security`, `exception`, and `observability`.
- Use JPA repositories for application persistence. Do not inject
  `JdbcClient`, `JdbcTemplate`, or `NamedParameterJdbcTemplate` into services.
- Keep domain decisions out of controllers and repository implementations.
- Use MapStruct for direct mappings between HTTP DTOs, domain objects, and
  persistence entities. Locale-aware or aggregate mappings may use explicit
  default methods, but that presentation logic must remain inside the
  dedicated mapper.
- Declare every field mapping that MapStruct can generate with `@Mapping`.
  Do not manually invoke response DTO constructors inside mappers. Default
  methods may calculate individual derived or localized values, which are then
  consumed by a generated mapping method.
- Compose MapStruct mappers through `@Mapper(uses = ...)`, using constructor
  injection for generated dependencies. Do not inject mapper dependencies with
  `@Autowired` fields in mapper source code.
- Prefer Java records for immutable request/response and value DTOs.
- Use Lombok whenever it reduces boilerplate: `@RequiredArgsConstructor` for
  dependency injection, `@Slf4j` for loggers, and `@Getter`/constructor
  annotations for entities. Use `@Data` for simple mutable data carriers, but
  not for JPA entities; avoid generated `equals`, `hashCode`, and `toString`
  methods that traverse persistence relationships.
- Prefer explicit local-variable types over Java type inference (`var`) when
  the declared type makes the code easier to read.
- Follow the Java formatting: four-space indentation, one declaration or statement per
  line, expanded control-flow blocks, explicit imports, and readable wrapping.

## Authentication and authorization

- Use Spring Security OAuth2 Resource Server to validate access-token JWTs. Do
  not implement a custom JWT filter or parse tokens with hand-written code.
- Configure issuer, JWKS URI, and expected audience outside source code.
- Validate signature, `iss`, `exp`, `nbf`, and the expected audience. Treat
  `sub` as the authenticated Arkana user ID.
- The current Supabase Auth integration uses its asymmetric JWKS keys. Do not
  copy an issuer secret or add local HS256 verification.
- Keep the API stateless. Do not create application login sessions or duplicate
  identity-provider credentials.
- Permit anonymous access only where the OpenAPI contract explicitly marks an
  operation as public. Require authentication by default.
- Authentication is not authorization. Enforce resource ownership, profile
  approval, billing access, and administrative permissions in application
  use cases and persistence queries.
- Never authorize from user-editable token metadata. Authorization data belongs
  in protected Arkana tables or explicitly trusted claims, with JWT staleness
  considered when claims are used.
- Return the contract's RFC 9457 `ProblemDetail` representation for `401` and
  `403` rather than Spring Security's default HTML or implementation-specific
  bodies.
- Keep CORS origins configuration-driven and narrowly scoped per environment.

## Database access

- PostgreSQL is the persistence technology. Keep application code portable so
  the database can move between hosts without redesigning the domain or HTTP
  API.
- Use the datasource credentials configured through the `ARKANA_DATABASE_*`
  environment variables. Liquibase and the application share that datasource
  unless a concrete operational requirement justifies additional database
  users.
- Never use an HTTP API key or identity-provider secret as a JDBC credential.
- Configure Hibernate schema handling as `ddl-auto: none`. Never use
  `create`, `create-drop`, or `update` outside an explicitly isolated test.
- Disable Open Session in View. Load the data required by a use case inside its
  transaction instead of relying on lazy loading from controllers.
- Keep database constraints authoritative for invariants that must survive an
  application implementation change.
- Map snake_case storage fields explicitly and keep them out of HTTP/domain
  DTOs.
- Prefer UUID identifiers and timezone-aware timestamps consistently with the
  existing schema and OpenAPI contract.

## Application ownership and isolation

- Every private query and mutation must constrain ownership using the verified
  JWT `sub`. Use owner-scoped repository methods or specifications; a lookup by
  resource ID alone is insufficient.
- The application datasource uses a shared technical database identity. It does
  not carry the HTTP user's identity, and Arkana does not use Row Level Security
  to authorize requests.
- Never accept ownership fields from request bodies or query parameters. Create
  owned entities with the trusted user ID supplied by the controller.
- For nested resources, first prove ownership of the aggregate root and also
  constrain child lookups by their parent and owner where the schema supports
  it.
- Cross-owner reads and mutations should normally be indistinguishable from a
  missing resource according to the OpenAPI contract, preventing resource
  enumeration.
- The web client must not receive credentials or grants for domain-table
  access. It calls only the versioned Arkana HTTP API.

## Liquibase ownership

- Liquibase in `arkana-api` is the only source of truth for all new Arkana
  domain schema changes.
- Keep the root changelog at
  `src/main/resources/db/changelog/db.changelog-master.yaml`.
- Name changelog files using `yyyyMMdd-xx-description.ext`, where `xx` is a
  zero-padded sequence for that date, for example
  `20260803-01-create-profiles.yaml`.
- Changelogs may mix Liquibase XML, YAML, and formatted SQL when each format is
  appropriate. Keep their execution order deterministic through the dated file
  names included by the master changelog.
- Prefer reviewed PostgreSQL SQL changesets included by the root YAML file,
  especially for constraints, indexes, grants, triggers, and functions.
- Put application behavior in Java. Do not add database functions, procedures,
  or triggers unless a transaction and declarative constraints cannot implement
  the requirement safely and the exception has been explicitly reviewed.
- Never edit a changeset that may already have run. Add a new forward-only
  changeset.
- Never use Hibernate schema generation as a migration mechanism.
- Do not create parallel domain migrations outside `arkana-api`.
- Before enabling Liquibase against an existing environment, reproduce a fresh
  database from the changelog, compare it with the existing schema, and
  explicitly baseline the database. Never let an unverified initial changelog
  run destructively against existing data.
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
- Use Spring's native `ProblemDetail`, `ErrorResponse`, and
  `ErrorResponseException` support for RFC 9457 errors. Do not add a custom
  error envelope or non-standard fields. Centralize safe exception translation
  and never return stack traces, internal messages, SQL details, or secrets.
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
- Add contract tests for each implemented endpoint and keep them independent
  from the database host and identity provider.
- Test authenticated endpoints with valid, expired, malformed, wrong-issuer,
  and wrong-audience JWTs where relevant.
- Add authorization tests proving one user cannot read or mutate another
  user's resources and that pending, rejected, or blocked profiles cannot use
  approved-only operations.
- For every owned resource controller, create two users and one resource for
  each. Verify list filtering, direct lookup rejection, mutation rejection, and
  unchanged persisted state for the second user's resource.
- Run Spring/JPA integration tests with H2 in PostgreSQL compatibility mode,
  with Liquibase enabled and Hibernate schema generation disabled. Any separate
  PostgreSQL-specific integration suite must be implemented in Java with JUnit
  and invoked through Gradle. Do not use Node, shell, or manual `psql` scripts
  as part of the backend verification workflow.
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
the browser, rely on RLS/Data API/PostgREST/Edge Functions, duplicate external
authentication, use privileged database credentials for ordinary traffic, or
introduce a second migration source of truth, call out the conflict before
implementing it.
