# Efí billing production runbook

This runbook activates Efí only for new Arkana subscriptions. Existing Asaas
and Abacate Pay subscriptions remain bound to their persisted provider.

## 1. Account and API readiness

1. Use an approved Efí Empresas account and complete the card-acquiring KYC.
2. Enable the API Cobranças subscription scopes and the Pix Automático scopes
   for locations, recurrences, immediate charges, recurring charges and webhooks.
3. Create separate applications and credentials for homologation and production.
4. Generate the Pix certificate for each environment. Store the P12 outside the
   image, mount it read-only and rotate it before expiry.

## 2. Efí plans

Create both plans in the same API Cobranças environment as the credentials used
by `arkana-api`. The plan defines recurrence, not price; Arkana sends the current
price as the subscription item when checkout is created.

For homologation, authenticate against
`https://cobrancas-h.api.efipay.com.br/v1/authorize`, then call
`POST https://cobrancas-h.api.efipay.com.br/v1/plan` with these bodies:

```json
{
  "name": "Arkana Mensal - Homologacao",
  "interval": 1,
  "repeats": null
}
```

```json
{
  "name": "Arkana Anual - Homologacao",
  "interval": 12,
  "repeats": null
}
```

For production, use the production API and names without `- Homologacao`.
Record each numeric `data.plan_id` returned by Efí. Register those IDs with this
idempotent SQL, replacing both placeholders:

The following copy/paste sequence uses homologation and requires `jq`. It reads
credentials interactively so the secret is not written into shell history:

```bash
printf 'Efí homologation Client_Id: '
IFS= read -r EFI_SETUP_CLIENT_ID
printf 'Efí homologation Client_Secret: '
stty -echo
IFS= read -r EFI_SETUP_CLIENT_SECRET
stty echo
printf '\n'

EFI_SETUP_AUTH_RESPONSE=$(curl -sS \
  --request POST \
  --user "${EFI_SETUP_CLIENT_ID}:${EFI_SETUP_CLIENT_SECRET}" \
  --header 'Content-Type: application/json' \
  --data '{"grant_type":"client_credentials"}' \
  https://cobrancas-h.api.efipay.com.br/v1/authorize)

printf '%s\n' "$EFI_SETUP_AUTH_RESPONSE" | jq .
EFI_SETUP_ACCESS_TOKEN=$(printf '%s' "$EFI_SETUP_AUTH_RESPONSE" | jq -er '.access_token')

curl -sS \
  --request POST \
  --header "Authorization: Bearer ${EFI_SETUP_ACCESS_TOKEN}" \
  --header 'Content-Type: application/json' \
  --data '{"name":"Arkana Mensal - Homologacao","interval":1,"repeats":null}' \
  https://cobrancas-h.api.efipay.com.br/v1/plan | jq .

curl -sS \
  --request POST \
  --header "Authorization: Bearer ${EFI_SETUP_ACCESS_TOKEN}" \
  --header 'Content-Type: application/json' \
  --data '{"name":"Arkana Anual - Homologacao","interval":12,"repeats":null}' \
  https://cobrancas-h.api.efipay.com.br/v1/plan | jq .

unset EFI_SETUP_CLIENT_ID EFI_SETUP_CLIENT_SECRET EFI_SETUP_AUTH_RESPONSE EFI_SETUP_ACCESS_TOKEN
```

Do not run the block a second time after both calls succeed; it would create
duplicate Efí plans. Copy the monthly and annual `data.plan_id` values before
the final `unset` (the IDs remain visible in the command output).

API-created plans may not appear as entries in the regular financial dashboard.
Confirm them with `GET /v1/plans`, and inspect the calls in
`API > Aplicações > <application> > Homologação > Histórico de Requisições`.
A plan is only a recurrence template; it does not create a subscription or a
financial transaction by itself.

```sql
insert into billing_provider_plan_mappings
  (id, plan_price_id, provider, provider_product_id)
values
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000001', 'EFI', '<efi-monthly-plan-id>'),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000002', 'EFI', '<efi-annual-plan-id>')
on conflict (plan_price_id, provider) do update
set provider_product_id = excluded.provider_product_id;
```

