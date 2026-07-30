# Play Store listing — Algora

Copy-paste source for the Play Console listing, plus an inventory of the graphics in this folder.
Every number quoted below is read out of the registries at build time (versionName `0.1.80`), not
estimated: 423 authored topics each with a runnable lab, 102 practice problems, 27 timed quizzes
(159 questions), and 1,976 flashcards.

**Re-check the counts before every upload.** They come from `TopicContentProvider.all`,
`TopicContentProvider.runnableSimulations`, `ProblemRegistry.all`, `QuizRegistry.all` and
`allReviewCards()`; the library grows most releases, and stale numbers in a store listing are a
misrepresentation Play can act on.

---

## Store presence

| Field | Value |
|---|---|
| App name | `Algora` |
| Package | `com.algora.app` |
| Category | Education |
| Tags | Computer science, Programming, Interview prep, Machine learning |
| Content rating | Everyone (no user content, no communication features) |
| Price | Free, with one lifetime in-app purchase |
| Contact | `hafsasultana0106@gmail.com` |
| Privacy policy | `https://saimumislam.github.io/algora/` (source: `gh-pages` branch, `index.html`) |

---

## Short description

Max 80 characters. Current: 75.

```
Learn algorithms and AI through 423 labs you can run — offline, no account.
```

Alternates, if the first reads too dense:

```
Data structures, algorithms and AI — every topic has a lab you can run.
```

```
See how algorithms actually work: 423 interactive labs, offline and ad-light.
```

---

## Full description

Max 4000 characters. Current: 2980.

```
Most algorithm apps hand you a wall of text and a code sample. Algora hands you the thing itself —
a linked list you can insert into, a graph you can run BFS across, a regression line you can drag
until the error stops shrinking.

423 topics across two tracks, and every single one ships with an interactive lab.

DSA TRACK
• Data structures — arrays, strings, linked lists, stacks, queues, hash tables, trees, heaps, tries,
  graphs, disjoint sets, skip lists, B-trees and more
• Algorithms — sorting, searching, recursion, dynamic programming, greedy methods, backtracking,
  graph traversal, shortest paths, string matching
• Complexity analysis — Big-O, amortised analysis, recurrence solving, space/time trade-offs
• Interview prep — 20 pattern guides (sliding window, two pointers, fast/slow, merge intervals,
  cyclic sort, monotonic stack, binary search on the answer, backtracking, union-find and more),
  each with its own lab; 20 behavioural prompts with STAR framing; and system design primers for
  both classic and ML rounds, 18 building blocks each

AI TRACK
• Machine learning — regression, classification, clustering, trees and ensembles, model evaluation
• Deep learning — neurons, backpropagation, CNNs, RNNs, transformers, training dynamics
• NLP — tokenisation, embeddings, attention, sequence models, the modern pipeline end to end
• Reinforcement learning — bandits, Q-learning, policy gradients, DQN, multi-agent methods,
  exploration, offline RL

EVERY TOPIC IS BUILT THE SAME WAY
1. What it is, in plain language
2. How it works, step by step
3. The mathematics, written out rather than hand-waved
4. A technical deep dive with runnable code
5. An interactive simulation — the part you actually play with
6. Where it shows up in real systems
7. Key takeaways worth remembering

PRACTICE, NOT JUST READING
• 102 problems grouped by pattern, each with examples, constraints, progressive hints, an approach
  walk-through with complexity, and a full Kotlin solution
• Every problem lists the topics it depends on, linked, so a gap sends you straight to the fix
• 27 timed quizzes — mock interview rounds, company-flavoured sets, and per-subject drills across
  both tracks, from arrays and bit manipulation to transformers and computer vision
• Spaced-repetition flashcards on an SM-2 schedule, capped at 20 new cards a day so a keen first
  session doesn't bury you a week later — including curated interview decks for complexities,
  pattern triggers, ML metrics and system design recall

TRACK WHAT YOU ACTUALLY DID
A completion ring per track, per-category bars, a week strip showing the topics you finished each
day, and a milestone ladder from your first topic to the whole track.

BUILT TO BE USED ANYWHERE
Everything is bundled in the app. No account, no sign-in, no server — open it on a plane, on the
underground, or on a phone with the data switched off and it behaves identically. Progress is stored
on your device.

Light and dark themes, five accent colours, bookmarks, and a continue-where-you-left-off shortcut.
Optional study reminders nudge you only if you have been away for a week, and can be switched off in
Settings.

PREMIUM
Algora is free to use, with a large part of the library open. Premium is a single lifetime purchase
— no subscription — that unlocks every remaining topic, lab and analysis tool and removes ads. Any
locked topic can also be opened for 24 hours by watching a rewarded ad, if you would rather not pay.
```

---

## What's new (release notes)

Max 500 characters.

```
First release.

• 423 topics across DSA and AI, each with an interactive lab
• 102 pattern-grouped practice problems with hints and worked solutions
• 27 timed quizzes and SM-2 scheduled flashcards, including curated interview decks
• 20 pattern guides, 20 behavioural prompts and two system design primers
• A progress dashboard with per-day activity and milestones
• Fully offline — no account, no server
```

