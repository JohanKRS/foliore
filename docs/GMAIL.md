# Gmail delivery

Foliore sends a prepared EPUB directly from the Google account selected by the user. The Gmail app does not need to open. The account used for delivery is independent of the account used for Drive synchronization. SMTP remains available under “Set up another email (SMTP)”; existing SMTP configurations remain selected after upgrading.

## Android flow

1. Settings → Send to Kindle → connect Gmail. Google Play services opens its account picker and requests `https://www.googleapis.com/auth/gmail.send` for that specific account.
2. Save the destination Kindle email and add the sending address to the approved personal-document senders in Amazon's settings. The dialog always includes a small reminder and an inline link to Amazon's Manage Your Content and Devices page (`/mycd`), for Gmail and SMTP alike. The storefront follows the device region rather than the selected app language. Existing approved senders do not need to be removed.
3. Send from book details or the metadata editor. Google Play services supplies an access token for the selected account, requesting authorization again when required.

Only the account address and chosen delivery provider are stored in Foliore preferences. Access tokens are held for the current operation; their cache is owned by Google Play services. There is no OAuth client secret in the app, no Foliore mail server and no inbox-reading scope. Gmail settings and tokens are not synchronized with the library.

Disconnecting Gmail forgets the local connection without silently switching to saved SMTP. It deliberately does not call Google's `revokeAccess`: that revokes all authorizations for this app/project, including Drive. Users can revoke Foliore in their Google Account permissions, which can require reconnecting Drive too.

An authorization result resumes only the pending operation in the current ViewModel. It consumes that operation before sending, checks the account/provider/destination again, and does not replay a send after process death. Canceling the account picker or consent does not replace an existing connection.

## Delivery and failure behavior

The sender streams an RFC 822 multipart MIME message to the Gmail API media-upload endpoint, using HTTPS, bounded buffers and a 25,000,000-byte EPUB attachment cap. It does not assemble a second full-size base64/JSON copy of the EPUB. UTF-8 text, attachment names and folded encoded subject headers are supported.

Requests disable redirects and have connection/read timeouts. A `401` clears the cached token for a later explicit attempt. Quota errors and oversize attachments have dedicated notices. A lost response after upload, an invalid success response or a server error is reported as an **unconfirmed send**: check Gmail Sent before trying again. Foliore never automatically repeats the upload, because the first attempt might already have delivered the message.

## Google Cloud configuration — 2026-09-18

Verified/configured through the Cloud Console in project `foliore`:

- Gmail API enabled.
- OAuth requested scope `gmail.send` added; existing `drive.appdata` retained. No restricted Gmail scopes added.
- Existing Android release client matches package `com.foliore.android` and certificate SHA-1 `CA:34:04:EF:1A:A6:53:40:01:FE:CF:70:8D:EF:37:52:DC:3C:65:D5`.
- Existing app name/support/developer contacts retained. Homepage `https://foliore.johankrs.com/`, privacy URL `https://foliore.johankrs.com/privacy/`, and consent-screen domain `johankrs.com` saved.
- OAuth remains **External / Testing**, with the owner's existing test account. No production publication or verification submission was performed.
- Console reports **no Cloud billing account associated**. Billing was not enabled.
- Both `Units per minute` and `Previous quota: Units per minute` reduced from 1,200,000 to **40,000**, with success confirmations. Per-user quotas were unchanged.

### Cost guard

Google currently documents standard Gmail API use at no additional cost, a daily billing threshold of 80,000,000 quota units per project, and `messages.send` costing 100 units. The future billing model is announced for later in 2026 with at least 90 days' notice; this is not a promise that the API will stay free indefinitely.

The Console currently exposes adjustable per-minute limits, not an adjustable daily threshold. The 40,000-unit/minute cap provides a conservative bound of approximately 57,600,000 units per 24 hours of sustained use (576,000 sends if used only for sending). This is below the published 80,000,000-unit threshold with a margin for rate-window boundaries. It limits bursts too, even when daily usage is low. Google rejects requests above the enforced rate; the app shows a limit notice.

This is a rate-based guard against the currently published threshold, **not a contractual billing guarantee or an exact daily spending switch**. Recheck pricing and quotas before enabling billing, raising the limit, or adding API methods. Quota models and enforcement can change. The Cloud project has no notification channels configured; no alert policy was created without a destination. Budget alerts alone would not block usage.

## Before public distribution

- Test account selection, granting/denying send consent, reconnection, and one real delivery on a Google-enabled device. Verify the selected sender, recipient, EPUB integrity and Sent folder; do not treat an HTTP success as proof of Kindle ingestion.
- Current test mode permits only explicitly listed test users. Sensitive-scope testing authorizations have the limitations documented by Google, including expiration; do not distribute this as a production-ready public connection.
- Publish the updated privacy disclosure. The app asset and repository's public privacy document are updated; the independently hosted site still needs deployment (its live policy was checked and still described SMTP only).
- Complete Google's sensitive-scope verification for `gmail.send`, including the real authorization/send demonstration and any domain verification requirements. Do not request `gmail.readonly`, `gmail.compose`, `gmail.modify` or full mail access.
- For Play-distributed builds, register/verify the **Play app-signing** certificate too if it differs from the local release/upload certificate. The existing client was verified for the local signed APK only.

## Official references

- [Usage limits and pricing](https://developers.google.com/workspace/gmail/api/reference/quota)
- [Workspace API billing rollout](https://developers.google.com/workspace/tools-safety)
- [Gmail scopes and verification](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [Android authorization and token handling](https://developer.android.com/identity/authorization)
- [Sending MIME messages](https://developers.google.com/workspace/gmail/api/guides/sending)
- [Media uploads](https://developers.google.com/workspace/gmail/api/guides/uploads)