Verify the result without exposing any credentials:

```sql
select plan_price_id, provider, provider_product_id
from billing_provider_plan_mappings
where provider = 'EFI'
order by plan_price_id;
```

Pix Automático uses the Arkana plan amount directly and therefore does not need
an external plan ID. The mappings above are required for card subscriptions.
The schema has one Efí mapping per Arkana price, not one per environment. If a
shared database is temporarily used for homologation, replace the homologation
IDs with production plan IDs before switching to production Efí credentials.

## 3. Runtime configuration

### 3.1 Values to collect or create

Use one Efí application with both **API de Emissões** and **API Pix** enabled.
In `Conta Efí > API > Aplicações`, open that application and select its
**Produção** credentials. Do not mix production credentials with a homologation
certificate or URL.

| Variable                     | Where the value comes from |
|------------------------------| --- |
| `EFI_CLIENT_ID`              | Efí: `API > Aplicações > <Arkana application> > Produção > Client_Id`. |
| `EFI_CLIENT_SECRET`          | Efí: the `Client_Secret` beside the production `Client_Id` above. It is a backend secret. |
| `EFI_CHARGES_URL`            | Fixed production route: `https://cobrancas.api.efipay.com.br`. |
| `EFI_PIX_URL`                | Fixed production route: `https://pix.api.efipay.com.br`. |
| *`EFI_PIX_KEY`               | Efí: copy the receiving key registered for the Arkana account from `Pix > Minhas chaves`. Copy the key itself, not a QR Code or location ID. |
| `EFI_PIX_AUTOMATIC_ENABLED`  | Use `false` to offer only credit card. Change to `true` only when Pix Automático is ready for customers. |
| *`EFI_CERTIFICATE_PATH`      | A path chosen by Arkana inside the API container. Mount the production P12 at `/app/secrets/efi-production.p12` and use that exact path. This value is not shown by Efí. |
| `EFI_CERTIFICATE_PASSWORD`   | The password of the P12. Leave it empty only when the downloaded P12 has no password. This is not the Efí account password. |
| `EFI_CHARGES_WEBHOOK_SECRET` | Generate locally with `openssl rand -hex 32`. Efí does not provide it. Use a different generated value for each webhook variable. |
| `EFI_PIX_WEBHOOK_SECRET`     | Generate locally with `openssl rand -hex 32`. It is placed in the Pix callback URL and compared by `arkana-api`. |
| `EFI_PIX_WEBHOOK_HMAC_KEY`   | Generate locally with `openssl rand -hex 32`. Configure the same value in the trusted Pix ingress that calculates `X-Efi-Signature`; do not send it to Efí. |
| `EFI_PIX_REQUIRE_MTLS`       | Arkana production policy: the literal value `true`. |
| `ARKANA_WEBHOOK_BASE_URL`    | Public HTTPS origin of `arkana-api`, with no trailing slash; production is `https://api.getarkana.com`. It is not the web app URL. |
| `BILLING_PROVIDER`           | Keep the current provider (`ASAAS` or `ABACATEPAY`) for the first deployment. Change it to `EFI` only in the activation step. |

Generate three independent secrets and store them immediately in a password
manager before putting them in Coolify:

```bash
openssl rand -hex 32 # EFI_CHARGES_WEBHOOK_SECRET
openssl rand -hex 32 # EFI_PIX_WEBHOOK_SECRET
openssl rand -hex 32 # EFI_PIX_WEBHOOK_HMAC_KEY
```

### 3.2 Mount the production certificate in Coolify

1. In Efí, go to `API > Meus Certificados`, select **Produção**, create a new
   certificate and download the P12. Efí only offers that download immediately
   after creation, so also keep an encrypted backup outside the repository.
2. Do not paste the P12 into **File Mount > Content**: that field creates a text
   file and a P12 is binary. **Volume Mount** also does not fit because it creates
   a managed Docker volume that is initially empty.
3. Create a certificate-only directory under the application directory shown by
   Coolify and upload the P12 into it over SSH/SCP. For example, if Coolify shows
   `/data/coolify/applications/<application-id>` as the suggested source, create
   `/data/coolify/applications/<application-id>/efi-secrets` and place
   `efi-production.p12` inside it. Do not put any other files in this directory.
