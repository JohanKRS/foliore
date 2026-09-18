# Gmail delivery verification — 2026-09-18

Branch: `codex/gmail-send`, based on `1c4addf`.

## Automated validation

- `:androidApp:testDebugUnitTest`: **180 tests, 0 failures, 0 errors, 0 skipped** (11 new Gmail/provider tests).
- `:androidApp:assembleDebug`, `:androidApp:lintDebug`, `:androidApp:assembleRelease`: passed. Native conversion tests used `/tmp/foliore-native-build`.
- Signed release built using the existing local signing and billing configuration; temporary configuration links removed afterward. APK verification passed, v2 signature, non-debuggable, four ABIs.
- `git diff --check`: passed.

New tests verify exact attachment/body round trips from the transmitted MIME, Unicode subject/filename handling, CRLF/header injection rejection, streaming mode, timeout and redirect settings, oversize rejection before network access, error classifications, no duplicate attempts on lost responses, and explicit provider selection without silent fallback.

## Runtime UI

Disposable AVD `FolioreGmailQA`, Android 36, Google APIs x86_64, 1080×2400. User's existing AVD was not used.

Verified settings entry, compact Gmail form, SMTP expansion and access to all fields/actions, destination save, English/Portuguese text, light/dark themes, font scale 1.3, rotation and scrolling in landscape. Connect Gmail opened Google's account flow; cancel returned to the unchanged form without falsely reporting a connection. Crash buffer remained empty.

Screenshots/XML included:

- `gmail-dialog`: English, light, new connection.
- `gmail-dark`: Portuguese, dark, saved synthetic destination.
- `gmail-landscape-scrolled`: Portuguese, landscape, font scale 1.3, Gmail controls reachable after scrolling.

No Google account was added to the disposable emulator. **Full consent, token renewal/revocation, real Gmail delivery and Kindle ingestion are still pending manual device testing.** The owner chose to test the preview later. No real email was sent during this work.

## Cloud configuration

See [Gmail setup](../../../GMAIL.md) for enabled API, send-only requested scope, matching Android release certificate, test audience, missing public verification and privacy deployment, and the conservative quota guard.

The Console confirmed both current/previous per-project per-minute Gmail quotas at 40,000 units. There is no billing account associated and no notification channel configured; no email alert policy was created. Scope/branding saves and quota changes were visibly confirmed through the Console.

## Delivered preview

`Foliore-0.1.0-gmail-preview-2026-09-18.apk` — version 0.1.0 (2).

SHA-256: `f419c1f137f1efa1742028cdc9d352756bee1ba4ce5affd85bba723395d3ffc7`.

Signer SHA-256: `0314c5c1f4540c1cf189a51cc17c39a31e9d9b943a1651ba77ca73776bff46a4`.

## Approved-sender reminder follow-up

The Kindle dialog now always shows a small approved-sender reminder and an inline Amazon settings link, including before connecting Gmail and when using SMTP. All eight translations were updated. Amazon documents this requirement in [Add an Email Address to Receive Documents in Your Kindle Library](https://digprjsurvey.amazon.co.uk/csad/help/node/GX9XLEVV8G4DB28H); existing approved addresses can remain.

`:androidApp:assembleDebug`, `:androidApp:lintDebug` and the signed release build passed. English and Portuguese layouts were checked on a disposable Android 36 emulator (`FolioreReminderQA`). The native link opened Chrome with `https://www.amazon.com/mycd`, confirmed by the activity log. No authenticated Amazon settings were changed or verified. The crash buffer remained empty. `kindle-reminder-pt.png` and its XML capture show the final Portuguese layout.

Updated signed preview: `Foliore-0.1.0-gmail-preview-2026-09-18-v2.apk`.

SHA-256: `8dbabfdedea824dd10ac7d9f29564e886c352ade1255784e953832eca770116a`.
