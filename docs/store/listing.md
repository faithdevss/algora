# Play Store listing — AlgorAI

Copy-paste source for the Play Console listing, plus an inventory of the graphics in this folder.
Every number quoted below is read out of the registries at build time (versionName `1.1.80`), not
estimated: 504 authored topics each with a runnable lab, 102 practice problems, 54 timed quizzes
(453 questions), 57 pattern guides and 2,414 flashcards.

**Re-check the counts before every upload.** They come from `TopicContentProvider.all`,
`TopicContentProvider.runnableSimulations`, `ProblemRegistry.all`, `QuizRegistry.all` and
`allReviewCards()`; the library grows most releases, and stale numbers in a store listing are a
misrepresentation Play can act on.

---

## Store presence

| Field | Value |
|---|---|
| App name | `AlgorAI: DSA, AI & Interviews` (29/30 chars) |
| Package | `com.algora.app` |
| Category | Education |
| Tags | Computer science, Programming, Interview prep, Machine learning |
| Content rating | Everyone (no user content, no communication features) |
| Price | Free, with one lifetime in-app purchase |
| Contact | `hafsasultana0106@gmail.com` |
| Privacy policy | *(host `docs/privacy-policy.md` somewhere public; fill in URL before submitting)* |

**The title names both halves of the app.** It is the most heavily weighted search field, so it
carries the two subjects people learn here (DSA, AI) and the reason many of them are learning
(interviews). An interview-only title (`Algora: Coding Interview Prep`) was tried and dropped: it
hid the course from students and self-learners, who are most of the audience. The launcher label is
`AlgorAI` (`app_name`); the store title and the installed name are separate on purpose.

---

## Short description

Max 80 characters. Current: 79.

```
Learn DSA and AI with interactive labs, then practise coding and ML interviews.
```

Learning first, interviews second — the same order the app is built in. "Learn DSA and AI" catches
students and self-learners searching a subject; "coding and ML interviews" catches job-seekers, and
names both kinds of technical round the app prepares for. "Interactive labs" is what sets it apart
from reading apps.

**No topic count here, deliberately.** The library grows most releases, so a number in this field is
one more thing to re-derive before every upload and one more way to ship a stale claim. Counts live
in the full description, which is edited anyway. Digits are not query terms, so this costs nothing
in search.

**No promise of a job, and no company names.** "Get hired" or "land a FAANG offer" would be an
outcome claim the app cannot back, and naming companies claims a provenance these authored questions
do not have. Both are metadata-policy risks on Play.

Alternates:

```
Learn DSA, AI and ML with runnable labs, and prepare for coding interviews.
```

```
Learn DSA and AI with visual labs, then practise coding and ML interviews.
```

The pitch in `core/share/AppShare.kt` is this same sentence; change both together.

---

## Full description

Max 4000 characters. Current: 3579.

The first lines are what Play shows before "More", so they name both halves: learn DSA and AI by
interactive labs, then prepare for coding and ML interviews. The learning tracks come first because the course
is most of the app; the interview section follows with its own heading, so job-seekers who expand
the text find it immediately.