4. In `arkana-api > Persistent Storage`, choose **Directory Mount** and configure:

   ```text
   Source Directory:      /data/coolify/applications/<application-id>/efi-secrets
   Destination Directory: /app/secrets
   ```

   This mounts the whole dedicated directory because this Coolify UI does not
   offer uploading binary content through **File Mount**.
5. Redeploy and use the Coolify terminal to verify readability with
   `test -r /app/secrets/efi-production.p12`. Do not print the file with `cat`.
6. Set `EFI_CERTIFICATE_PATH=/app/secrets/efi-production.p12`. If the P12 is
   passwordless, keep `EFI_CERTIFICATE_PASSWORD=` as an empty value.

For homologation, use separate files and paths ending in
`efi-homologation.p12`; never overwrite or reuse the production certificate.

### 3.3 Coolify copy/paste block for `arkana-api`

Open `arkana-api > Environment Variables > Developer View`, replace every
`REPLACE_...` value, and paste the complete block. These are runtime variables;
disable **Build Variable** for the secrets after saving.

```dotenv
# Keep the current provider for the first deployment. Do not use EFI yet.
BILLING_PROVIDER=ASAAS

# Efí production credentials: API > Aplicações > <Arkana> > Produção
EFI_CLIENT_ID=REPLACE_WITH_PRODUCTION_CLIENT_ID
EFI_CLIENT_SECRET=REPLACE_WITH_PRODUCTION_CLIENT_SECRET

# Official Efí production base URLs; copy these literally.
EFI_CHARGES_URL=https://cobrancas.api.efipay.com.br
EFI_PIX_URL=https://pix.api.efipay.com.br

# Pix key and mounted production certificate.
EFI_PIX_KEY=REPLACE_WITH_PIX_KEY
EFI_PIX_AUTOMATIC_ENABLED=false
EFI_CERTIFICATE_PATH=/app/secrets/efi-production.p12
EFI_CERTIFICATE_PASSWORD=

# Three different values generated with `openssl rand -hex 32`.
EFI_CHARGES_WEBHOOK_SECRET=REPLACE_WITH_RANDOM_CHARGES_SECRET
EFI_PIX_WEBHOOK_SECRET=REPLACE_WITH_RANDOM_PIX_SECRET
EFI_PIX_WEBHOOK_HMAC_KEY=REPLACE_WITH_RANDOM_INGRESS_HMAC_KEY

# Production callback validation and public API origin.
EFI_PIX_REQUIRE_MTLS=true
ARKANA_WEBHOOK_BASE_URL=https://api.getarkana.com
```

If production currently uses `ABACATEPAY`, keep
`BILLING_PROVIDER=ABACATEPAY` in the first line instead. Saving this block must
not route new checkouts to Efí yet.

### 3.4 Web build variables (not backend secrets)

The browser only receives these two publishable tokenization values:

```dotenv
VITE_EFI_ACCOUNT_ID=REPLACE_WITH_ACCOUNT_IDENTIFIER
VITE_EFI_ENVIRONMENT=production
```

Get `VITE_EFI_ACCOUNT_ID` from
`Conta Efí > API > Aplicações > Introdução > Identificador de conta`. Depending
on the current panel layout, `Aplicações` may be omitted from the breadcrumb and
the **Identificador de conta** shortcut appears in the upper-right corner of the
API introduction page, beside **Códigos de erros**. The value is also called
`payee_code` in Efí documentation.

The account identifier is not an environment-specific credential: use the same
value with `VITE_EFI_ENVIRONMENT=sandbox` for homologation and with
`VITE_EFI_ENVIRONMENT=production` for production. It is not `Client_Id`, a Pix
key or an account number. If the option is absent, confirm that the signed-in
account supports API Cobranças and that the current user has permission to view
API account information; ask Efí support for the account's `payee_code` if it
still does not appear.

The current `arkana-web` production deployment is on Cloudflare Pages, so put
these variables in the **Cloudflare Pages production build environment**, not
in the `arkana-api` Coolify application, and trigger a new web build. `VITE_`
values are embedded by Vite at build time. If the web deployment is later moved
to Coolify, mark them as build variables on that separate web application.

