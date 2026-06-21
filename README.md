# TestChimp RUM — Android

Kotlin/Android library for emitting structured RUM events to [TestChimp](https://testchimp.io), aligned with:

- [@testchimp/rum-js](https://www.npmjs.com/package/@testchimp/rum-js) (browser)
- [testchimp-rum-ios](https://github.com/testchimphq/testchimp-rum-ios) (Swift)

Same HTTP paths, headers (`Project-Id`, `TestChimp-Api-Key`, optional `ci-test-info`), validation rules, and Mobilewright automation URL contract.

## Requirements

- **minSdk 24**, **compileSdk 34**
- Kotlin **1.9**+, Java **17** (AGP **8.2**)

## Add to your app

### Option A — JitPack (recommended for public installs)

[JitPack](https://jitpack.io/) builds this repo on each **git tag** and serves Maven artifacts **without** Maven Central or GitHub Packages credentials for consumers.

1. Add the repository in **`settings.gradle.kts`** (or `settings.gradle`):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

2. Depend on the **library module** (this is a multi-module Gradle project; artifact id = module folder name):

```kotlin
dependencies {
    implementation("com.github.testchimphq:testchimp-rum-android:0.1.0")
}
```

Use the **exact version** JitPack lists for your **git tag** (e.g. `0.1.0`), or a snapshot such as **`master-SNAPSHOT`** while testing—see [JitPack — testchimp-rum-android](https://jitpack.io/#testchimphq/testchimp-rum-android).

If the site shows a slightly different line after the build completes, prefer **their** copy-paste.

[![](https://jitpack.io/v/testchimphq/testchimp-rum-android.svg)](https://jitpack.io/#testchimphq/testchimp-rum-android)

### Option B — Git submodule / local module

1. Clone or submodule this repo next to your app.
2. In **settings.gradle.kts**:

```kotlin
include(":testchimp-rum")
project(":testchimp-rum").projectDir = file("../testchimp-rum-android/testchimp-rum")
```

3. In **app/build.gradle.kts**:

```kotlin
dependencies {
    implementation(project(":testchimp-rum"))
}
```

### Option C — Maven Central (optional)

When `io.testchimp:rum-android` is published to Central, consumers use Maven Central only — see `libraryVersion` / `groupId` in `testchimp-rum/build.gradle.kts`.

### Option D — Publish to Maven Local (development)

From this repo (with `ANDROID_HOME` or `sdk.dir` in `local.properties`):

```bash
./gradlew :testchimp-rum:publishToMavenLocal
```

In the consumer:

```kotlin
repositories { mavenLocal() }
dependencies { implementation("io.testchimp:rum-android:0.1.0") }
```

## Usage

Initialize once (typically in `Application.onCreate`), then emit events from UI code.

```kotlin
import io.testchimp.rum.TestChimpEmitInput
import io.testchimp.rum.TestChimpRum
import io.testchimp.rum.TestChimpRumConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TestChimpRum.initialize(
            this,
            TestChimpRumConfig(
                projectId = BuildConfig.TC_PROJECT_ID,
                apiKey = BuildConfig.TC_API_KEY,
                environment = BuildConfig.BUILD_TYPE,
                release = BuildConfig.VERSION_NAME,
            ),
        )
    }
}

// Elsewhere
TestChimpRum.emit(TestChimpEmitInput(title = "button_tap", metadata = mapOf("screen" to "Home")))
```

### Advanced options

Pass `options = TestChimpRumConfig.Options(...)` for the same knobs as the JS SDK (`captureEnabled`, `maxEventsPerSession`, `eventSendIntervalMillis`, `testchimpEndpoint`, `automationContextTtlSeconds`, etc.).

### TrueCoverage (Mobilewright)

1. **CI / runner:** set `TESTCHIMP_PROJECT_TYPE=android` and use `@testchimp/playwright` `installTestChimp` so `device.openUrl` pushes CI JSON into the app on every test (see that package’s README).

2. **Deep link:** declare an intent filter so `testchimp-rum://truecoverage/...` opens your app (often your launcher activity or a dedicated transparent activity).

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="testchimp-rum"
        android:host="truecoverage"
        android:pathPrefix="/v1" />
</intent-filter>
```

3. **Deliver to the SDK** in `onCreate` / `onNewIntent`:

```kotlin
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    TestChimpRum.handleAutomationIntent(intent)
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    TestChimpRum.handleAutomationIntent(intent)
}
```

**URLs (defaults, aligned with iOS + Playwright):**

- Set context: `testchimp-rum://truecoverage/v1/set?p=<base64url(JSON)>`
- Clear: `testchimp-rum://truecoverage/v1/clear`

Optional env overrides on the runner: `TESTCHIMP_RUM_AUTOMATION_SET_PREFIX`, `TESTCHIMP_RUM_AUTOMATION_CLEAR_URL`.

## How this helps in real testing scenarios

`TestChimpRum` on Android feeds **TrueCoverage**: structured navigation and custom events from **production** compared to **Mobilewright** CI runs (via [@testchimp/playwright](https://www.npmjs.com/package/@testchimp/playwright) + `testchimp-rum://truecoverage/...` deep links). Instrument flows with metadata dimensions (screen, auth state, payment method, locale)—then see which slices real users hit but your suite never exercises.

Start here: [TrueCoverage introduction](https://docs.testchimp.io/truecoverage/intro) · [How TrueCoverage works](https://docs.testchimp.io/truecoverage/how-it-works) · [Instrumentation (Android)](https://docs.testchimp.io/truecoverage/instrumentation) · [Mobile testing with Mobilewright](https://docs.testchimp.io/smart-tests/mobile-testing)

### Native mobile flows worth instrumenting

| Scenario | Testing guide |
|----------|----------------|
| Google Pay & wallet checkout | [Wallet payment flows](https://docs.testchimp.io/guides/flows/testing-wallet-payments) |
| OAuth & social login (Custom Tabs) | [OAuth social login](https://docs.testchimp.io/guides/auth/testing-oauth-social-login) |
| Firebase Auth on Android | [Firebase authentication](https://docs.testchimp.io/guides/auth/testing-firebase-auth) |
| MFA / biometric unlock | [MFA & 2FA flows](https://docs.testchimp.io/guides/auth/testing-mfa-2fa) |
| Push notification opt-in | [Push notifications](https://docs.testchimp.io/guides/integrations/testing-push-notifications) |
| Onboarding & activation funnels | [SaaS onboarding](https://docs.testchimp.io/guides/verticals/testing-saas-onboarding-flows) |
| Subscription & trial conversion | [Subscription billing](https://docs.testchimp.io/guides/flows/testing-subscriptions-billing) · [Trial to paid](https://docs.testchimp.io/guides/flows/testing-trial-to-paid) |
| Localization & regional builds | [Localization / i18n](https://docs.testchimp.io/guides/patterns/testing-localization-i18n) |
| GDPR consent & data export | [GDPR privacy flows](https://docs.testchimp.io/guides/patterns/testing-gdpr-privacy) |
| Fintech transfers & KYC | [Fintech web apps](https://docs.testchimp.io/guides/verticals/testing-fintech-web-apps) |
| Healthcare patient apps | [Healthcare patient portals](https://docs.testchimp.io/guides/verticals/testing-healthcare-portals) |
| Insurance quote wizards | [Insurance quote flows](https://docs.testchimp.io/guides/verticals/testing-insurance-quotes) |

### Payments, auth & integrations (cross-platform playbooks)

| Scenario | Testing guide |
|----------|----------------|
| Stripe (web + mobile web checkout) | [Stripe payments](https://docs.testchimp.io/guides/flows/testing-stripe-payments) |
| Stripe webhooks | [Stripe webhooks in CI](https://docs.testchimp.io/guides/integrations/testing-stripe-webhooks) |
| Magic links & passwordless | [Magic link testing](https://docs.testchimp.io/guides/auth/testing-magic-link-passwordless) |
| Auth0 / Okta SSO | [Auth0 & Okta SSO](https://docs.testchimp.io/guides/auth/testing-auth0-okta-sso) |
| SMS / OTP verification | [SMS & OTP testing](https://docs.testchimp.io/guides/integrations/testing-sms-otp) |
| Transactional email triggers | [Transactional email](https://docs.testchimp.io/guides/integrations/testing-transactional-email) |
| File upload & document capture | [File uploads](https://docs.testchimp.io/guides/integrations/testing-file-uploads) |
| PDF receipts & statements | [PDF downloads](https://docs.testchimp.io/guides/integrations/testing-pdf-downloads) |

### UI patterns & AI-assisted mobile

| Scenario | Testing guide |
|----------|----------------|
| Form validation & error states | [Form validation](https://docs.testchimp.io/guides/patterns/testing-form-validation) |
| Search & filters | [Search & filters](https://docs.testchimp.io/guides/patterns/testing-search-filters) |
| Calendar & booking | [Calendar scheduling](https://docs.testchimp.io/guides/patterns/testing-calendar-scheduling) |
| Date, time & timezones | [Date, time & timezones](https://docs.testchimp.io/guides/patterns/testing-date-time-timezones) |
| Conversational / chat UI | [Conversational UI](https://docs.testchimp.io/guides/ai/testing-conversational-ui) |
| AI agent tool flows | [AI agent workflows](https://docs.testchimp.io/guides/ai/testing-ai-agent-workflows) |

### Close the loop: Mobilewright + TestChimp

1. Instrument prod with `TestChimpRum` and review gaps in the [TrueCoverage dashboard](https://docs.testchimp.io/truecoverage/dashboard)
2. Author **Mobilewright** SmartTests — [Introduction to SmartTests](https://docs.testchimp.io/smart-tests/intro)
3. Link tests to markdown scenarios — [Requirement traceability](https://docs.testchimp.io/test-planning/requirement-traceability)
4. Expand coverage from gaps — [/testchimp evolve](https://docs.testchimp.io/qa-autopilot-claude/evolve)

Browse all guides: [Testing guides hub](https://docs.testchimp.io/guides/intro) · [Business flows](https://docs.testchimp.io/guides/flows/intro) · [Auth & identity](https://docs.testchimp.io/guides/auth/intro) · [Industry verticals](https://docs.testchimp.io/guides/verticals/intro)

## Building the AAR locally

```bash
export ANDROID_HOME=/path/to/Android/sdk   # or create local.properties with sdk.dir=...
./gradlew :testchimp-rum:assembleRelease
```

Output: `testchimp-rum/build/outputs/aar/testchimp-rum-release.aar`.

## Releasing (maintainers — JitPack)

1. Bump **`val libraryVersion`** in **`testchimp-rum/build.gradle.kts`** (used in the published POM).
2. Commit and push to your default branch.
3. Run **`./scripts/release-jitpack.sh`** — creates and pushes a **git tag** matching that version (or tag manually with the same name).
4. Wait for a green build on [JitPack](https://jitpack.io/#testchimphq/testchimp-rum-android), then use the dependency coordinate from the site if it differs.

`jitpack.yml` pins **JDK 17** and runs `assembleRelease` + `publishReleasePublicationToMavenLocal` for reproducible builds.

## License

MIT — see [LICENSE](LICENSE).
