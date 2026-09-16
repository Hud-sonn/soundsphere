# Payment Integration Plan — OPay Hosted Checkout via WebView

Date: 2026-09-14
Status: Proposal (NOT implemented)
Why: Propose the payment flow for Soundsphere Premium using OPay's hosted checkout page via WebView — same pattern as Spotify, YouTube Music, and other major music apps.

## Answer: Yes, hosted webview is possible and recommended

OPay's Cashier Create API returns a `cashierUrl` — a fully hosted checkout page that runs in a browser. The app loads this URL in a WebView. This is the **exact same pattern** used by Spotify, YouTube Music, Netflix, and other subscription apps.

**Why this is better than the native SDK:**
- No AAR dependency to maintain
- OPay handles all checkout UI updates automatically
- Works across Android/iOS with one integration
- Credentials stay server-side (public key only for create call)
- Checkout experience is consistent and PCI-compliant

## Payment Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        IN-APP FLOW                              │
│                                                                 │
│  1. User taps "Upgrade to Premium"                              │
│  2. App shows plan selection screen (UI)                        │
│  3. User selects plan (monthly ₦1,500 / yearly ₦12,000)        │
│  4. App calls POST /billing/checkout                            │
│  5. Backend creates OPay order → gets cashierUrl                │
│  6. Backend returns cashierUrl to app                           │
│  7. App opens WebView with cashierUrl                           │
│  8. User completes payment on OPay's hosted page                │
│  9. OPay redirects to returnUrl (app deep link)                 │
│ 10. App receives redirect, calls GET /billing/status/{ref}     │
│ 11. Backend verifies payment, updates subscriptions table      │
│ 12. App shows success screen, tier updated                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     WEBHOOK (server-to-server)                  │
│                                                                 │
│  OPay sends POST /billing/webhook with payment result          │
│  Backend verifies HMAC signature                               │
│  Backend updates subscriptions table (is_pro=true, expires_at) │
│  (Webhook is the source of truth — WebView redirect is UX)     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Backend Endpoints (new: `routers/billing.py` router)

### `POST /billing/checkout`
Creates an OPay order and returns the hosted checkout URL.

```python
# Request
{
    "plan": "monthly" | "yearly",
    "email": "user@example.com"
}

# Response
{
    "cashierUrl": "https://cashier.opaycheckout.com/...",
    "reference": "SS-PREMIUM-abc123",
    "expires_in": 1800
}
```

**Backend logic:**
1. Authenticate user (JWT)
2. Check no active subscription already
3. Generate unique reference (`SS-PREMIUM-{uuid}`)
4. Call OPay Cashier Create API with:
   - `amount.total`: 150000 (₦1,500 monthly) or 1200000 (₦12,000 yearly) in kobo
   - `currency`: "NGN"
   - `country`: "NG"
   - `returnUrl`: `soundsphere://billing/return?ref={reference}`
   - `callbackUrl`: `https://api.soundsphere.name.ng/billing/webhook`
   - `cancelUrl`: `soundsphere://billing/cancel`
   - `product.name`: "Soundsphere Premium (Monthly/Yearly)"
   - `userInfo`: user ID, email
5. Store order in `payment_orders` table (reference, user_id, amount, status, created_at)
6. Return `cashierUrl` to app

### `GET /billing/status/{reference}`
Check payment status after WebView redirect.

```python
# Response
{
    "reference": "SS-PREMIUM-abc123",
    "status": "SUCCESS" | "PENDING" | "FAIL" | "CLOSE",
    "expires_at": "2026-10-14T00:00:00Z"
}
```

### `POST /billing/webhook`
OPay webhook receiver (server-to-server, source of truth).

```python
# Verifies HMAC-SHA512 signature
# Updates subscriptions table:
#   is_pro = true
#   expires_at = now() + plan_duration
#   tier = 'premium'
# Updates payment_orders table:
#   status = 'SUCCESS'
```

### `GET /billing/status`
Get current user's subscription status.

```python
# Response
{
    "tier": "premium",
    "is_pro": true,
    "expires_at": "2026-10-14T00:00:00Z",
    "plan": "monthly"
}
```

