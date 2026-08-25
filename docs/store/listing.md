# Play Store listing — Algora

Copy-paste source for the Play Console listing, plus an inventory of the graphics in this folder.
Every number quoted below is read out of the registries at build time (versionName `1.1.41`), not
estimated: 504 authored topics each with a runnable lab, 102 practice problems, 29 timed quizzes
(173 questions), and 2,414 flashcards.

**Re-check the counts before every upload.** They come from `TopicContentProvider.all`,
`TopicContentProvider.runnableSimulations`, `ProblemRegistry.all`, `QuizRegistry.all` and
`allReviewCards()`; the library grows most releases, and stale numbers in a store listing are a
misrepresentation Play can act on.

---

## Store presence

| Field | Value |
|---|---|
| App name | `Algora: DSA & AI Learning` (25/30 chars) |
| Package | `com.algora.app` |
| Category | Education |
| Tags | Computer science, Programming, Interview prep, Machine learning |
| Content rating | Everyone (no user content, no communication features) |
| Price | Free, with one lifetime in-app purchase |
| Contact | `hafsasultana0106@gmail.com` |
| Privacy policy | *(host `docs/privacy-policy.md` somewhere public; fill in URL before submitting)* |

---

## Short description

Max 80 characters. Current: 79.

```
Data structures, algorithms, ML, DL, NLP and RL — runnable labs, fully offline.
```

Named subjects rather than the umbrella "AI", because the title no longer carries them — `Algora:
DSA & AI Learning` spends its 25 characters on the brand and the two tracks, so ML, DL, NLP and RL
appear nowhere in the two highest-weighted fields unless this line names them.

**No topic count here, deliberately** — same reasoning as the feature graphic below. The library
grows most releases, so a number in this field is one more thing to re-derive before every upload
and one more way to ship a stale claim. Counts live in the full description, which is edited
anyway. This costs nothing in search: digits are not query terms.

Alternates, if the first reads too dense:

```
Data structures, algorithms, ML, DL, NLP, RL — interactive labs, works offline.
```

```
Data structures, algorithms and AI — every topic has a lab you can run.
```

---

## Full description

Max 4000 characters. Current: 3471.

```
Most algorithm apps hand you a wall of text and a code sample. Algora hands you the thing itself —
a linked list you can insert into, a graph you can run BFS across, a regression line you can drag
until the error stops shrinking.

504 topics across two tracks, and every single one ships with an interactive lab you can run
offline — a computer science visualiser, not a textbook you scroll.

DSA TRACK — DATA STRUCTURES AND ALGORITHMS
• Data structures — arrays, strings, linked lists, stacks, queues, hash tables, trees, heaps, tries,
  graphs, disjoint sets, skip lists, B-trees and more
• Algorithms — sorting, searching, recursion, dynamic programming, greedy methods, backtracking,
  graph traversal, shortest paths, string matching
• Complexity analysis — Big-O, amortised analysis, recurrence solving, space/time trade-offs
• Coding interview prep — 20 pattern guides (sliding window, two pointers, fast/slow, merge intervals,
  cyclic sort, monotonic stack, binary search on the answer, backtracking, union-find and more),
  each with its own lab; 20 behavioural prompts with STAR framing; and system design primers for
  both classic and ML rounds, 18 building blocks each

AI TRACK — MACHINE LEARNING, DEEP LEARNING, NLP AND RL
• Machine learning — regression, classification, clustering, trees and ensembles, model evaluation
• Deep learning — neurons, backpropagation, CNNs, RNNs, transformers, training dynamics
• NLP (natural language processing) — tokenisation, embeddings, attention, sequence models, the
  modern pipeline end to end
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
• 102 coding problems grouped by pattern, each with examples, constraints, progressive hints, an approach
  walk-through with complexity, and a full Kotlin solution
• Every problem lists the topics it depends on, linked, so a gap sends you straight to the fix
• 29 timed quizzes — mock interview rounds, company-flavoured sets, and per-subject drills across
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
locked topic can also be opened for 6 hours by watching a rewarded ad, if you would rather not pay.
```

---

## What's new (release notes)

Max 500 characters.