---

## Graphics

| Asset | File | Spec |
|---|---|---|
| Feature graphic | `graphics/feature-graphic-1024x500.png` | 1024×500 PNG, no alpha |
| — source | `graphics/feature-graphic.html` | render with headless Chrome; command is in the file header |
| App icon | `graphics/icon-512.png` | 512×512 PNG |

Both are generated from `docs/design/Algora Icon.html`'s brand tile — the same
`#6366F1 → #7C3AED → #C026D3` gradient and node-graph mark the launcher icon uses — so the store
page and the installed icon read as one thing.

**The feature graphic quotes no counts, deliberately.** It used to read "176 topics · 91 problems ·
19 quizzes", which was three releases stale and unfixable without redrawing the art. Its pills now
say what the app *has* rather than how much of it — `DSA · AI · interview prep`, `Problems, quizzes,
flashcards`, `Works offline` — so the library can grow without dating the image. Numbers belong in
the text fields below, where they are re-derived from the registries before each upload.

---

## Screenshots

Captured on a Pixel 7 emulator (1080×2400, 9:19.5), dark theme, with the status bar in demo mode so
the clock reads 9:41 and no notification icons leak in. All content is real app state, not mockups:
the 6 completed topics behind the progress numbers were completed by hand before capture.

| # | File | What it shows | Suggested caption |
|---|---|---|---|
| 1 | `01-home-dsa.png` | Home, DSA track — featured lab, continue, category grid | Two tracks. One tap to the lab. |
| 2 | `02-topic-detail.png` | Array topic — hero plus How Does It Work steps | Every topic explains itself first |
| 3 | `03-lab-linked-list.png` | Linked-list visualiser with insert/delete/search | Then hands you the controls |
| 4 | `04-simulations-catalog.png` | Catalog of all 423 labs | 423 labs, all runnable offline |
| 5 | `05-problem-detail.png` | Problem with examples and prerequisite topics | Problems that link back to the theory |
| 6 | `06-timed-quiz.png` | Timed mock interview, question 1 of 6 | Timed rounds under a real clock |
| 7 | `07-flashcards.png` | Revealed card with Again / Good / Easy | Recall on an SM-2 schedule |
| 8 | `08-progress.png` | Progress dashboard — ring, week strip, milestones | See the week you actually had |
| 9 | `09-ai-track-home.png` | Home, AI track | ML, DL, NLP and RL, same treatment |
| 10 | `10-lab-linear-regression.png` | Regression lab fitted, with the gradient-descent code | Drag the line. Watch the error fall. |

**Play Console caps phone screenshots at 8.** Ten are supplied because ten were asked for; if the
console rejects the extras, drop 9 and 10 — the AI track is already implied by screenshot 1's track
toggle, and 3 already carries the "labs are interactive" message. Alternatively upload 9 and 10 in
the 7-inch and 10-inch tablet slots, which accept the same aspect ratio.

---

## Before submitting

Open items that are not copy problems but will block or damage the release:

- **Ad and billing ids are still fakes.** Phase 8 shipped billing and rewarded ads against debug
  stubs (`docs/plan/ROADMAP.md`). AdMob ids are now configured out of git — put the real pair in
  `local.properties` per `docs/admob-setup.md`; a release built without them ships Google's test
  ads and earns nothing. The Play Console product id for the lifetime purchase is still hardcoded
  and has to be swapped in before upload, or the paywall will not work in production.
- **Data safety form.** The app itself collects nothing — progress lives in local DataStore, there
  is no account and no network call of our own. The ads SDK does collect data, so the form must
  declare whatever AdMob's current disclosure requires once the real ids are in. Neither the study
  reminder nor the in-app review flow adds anything to declare: the reminder is scheduled locally
  from a stored epoch day, and Play's review API returns no data to the app.
- **`POST_NOTIFICATIONS` is now requested** (`AndroidManifest.xml`) for the study reminder. Play does
  not gate this permission behind a declaration form, but the listing must not misdescribe it: the
  only notification the app ever posts is the lapsed-learner nudge, at most one a week, with an
  opt-out switch in Settings and no notification at all until the user grants the runtime permission
  on Android 13+.
- **In-app review is offered at 15% track completion**, once per install, from the Progress screen.
  Play policy forbids incentivising, filtering or repeatedly prompting for ratings — none of which
  the implementation does, and the code must stay that way (`core/playreview/AppReviewPrompt.kt`).
- **Support email and privacy policy URL** are mandatory fields and are not written anywhere in the
  repo yet.
- **Version numbers are derived, not typed.** `versionCode` is the commit count on the branch being
  built and `versionName` is `major.minor.<commit count>` (`app/build.gradle.kts`) — currently 80 /
  `0.1.80`. Nothing to bump per build; raise `versionMajor` / `versionMinor` only when the release
  is a genuinely new line. Build from a git checkout with full history, or the fallback pins the
  code to 1 and Play will reject the second upload.
