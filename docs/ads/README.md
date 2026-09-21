# Google Ads assets — AlgorAI

Everything a Google Ads App campaign asks for, in one place: the text that goes
in the asset fields, and the images in `images/`.

Sibling of `docs/store/listing.md`, which covers the Play listing. Where the two
overlap — the pitch, the brand — this file defers to that one.

---

## Images

Uploadable PNGs live in `images/`. Their `.html` sources live in `graphics/`,
and `graphics/build.sh` turns the second into the first — the same
headless-Chrome pipeline the Play feature graphic uses. Rebuild all five:

```
./docs/ads/graphics/build.sh
```

or one at a time by substring: `./docs/ads/graphics/build.sh portrait`.

| Asset | File | Slot it fills |
|---|---|---|
| Landscape 1.91:1, A | `images/ad-landscape-1200x628.png` | Image, required |
| Landscape 1.91:1, B | `images/ad-landscape-b-1200x628.png` | Image, variant |
| Square 1:1, A | `images/ad-square-1200x1200.png` | Image, required |
| Square 1:1, B | `images/ad-square-b-1200x1200.png` | Image, variant |
| Portrait 4:5 | `images/ad-portrait-960x1200.png` | Image, recommended |
| Logo 1:1 | `images/logo-square-1200x1200.png` | Logo |
| Logo 4:1 | `images/logo-landscape-1200x300.png` | Logo |

Upload all seven. Google will not serve a placement whose ratio it has no asset
for, and the ratio it wants is decided by the surface, not by us.

**A and B are a real test, not the same ad twice.** A sells watching an
algorithm run — a lab mid-execution, text left, phones right. B sells being
drilled on it — the Practice hub and the labs catalog, with the layout mirrored
so the two are told apart at a glance in the asset library. Change one variable
at a time from here and leave each up long enough to read, or the result means
nothing. B deliberately does not use the progress dashboard: a fresh install's
dashboard is all zeros, which reads as an empty app.

**The art is real app state, not a mockup.** The phones carry
`docs/store/screenshots/Algora_33.png` (graph two-colouring lab, mid-run),
`Algora_26.png` (tree lab with the Kotlin traversal above it) and `Algora_30.png`
(the Practice hub). `Algora_33` is the hero everywhere because its nodes are
already the brand violet, so the phone and the canvas behind it read as one
piece — most of the other labs use traffic-light colours that fight the ramp.

**No counts are burned into the art**, for the reason `listing.md` gives about
the feature graphic: the library grows most releases, and a number inside a PNG
cannot be re-derived at upload time the way a text field can.

---

## Headlines

Max 30 characters. Google mixes these freely, so each one sells a different
hook rather than restating the last.

| Headline | Chars | Angle |
|---|---|---|
| `Learn DSA & AI by Doing` | 23 | method |
| `504 Topics, 504 Labs` | 20 | scale |
| `DSA, ML, DL, NLP and RL` | 23 | coverage |
| `Coding Interview Prep` | 21 | intent |
| `Works Fully Offline` | 19 | constraint |

Spares, all within the limit: `Algorithms You Can Run` (22),
`Not a Textbook. A Lab.` (22), `Practice, Not Just Reading` (26).

---

## Descriptions

Max 90 characters.

| Description | Chars |
|---|---|
| `Data structures, algorithms, ML, DL, NLP and RL. Every topic ships a lab you can run.` | 85 |
| `504 topics, pattern-grouped problems, timed quizzes and flashcards. Fully offline.` | 82 |
| `Insert into a linked list, run BFS on a graph, tune a regression line until it fits.` | 84 |
| `Interview prep: pattern guides, timed mock rounds and worked Kotlin solutions.` | 78 |
| `No account, no sign-in, no server. Open it on a plane and it behaves identically.` | 81 |

Spare: `Every topic: plain language, the maths written out, complete code, then a lab.` (78)

---

## Before the campaign goes live

- **`504` is the one figure in the copy, and it dates.** Counted off
  `TopicContentProvider.byId` at 165 commits (`1.1.65`); every topic there
  assigns a real `SimulationType`, none `NotYetAvailable`, which is what makes
  "504 Labs" true rather than approximately true. Re-count before editing the
  campaign, or swap `504 Topics, 504 Labs` for `Algorithms You Can Run` and take
  the number out of the descriptions too. Note the 504 labs are 34 lab engines
  re-parameterised, not 504 bespoke screens — "every topic ships a lab" is
  accurate, "504 unique visualisers" would not be. The B variants carry the
  number a second way, inside `Algora_28.png` itself, where the labs catalog
  prints "504 interactive labs" as real app state. That one cannot be fixed by
  editing text: it needs the screenshot recaptured.
- **Do not claim a rating or a review count.** The app has neither yet, and
  Google disapproves ads that quote them without the data behind it.
- The pitch line in `AppShare.kt` and the short description in
  `listing.md` are the same sentence on purpose. If the pitch changes, all three
  places change together.