## 4. Webhooks and ingress

- API Cobranças notification URL:
  `https://api.getarkana.com/v1/webhook/payment/efi/charges?webhookSecret=<EFI_CHARGES_WEBHOOK_SECRET>`.
  `arkana-api` sends this URL in the `notification_url` metadata of each new
  card subscription; there is no global dashboard field to fill in.
- Use this same Pix callback URL when registering all three relevant Pix
  webhook types:
  `https://api.getarkana.com/v1/webhook/payment/efi/pix?webhookSecret=<EFI_PIX_WEBHOOK_SECRET>&ignorar=`.
  Register it through `PUT /v2/webhook/:chave` (immediate Pix),
  `PUT /v2/webhookrec` (recurrence status), and `PUT /v2/webhookcobr`
  (recurring charges). The application needs the corresponding webhook read
  and write scopes.
- Require a verified client certificate on the Pix ingress route. The reverse
  proxy must reject requests that did not complete mTLS, set
  `X-Client-Certificate-Verified: SUCCESS`, and add `X-Efi-Signature` containing
  the lowercase hex HMAC-SHA256 of the unmodified body. Strip both incoming
  headers before setting trusted values.
- Do not log query strings on these routes because they carry webhook secrets.
- Use different secrets for Cobranças and Pix and rotate them independently.

Coolify/Traefik does not derive `X-Efi-Signature` from
`EFI_PIX_WEBHOOK_HMAC_KEY` automatically. Do not enable Efí until a trusted
ingress or middleware in front of this route performs the mTLS validation,
body-preserving HMAC calculation and header stripping described above.

The `ignorar` query parameter prevents Efí from appending `/rec` or `/cobr` to
the registered callback. Efí notification history IDs and deterministic Pix
event IDs provide application-level deduplication.

## 5. Homologation checklist

- Card during trial: subscription created with deferred first charge.
- Card after trial: subscription and first charge are created. Efí homologation
  currently leaves recurring-card subscriptions in `waiting` and does not offer
  a payment simulation that advances them to `paid`; do not wait for a paid
  webhook from this sandbox flow. Validate paid-webhook handling with automated
  integration tests and repeat the complete provider flow during the controlled
  production smoke test.
- Pix during trial: Jornada 2 authorization, with recurrence starting at trial end.
- Pix after trial: Jornada 3 immediate charge plus recurrence authorization.
- Pix approval, payment, renewal, cancellation and terminal retry exhaustion.
- Duplicate and out-of-order webhook delivery.
- Repeated checkout with the same idempotency key returns the persisted action.
- The hourly scheduler creates one future Pix charge for a subscription/date.
- Existing Asaas and Abacate Pay subscriptions still change plan and cancel at
  their original provider.

## 6. Activation and rollback

1. Deploy with Efí configured and `BILLING_PROVIDER` unchanged.
2. Run production smoke tests with an internal account.
3. Set `BILLING_PROVIDER=EFI` to route only new checkouts to Efí.
4. Monitor provider HTTP failures, OAuth refreshes, webhook delay, ignored events,
   pending authorizations and Pix subscriptions without an upcoming charge.

Rollback new checkouts by restoring `BILLING_PROVIDER=ASAAS` or
`BILLING_PROVIDER=ABACATEPAY`. Do not delete Efí configuration or webhooks:
subscriptions already created at Efí continue to be routed there.

## Official references

- [Efí API Cobranças credentials and production URLs](https://dev.efipay.com.br/docs/api-cobrancas/credenciais/)
- [Efí API Pix credentials, certificate and production URLs](https://dev.efipay.com.br/docs/api-pix/credenciais/)
- [Efí card tokenization and account identifier](https://dev.efipay.com.br/docs/api-cobrancas/cartao/)
- [Efí Pix, Pix Automático and recurring-charge webhooks](https://dev.efipay.com.br/docs/api-pix/webhooks/)
- [Coolify environment-variable Developer View](https://coolify.io/docs/knowledge-base/environment-variables)
- [Coolify persistent storage and bind mounts](https://coolify.io/docs/knowledge-base/persistent-storage)