```
Learn data structures, algorithms and AI through interactive labs, then prepare for coding and machine learning interviews. AlgorAI is a visual computer science course and an interview-prep app in one, and it works fully offline.

Most apps hand you a wall of text. AlgorAI hands you the thing itself: a linked list you can insert into, a graph you can run BFS across, a regression line you can tune with sliders until the error stops shrinking. 504 topics, each with an interactive lab.

LEARN DSA — DATA STRUCTURES AND ALGORITHMS
• Data structures: arrays, strings, linked lists, stacks, queues, hash tables, trees, heaps, tries, graphs, disjoint sets, B-trees and more
• Algorithms: sorting, searching, recursion, dynamic programming, greedy, backtracking, graph traversal, shortest paths, string matching
• Complexity analysis: Big-O, amortised analysis, recurrences, space/time trade-offs

LEARN AI — MACHINE LEARNING, DEEP LEARNING, NLP AND RL
• Machine learning: regression, classification, clustering, trees and ensembles, model evaluation
• Deep learning: backpropagation, CNNs, RNNs, transformers, diffusion models
• NLP and LLMs: tokenisation, embeddings, attention, RAG, fine-tuning, agents
• Reinforcement learning: bandits, Q-learning, policy gradients, DQN, PPO

EVERY TOPIC, THE SAME SHAPE
Plain-language intro, a step-by-step walkthrough, the maths written out, complete code, an interactive lab, real-world uses and key takeaways.

PREPARE FOR INTERVIEWS
• 54 timed quizzes with 453 questions, under a real countdown clock
• Beginner rounds, free: arrays, hash maps, Big-O, stacks and queues, recursion, search, plus coding rounds on arrays, linked lists and trees
• Advanced rounds: graphs, dynamic programming, advanced data structures, and hard classics such as trapping rain water
• Scenario, picture and story rounds: real product problems, diagrams and narrated cases (a startup lunch rush, a 3 a.m. outage), solved step by step
• AI interview rounds: NLP, LLM and GenAI engineering, computer vision, recommender systems, and AI in healthcare, finance and retail
• Every answer explains the solution, its complexity, and why the tempting wrong answer fails
• 57 coding pattern guides (sliding window, two pointers, merge intervals, monotonic stack, backtracking, union-find and more), each with its own lab
• 102 coding problems grouped by pattern, with progressive hints, a walk-through and a full Kotlin solution
• System design and ML system design primers, and 20 behavioural questions with STAR guidance

For students, self-learners, campus placements, new-grad roles and experienced software engineer, data scientist or ML engineer interviews.

LEARN, THEN REMEMBER
Learn mode checks each quiz answer as you go. A weak-spot drill targets the patterns you miss most, and every miss comes back as a flashcard. 2,414 flashcards on an SM-2 schedule, a completion ring per track, a week strip of what you finished, and your best score on every quiz.

WORKS ANYWHERE
Everything is bundled in the app. No account, no sign-in, no server: study on a commute or with data switched off. Progress stays on your device. Light and dark themes, bookmarks, and optional study reminders you can switch off in Settings.

PREMIUM
AlgorAI is free to start, with 60 lessons and 35 quiz sets open, including every beginner interview round. Premium is a single lifetime purchase, not a subscription, that unlocks every remaining topic, lab and advanced interview round and removes ads. A rewarded ad opens most locked lessons for 6 hours; interview practice needs Premium.
```

---

## What's new (release notes)

Max 500 characters. Current: 414.

```
New: learn mode, weak spots and AI interview rounds.

• Learn mode: check each answer and read why, no timer
• Weak spots: see the patterns you miss most and drill them in one tap
• Every missed question comes back as a flashcard
• AI rounds: NLP, LLMs, computer vision, recommenders, industries
• Picture and story rounds, and 10 free beginner rounds
• 54 timed quizzes, 453 questions, each with a worked solution
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
| 6 | `06-lab-linear-regression.png` | Regression lab fitted, with the gradient-descent code | Move the sliders. Watch the error fall. |
| 7 | `07-problem-detail.png` | Problem with examples and prerequisite topics | Problems that link back to the theory |
| 8 | `08-timed-quiz.png` | Timed mock interview, question 1 of 6 | Timed rounds under a real clock |
| 9 | `09-progress.png` | Progress dashboard — ring, week strip, milestones | See the week you actually had |
| 10 | `10-flashcards.png` | Revealed card with Again / Good / Easy | Recall on an SM-2 schedule |
| 11 | *`11-topic-detail-dynamic-programming.png`* | Dynamic programming — deep dive with complete Kotlin | Real code, not pseudocode |
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
the mathematics (§3), 11 is the technical deep dive with complete code (§4), 12 is real-world
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
- **In-app review is offered once per install**, at the first of three moments: 15% of the active
  track complete or 5 different active days (checked on the Progress screen), or a timed quiz
  finished at 80% or more (on its results screen).
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