```
First release.

• 504 topics across DSA and AI, each with an interactive lab
• 102 pattern-grouped practice problems with hints and worked solutions
• 29 timed quizzes (173 questions) and 2,414 SM-2 scheduled flashcards
• Streak freezes: miss a day without losing your streak
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

The order below runs **DSA half, then AI half, then practice** — home, topic, lab; home, topic, lab;
problem, quiz. Both tracks get the identical three-beat so the AI track reads as a first-class half
of the app rather than a bonus tacked on the end, which is how it read when its only shot sat at
position 9, past the point Play stops scrolling most people.

| # | File | What it shows | Suggested caption |
|---|---|---|---|
| 1 | `01-home-dsa.png` | Home, DSA track — featured lab, continue, category grid | Two tracks. One tap to the lab. |
| 2 | `02-topic-detail-array.png` | Array topic — hero plus How Does It Work steps | Every topic explains itself first |
| 3 | *`03-lab-graph-bfs.png`* | Graph visualiser mid-BFS — visited set, frontier, controls | Then hands you the controls |
| 4 | `04-home-ai.png` | Home, AI track | ML, DL, NLP and RL, same treatment |
| 5 | *`05-topic-detail-transformers.png`* | Transformers — the mathematics section, written out | The maths, not hand-waved |
| 6 | `06-lab-linear-regression.png` | Regression lab fitted, with the gradient-descent code | Drag the line. Watch the error fall. |
| 7 | `07-problem-detail.png` | Problem with examples and prerequisite topics | Problems that link back to the theory |
| 8 | `08-timed-quiz.png` | Timed mock interview, question 1 of 6 | Timed rounds under a real clock |
| 9 | `09-progress.png` | Progress dashboard — ring, week strip, milestones | See the week you actually had |
| 10 | `10-flashcards.png` | Revealed card with Again / Good / Easy | Recall on an SM-2 schedule |
| 11 | *`11-topic-detail-dynamic-programming.png`* | Dynamic programming — deep dive with runnable Kotlin | Runnable code, not pseudocode |
| 12 | *`12-topic-detail-backpropagation.png`* | Backpropagation — where it shows up in real systems | Then shows you where it is used |
| 13 | *`13-topic-detail-binary-search-tree.png`* | Binary search tree — key takeaways card | The part worth remembering |
| 14 | `14-simulations-catalog.png` | Catalog of every lab | Every topic has one |

*Italic filenames are not captured yet.* Everything else is on disk under exactly the name shown —
the renumbering has already been done with `git mv`, so the numbers here and the numbers in
`screenshots/` agree, with gaps at 3, 5, 11, 12 and 13 waiting to be filled. The old linked-list lab
survives as `spare-lab-linked-list.png`, out of the numbered sequence but not deleted.

**The five outstanding captures, and why each is framed the way it is.**

Shot 3 replaces the linked-list lab. A graph is the better shot on two counts: the full
description's own opening promises "a graph you can run BFS across", so the store page should show
the thing it just claimed, and a traversal lights up more of the screen than a four-node list.
Capture `topicId = "graph"` or `"bfs"` (`SimulationType.GraphVisualizer`) mid-run, not at rest, so
the visited and frontier states are both visible.

Shots 2, 5, 11, 12 and 13 are five topic details, and **each one frames a different section of the
7-section template** — that is the whole point of having five. Shot 2 is How Does It Work (§2), 5 is
the mathematics (§3), 11 is the technical deep dive with runnable code (§4), 12 is real-world
applications (§6) and 13 is key takeaways (§7). Five shots of the same section would read as one
screen photographed five times; five different sections make the argument the full description makes
in prose, that every topic is built the same way. §5 needs no shot of its own — it is the
interactive lab, which shots 3 and 6 already are.

Topic ids, all verified present in the registry: `dynamic_programming`, `backpropagation`,
`binary_search_tree`, `transformers`. Pick the frame so the section heading itself is visible in the
shot, otherwise the sequence just looks like five scrolls of one page.

**Play Console caps phone screenshots at 8**, so 1–8 are the set that has to survive on its own, and
they were chosen to. Cut from the old top eight: the simulations catalog (a list screen, and its
caption quoted a topic count that is now deliberately absent from the short description) and
flashcards and progress (both post-install value — they sell retention to someone who has already
installed, which is not the job of the first eight). Upload 9–14 into the 7-inch and 10-inch tablet
slots, which take the same aspect ratio and cap at 8 each, so all six fit in either one.

Shots 11–13 are also the **swap bench**. If 1–8 underperform, the cheapest test is trading shot 2
for 11 or 12 — same screen type, different section, no recapture of anything else needed. Change one
at a time and leave it up long enough to read, or the result means nothing.

---

## Before submitting

Open items that are not copy problems but will block or damage the release:

- **AdMob ids are real — and there are three of them now.** `local.properties` must carry
  `admob.appId`, `admob.rewardedUnitId` **and** `admob.interstitialUnitId` (the last added with the
  quiz-exit interstitial in Phase 14). Note the build does **not** fail on a fallback: every Gradle
  configuration run *logs* "AdMob: using Google test ids for release builds", and a release built
  with that line still in the log ships test ads and earns nothing. Read the log before uploading.
  Still outstanding: the Play Console in-app product must exist with id `algora_premium_lifetime`
  (`core/billing/PremiumBilling.kt`) before the paywall can complete a real purchase — that's a Play
  Console entry, not a code change.
- **Data safety form.** The app itself collects nothing — progress lives in local DataStore, there
  is no account and no network call of our own. The ads SDK does collect data, so the form must
  declare whatever AdMob's current disclosure requires once the real ids are in. The interstitial
  adds no new *category* of collection — it is the same SDK and the same identifiers as the rewarded
  unit — but it does change what the listing and the privacy policy must say, because it is the one
  ad the user does not opt into (`docs/privacy-policy.md` is already updated). Neither the study
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
- **Privacy policy URL is still the one open blocker for this submission.** `docs/privacy-policy.md`
  exists but isn't hosted anywhere public — Play Console will not accept a listing without a live
  URL in that field. Support email is set (`hafsasultana0106@gmail.com`, see Store presence above).
- **Version numbers are derived, not typed.** `versionCode` is the commit count on the branch being
  built and `versionName` is `major.minor.<commit count>` (`app/build.gradle.kts`) — currently 141 /
  `1.1.41`. Nothing to bump per build; raise `versionMajor` / `versionMinor` only when the release
  is a genuinely new line. Build from a git checkout with full history, or the fallback pins the
  code to 1 and Play will reject the second upload.
