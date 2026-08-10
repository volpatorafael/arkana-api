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

Create monthly and annual plans in API Cobranças with the same amount and
interval as `billing_plan_prices`. Register each returned plan ID without
changing existing mappings:

```sql
insert into billing_provider_plan_mappings
  (id, plan_price_id, provider, provider_product_id)
values
  (gen_random_uuid(), '<monthly-price-uuid>', 'EFI', '<efi-monthly-plan-id>'),
  (gen_random_uuid(), '<annual-price-uuid>', 'EFI', '<efi-annual-plan-id>');
```

Pix Automático uses the Arkana plan amount directly and therefore does not need
an external plan ID. The mappings above are required for card subscriptions.

## 3. Runtime configuration

Configure the backend secrets (never with a `VITE_` prefix):

```text
EFI_CLIENT_ID
EFI_CLIENT_SECRET
EFI_CHARGES_URL
EFI_PIX_URL
EFI_PIX_KEY
EFI_CERTIFICATE_PATH
EFI_CERTIFICATE_PASSWORD
EFI_CHARGES_WEBHOOK_SECRET
EFI_PIX_WEBHOOK_SECRET
EFI_PIX_WEBHOOK_HMAC_KEY
EFI_PIX_REQUIRE_MTLS=true
ARKANA_WEBHOOK_BASE_URL
```

The web application receives only the publishable tokenization values:

```text
VITE_EFI_ACCOUNT_ID
VITE_EFI_ENVIRONMENT=sandbox|production
```

Keep `BILLING_PROVIDER=ASAAS` (or `ABACATEPAY`) during the initial deployment.

## 4. Webhooks and ingress

- API Cobranças notification URL:
  `/v1/webhook/payment/efi/charges?webhookSecret=<dedicated-secret>`.
- Pix Automático webhook URL:
  `/v1/webhook/payment/efi/pix?webhookSecret=<dedicated-secret>&ignorar=`.
- Require a verified client certificate on the Pix ingress route. The reverse
  proxy must reject requests that did not complete mTLS, set
  `X-Client-Certificate-Verified: SUCCESS`, and add `X-Efi-Signature` containing
  the lowercase hex HMAC-SHA256 of the unmodified body. Strip both incoming
  headers before setting trusted values.
- Do not log query strings on these routes because they carry webhook secrets.
- Use different secrets for Cobranças and Pix and rotate them independently.

The `ignorar` query parameter prevents Efí from appending `/rec` or `/cobr` to
the registered callback. Efí notification history IDs and deterministic Pix
event IDs provide application-level deduplication.

## 5. Homologation checklist

- Card during trial: subscription created with deferred first charge.
- Card after trial: immediate first charge and webhook activation.
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
