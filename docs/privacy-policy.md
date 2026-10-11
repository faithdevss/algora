# AlgorAI — Privacy Policy

*Effective October 11, 2026*

AlgorAI ("the app") is a data structures, algorithms, and AI/ML learning app for Android and
iOS, published by Saimum Islam. This policy explains what data the app touches and why. If a category of data
isn't listed below, the app does not collect it.

## No account, no server

AlgorAI has no sign-up, no login, and no backend server of its own. Your progress — completed
topics, quiz history, flashcard review schedule, streak, and settings such as your chosen theme and
app icon — is stored in local app storage on your device (DataStore on Android, the app's own
storage on iOS). Uninstalling the app deletes it. If your device's own backup is enabled (Android
backup, or iCloud on iOS), that local data may be included in your normal device backup, same as
any other app's — AlgorAI does not manage or control that separately.

**In short:** your progress stays on your device. On iOS, nothing about how you use the app is sent
anywhere. On Android, a small set of usage events and crash reports, not tied to your name or any account, go to Google Firebase
so we can see which parts of the app help people keep learning and fix crashes — see
[Usage analytics and crash reports](#usage-analytics-and-crash-reports-android-only) below.

## Permissions

Notifications (`POST_NOTIFICATIONS` on Android, the notification permission on iOS) — only for
the optional study reminders, toggleable in Settings. Scheduled locally on-device; nothing
about it is sent anywhere.

## Third-party services

AlgorAI uses a small number of platform services to run ads (Android only), measure usage and
crashes (Android only), process payments, and show the native "rate this app" prompt. Each is the
platform owner's own SDK under its own terms.

### Plans and payment

Most lessons, labs, quizzes and interview rounds need Premium. Premium is either a **monthly
subscription** or a **one-time lifetime purchase**; both unlock everything and remove ads. There is
no free trial. The subscription renews automatically each month until you cancel it in your store
account (Google Play subscriptions on Android, Apple ID subscriptions on iOS), and cancelling stops
the next renewal. Prices vary by country and are shown in your local currency before you buy.
Payment is processed entirely by Google Play or Apple, never by AlgorAI. AlgorAI never sees or
stores card details or your payment account — only the store's confirmation that a purchase or
active subscription exists.

### Android

- **Google Play Billing** — handles the monthly subscription and the lifetime purchase.
- **Google AdMob** — ads, in two places and nowhere else. **Rewarded video** is optional: watch one
  to unlock a locked topic for 6 hours, or to bank a streak freeze. It is only ever shown if you tap
  "watch an ad". **A full-screen ad** may also appear when you leave the results of a completed quiz
  — never during a quiz, and never for anyone with Premium. **At most two ads a day in total**, of
  both kinds together; after that, no ad of any kind is shown until the next day. Google's ad SDK
  may collect device and advertising identifiers to serve and measure ads, per
  [Google's ad policy](https://policies.google.com/technologies/partner-sites).
- **Google Play In-App Review** — the native "rate AlgorAI" prompt shown occasionally after
  positive usage signals. Handled entirely by Play's review API; AlgorAI doesn't see your
  rating or review text.
- **Google Firebase Analytics and Firebase Crashlytics** — usage events and crash reports, described
  in the next section.

### Usage analytics and crash reports (Android only)

The Android app sends a small, fixed set of usage events to **Google Firebase Analytics**:

- a topic was opened or completed (the topic, its section, whether it is free or Premium, and how
  you had access to it: free, Premium, or unlocked by an ad);
- a locked topic was tapped, or the Premium screen was opened;
- a quiz was finished (the quiz, your score as a percentage, and how long it took);
- the daily drill was opened or finished, a flashcard session was done (how many cards), your
  streak grew (its length), or a study reminder was tapped;
- two labels attached to those events: whether you have Premium, and which track (DSA or AI) you
  are in.

Firebase also records standard app data with these events, such as an app-instance ID generated on
your device, device model, OS version, app version, approximate country, and session timing. Because
the Android app includes Google's ad SDK, Firebase may also read your device's advertising ID unless
you have deleted or disabled it in your Android settings.

**Firebase Crashlytics** sends a report when the app crashes: the stack trace, device model, OS
version, app version, and an installation ID — nothing you typed or studied.

None of this includes your name, email, contacts, location beyond country, or anything that
identifies you to AlgorAI; there is no account to tie it to. The data is processed by Google under
the [Firebase privacy terms](https://firebase.google.com/support/privacy) and kept for the period set
in Google's retention settings. If you do not
want this data sent, you can reset or delete your advertising ID in Android settings, or stop using
the Android app; the iOS app sends none of it.

### iOS

- **Apple StoreKit** — handles the monthly subscription and the lifetime purchase through your
  Apple ID. Apple's own terms and privacy policy apply to the transaction.
- **Apple's rating prompt** (`SKStoreReviewController`) — the native "rate AlgorAI" prompt shown
  occasionally after positive usage signals. Handled entirely by Apple; AlgorAI doesn't see your
  rating or review text.
- The iOS app shows no ads and contains no advertising, analytics, or crash-reporting SDK.

## What AlgorAI does not do

- No analytics or crash reporting on iOS; on Android, only what is listed above.
- No user accounts, sign-in, or personal profile of any kind.
- No chat, comments, or any way for users to share content with each other.
- No sale of data, and no sharing of it beyond the Google and Apple services named above.

## Children

AlgorAI is listed for a general audience (13+) and is not directed at children. It does not
knowingly collect personal information from anyone.

## Changes to this policy

If this policy changes, the effective date above is updated and the new version posted at
the same address.

## Contact

Questions about this policy: hafsasultana0106@gmail.com