## In-App UI (like Spotify/YouTube)

### What we own vs what OPay owns

| Screen | Owner | Notes |
|--------|-------|-------|
| Plan selection (features + pricing) | **Our UI** | Full design control |
| Pre-checkout loading | **Our UI** | Spinner while backend creates order |
| OPay hosted checkout | **OPay** | Card/bank/wallet input — can't modify |
| Success confirmation | **Our UI** | Shows tier, expiry, receipt |
| Subscription management | **Our UI** | View plan, cancel, renew |

**OPay's page only appears for the actual payment input.** The user sees our branded experience until they tap "Pay", then OPay handles the payment, then they return to our success screen.

### Payment Screen (`PaymentScreen.kt`)

**Layout (matching Spotify/YouTube Premium pages):**

```
┌─────────────────────────────────────────┐
│  ← Back                    Premium      │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │      🎵  SOUNDSPHERE PREMIUM    │   │
│  │                                 │   │
│  │  ✓ Unlimited playlists          │   │
│  │  ✓ Unlimited liked songs        │   │
│  │  ✓ 10x AI playlist generations  │   │
│  │  ✓ 10 collaborative Blends      │   │
│  │  ✓ Unlimited listening history   │   │
│  │  ✓ Priority sync                │   │
│  │  ✓ Ad-free experience           │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌──────────────┐ ┌──────────────┐     │
│  │   MONTHLY    │ │   YEARLY     │     │
│  │   ₦1,500     │ │   ₦12,000    │     │
│  │   /month     │ │   /year      │     │
│  │              │ │  Save 33%    │     │
│  └──────────────┘ └──────────────┘     │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │     Subscribe Now               │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Secured by OPay • Cancel anytime       │
│                                         │
└─────────────────────────────────────────┘
```

**Components:**
- `PaymentScreen.kt` — main screen with plan cards + subscribe button
- `PaymentWebView.kt` — WebView wrapper for OPay checkout
- `PaymentSuccessScreen.kt` — success confirmation after payment
- `PaymentViewModel.kt` — handles checkout creation + status polling

### WebView Integration

```kotlin
// PaymentWebView.kt
@Composable
fun PaymentWebView(
    cashierUrl: String,
    onReturn: (reference: String) -> Unit,
    onCancel: () -> Unit,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith("soundsphere://billing/return")) {
                            val ref = Uri.parse(url).getQueryParameter("ref")
                            onReturn(ref ?: "")
                            return true
                        }
                        if (url.startsWith("soundsphere://billing/cancel")) {
                            onCancel()
                            return true
                        }
                        return false
                    }
                }
                loadUrl(cashierUrl)
            }
        }
    )
}
```

### Navigation

```
Settings
  └→ PaymentScreen (plan selection)
      └→ PaymentWebView (OPay checkout)
          ├→ PaymentSuccessScreen (on success)
          └→ Back to Settings (on cancel)
```

### Deep Link Handling

`AndroidManifest.xml`:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="soundsphere" android:host="billing" />
</intent-filter>
```

## New Table: `payment_orders`

```sql
CREATE TABLE payment_orders (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users(id),
    reference   text NOT NULL UNIQUE,
    plan        text NOT NULL CHECK (plan IN ('monthly', 'yearly')),
    amount      integer NOT NULL,  -- in kobo
    currency    text NOT NULL DEFAULT 'NGN',
    status      text NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'success', 'fail', 'close')),
    opay_order_no text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- RLS: users can only read their own orders
ALTER TABLE payment_orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users read own orders" ON payment_orders
    FOR SELECT USING (auth.uid() = user_id);

-- No authenticated INSERT/UPDATE — only service_role via webhook
```

## Implementation Phases

### Phase 1: Backend (1-2 days)
1. Create `payment_orders` table via MCP migration
2. Add billing router endpoints (`/checkout`, `/status`, `/webhook`)
3. Implement OPay Cashier Create API call (server-side, HMAC-SHA512 signed)
4. Implement webhook receiver (verify signature, update `subscriptions` table)
5. Add `/billing/status` endpoint for app to check tier

### Phase 2: App UI (2-3 days)
1. Create `PaymentScreen.kt` (plan cards, subscribe button)
2. Create `PaymentWebView.kt` (WebView wrapper for OPay checkout)
3. Create `PaymentSuccessScreen.kt` (confirmation screen)
4. Add navigation routes (`payment`, `payment/webview`, `payment/success`)
5. Handle deep link (`soundsphere://billing/return`)
6. Update settings screen with "Upgrade to Premium" entry point

### Phase 3: Polish (1 day)
1. Loading states, error handling, retry logic
2. Subscription management screen (view plan, cancel, renew)
3. Receipt/transaction history
4. Test with OPay sandbox cards

## Payment Methods Available (Nigeria)

| Method | Value | Notes |
|--------|-------|-------|
| Bank Card | `BankCard` | Visa, Mastercard, Verve (3DS) |
| Bank Transfer | `BankTransfer` | Temp account number generated |
| USSD | `BankUSSD` | `*737#` etc. |
| Bank Account | `BankAccount` | Direct bank debit |
| OPay Wallet | `OPayWalletNg` | QR or balance |
| Reference Code | `ReferenceCode` | Pay at OPay agent/POS |

**Recommendation:** Show all methods (leave `payMethod` blank in API call) — let the user choose.

## Security

- OPay credentials (public key, merchant ID) stay server-side only
- Webhook verified via HMAC-SHA512 signature
- `payment_orders` table: RLS enabled, users read own only
- `subscriptions` table: only `service_role` writes (webhook)
- WebView loads OPay's hosted page — no card data touches our app
- Return URL is a deep link, not a callback to our server (prevents MITM)

## Share URL Hiding — Backend URL Exposure

### Current problem

Share links expose the backend infrastructure:
- `https://api.soundsphere.name.ng/s/{songId}` — song shares
- `https://api.soundsphere.name.ng/p/{token}` — playlist shares

This reveals the backend domain to users, which is a security concern (attack surface, infrastructure exposure).

### Proposed solution

Use a public-facing short domain for all user-facing share links. The actual `api.soundsphere.name.ng` never appears in shared URLs.

| Current (exposed) | Proposed (hidden) |
|---|---|
| `api.soundsphere.name.ng/s/{id}` | `sndsph.re/s/{id}` |
| `api.soundsphere.name.ng/p/{token}` | `sndsph.re/p/{token}` |
| `soundsphere-auth.onrender.com` | never user-facing |

### Implementation options

**Option A: Cloudflare Worker (recommended)**
- Register `sndsph.re` (or use `soundsphere.name.ng` subdomain)
- Worker at `sndsph.re/s/{id}` → 302 redirect to `soundsphere://song/{id}` (app open) or web preview
- Worker at `sndsph.re/p/{token}` → 302 redirect to `soundsphere://p/{token}` (app open) or web preview
- Pros: zero backend changes, fast, free tier, DDoS protection
- Cons: one more service to manage

**Option B: Backend reverse proxy**
- Add routes to `api.soundsphere.name.ng`: `/s/{id}` → redirect to deep link, `/p/{token}` → redirect to deep link
- Use a separate domain (e.g. `soundsphere.name.ng`) that proxies to the backend
- Pros: no extra service
- Cons: still exposes backend domain if DNS leaks

**Option C: Custom scheme only**
- Share only `soundsphere://p/{token}` and `soundsphere://song/{id}`
- No web fallback — non-app users see nothing
- Pros: simplest, zero exposure
- Cons: bad UX for users without the app

### Recommendation

**Option A (Cloudflare Worker)** — cleanest separation. The short domain (`sndsph.re`) is public, the backend (`api.soundsphere.name.ng`) stays internal. The worker just does redirects, no logic.

### Files to change

- `soundsphere_strings.xml` — update share URL templates from `api.soundsphere.name.ng` to `sndsph.re`
- `PlaylistMenu.kt`, `PlaylistScreenMenus.kt`, `SongMenu.kt`, `QueueMenu.kt`, `Player.kt` — use new share domain
- `AndroidManifest.xml` — add intent filter for `sndsph.re` domain
- `MainActivity.kt` — handle `sndsph.re` deep links alongside `soundsphere://`
