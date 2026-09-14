# Reserve a Slot — make the guards actually guard (1, 2, 3, 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix a purge test that would pass against the very defect it guards, and add the access-control config test whose absence let a 403-for-everyone endpoint reach review twice.

**Architecture:** Four independent tasks across three repos. Three are test-only. The fourth is a one-line annotation.

**Spec:** https://claude.ai/code/artifact/90e6b0eb-dc8a-4697-a8d4-8d9086d283d6 and `cpp-context-hearing/docs/reserve-a-slot-jira.md`

## Global Constraints

- **NO COMMITS.** No `git add`, `git commit`, `git stash`, `git rm`. Everything stays unstaged. Any "Commit" step is an explicit no-op.
- **Nothing in this feature is committed**, so `git diff` shows many prior, already-reviewed tickets. Do not revert, tidy or comment on any of it.
- **`cpp-context-listing-courtscheduler` is Gradle. `cpp-context-hearing` and `cpp-context-listing` are Maven.**
- **No production code changes in any task.** A test that cannot pass without one has found a defect: report it, do not code around it.
- Every new config test must **pass on arrival**. A test that fails the moment it lands gets disabled rather than fixed. Where reality is already imperfect, encode the known exceptions in a documented allowlist — the allowlist is the deliverable as much as the assertion is.

## Why these four

Two defects reached review approval today. Both were found by running things, not by reading them:

- `courtscheduler.get.booking-status` had no Drools rule — 403 to every caller, and because its consumer fails open, the pre-share gate would have reported "couldn't check" forever while appearing to work.
- BUG-1 was a bare `DELETE` in the nightly purge that removed reservation rows without restoring session capacity — every expired reservation silently burned a slot.

Task 1 closes the second hole for good. Tasks 2 and 3 close the first, in the two repos that still lack the guard.

---

### Task 1: make the purge test prove capacity comes back

**Repo:** `cpp-context-listing-courtscheduler` (**Gradle**), branch `team/ras`
**File:** `listingcourtscheduler-integration-test/src/test/java/uk/gov/moj/cpp/courtscheduler/integration/CourtSchedulerIT.java`

**The problem is deeper than a missing assertion.** `shouldPurgeAllocatedListingsWhoseExpiresAtHasAlreadyPassed` (around line 5225) seeds its rows with `databaseSeeder.insertAllocatedListing(...)`, writing straight to the table. The booking pipeline never runs, so `court_schedule.available_slots` is never decremented. Adding "assert capacity restored" to the test as it stands would assert that nothing changed from nothing — it would pass whether or not the purge restores anything.

BUG-1 was precisely a purge that deleted the row and left the capacity consumed. **The current test passes against that defect.** To catch it, the reservation must be created through the real path so that capacity is genuinely taken first.

- [ ] **Step 1: Rewrite the test to reserve through the real path**

Keep the existing test's two-row shape — one expired, one not — because the not-expired row is what proves the purge is selective. Change how they are created:

1. Seed a court schedule with **pinned** capacity (do not rely on `RANDOM`): slot-based, active, `availableSlots` and `maxSlots` both set to a known value such as 5, a future `sessionDate`. `createTestCourtSchedule()` at line 3654 pins some fields but leaves capacity to `RANDOM` — pin it explicitly in your test or in a small local helper, and say which you did.
2. Create **two separate reservations** on that session by calling `POST /provisionalBooking` twice (media type `application/vnd.courtscheduler.create.provisional.booking+json`), capturing each returned `bookingId`. Two calls, not one with two slots — they must be independent bookings so one can expire while the other does not.
3. Assert capacity has actually dropped by two before you purge. If it has not, the reservation did not go through the real pipeline and the rest of the test is meaningless — assert this explicitly with a message saying so.
4. Age **one** of them with `databaseSeeder.updateAllocatedListingExpiresAt(<its row id>, LocalDate.now().minusDays(1))`. Find the row id by reading `databaseReader.allocatedListings()` and matching on `hearingId` equal to that bookingId — a reservation's `hearing_id` is its bookingId.
5. Leave the other at its natural expiry (today). The existing test's comment explains why "not yet expired" needs a **future** date to exercise the not-purged branch, since the cutoff is a date and a today-dated expiry survives until the day rolls over. Preserve that reasoning — set the survivor to `plusDays(1)` as the current test does.
6. `POST` the purge exactly as the current test does, then assert:
   - the expired row is gone and the surviving row is still there (keep the existing assertions)
   - **`availableSlots` is back up by exactly one** — the purged reservation's slot returned, the surviving reservation's did not
   - assert the exact number, not a direction: a message like `"the purge deleted the row without restoring its slot — this is BUG-1"` on the capacity assertion.

Keep the existing comment about the date-based cutoff. Add a short Javadoc saying why the reservations are made through `POST /provisionalBooking` rather than seeded, and naming BUG-1 — otherwise someone will "simplify" it back to direct seeding and silently remove the guard.

- [ ] **Step 2: Run it**

```bash
./gradlew :listingcourtscheduler-integration-test:test --tests '*CourtSchedulerIT*'
```

(The Gradle task is `test`, not `integrationTest`. The docker-compose stack does come up in this environment.)

Expected: PASS, with every other test in that large class still passing. If the stack will not start, say so plainly and fall back to `compileTestJava` — **never report an unrun test as passing.**

- [ ] **Step 3: Prove it catches BUG-1**

Temporarily make the purge defective again: in `AllocatedListingRepository.findExpiredReservedSessions` / the repository's `releaseExpiredReservations`, comment out the capacity-restoring steps so only the row deletion happens. Re-run and confirm the test FAILS on the capacity assertion. **Restore the production code exactly**, re-run to confirm PASS, and verify with `git diff` that the production file is byte-identical to how you found it.

Both runs go in your report. This is the entire point of the task: without it you have only changed a test that was always green to a different test that is always green.

- [ ] **Step 4: Commit** — **skipped, no commits.**

---

### Task 2: RAML↔Drools guard for courtscheduler

**Repo:** `cpp-context-listing-courtscheduler` (**Gradle**), branch `team/ras`
**File to create:** `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/accesscontrol/CourtSchedulerAccessControlConfigTest.java`

This is the test that would have caught today's Critical automatically.

**Measured state — re-verify it, do not assume it:**
- RAML: `listingcourtscheduler-api/src/raml/courtscheduler-api.raml`, ~42 distinct `name:` actions.
- DRL: `listingcourtscheduler-api/src/main/resources/uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl`, ~43 `Action(name == "…")` rules.
- **10 RAML actions have no name-keyed rule**, and they are pre-existing, not bugs:
  - `courtscheduler.get.court_schedule` — covered by a rule named `courtscheduler.get`
  - nine `courtscheduler.judiciary.*.availability*` actions — covered by rules keyed on REST paths instead, e.g. `Action(name == "POST /judiciaries/availability-rules/add")`

- [ ] **Step 1: Write the test**

Model it on the one just added to `cpp-context-listing`: `listing-query/listing-query-api/src/test/java/uk/gov/moj/cpp/listing/queryapi/ListingQueryApiAccessControlConfigTest.java`. **Read it first** — it already solves the two traps: a `withoutComments` helper stripping `/* … */` then `//` (a rule behind a comment is as disabled as a deleted one), and a non-empty guard so a regex that silently matches nothing cannot pass forever.

Assert **one-directionally**: every RAML action either has a name-keyed rule or appears in the allowlist. Do not assert set equality.

The allowlist is a `Set<String>` constant with a comment per entry — or one comment per group for the nine judiciary ones — saying **why** each is exempt and what covers it instead. Write it so a reader can tell an intentional exemption from an oversight, because that distinction is the only thing standing between this test and the bug it exists to catch.

Include the same non-empty guard, and a second one: assert the allowlist contains no action that *does* now have a rule. Otherwise an exemption outlives the problem it documented and the next genuinely missing rule hides behind a stale entry.

- [ ] **Step 2: Run it**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*CourtSchedulerAccessControlConfigTest*'
```

Expected: PASS on arrival.

- [ ] **Step 3: Prove it bites**

Comment out the `courtscheduler.get.booking-status` rule — the one added today to fix the Critical — with `//`, re-run, confirm FAIL naming that action. Repeat with `/* … */`. Restore, confirm PASS, and verify the `.drl` is byte-identical with `git diff`. All runs in the report.

- [ ] **Step 4: Commit** — **skipped, no commits.**

---

### Task 3: RAML↔Drools guard for hearing's command API, and a defect to report

**Repo:** `cpp-context-hearing` (**Maven**), branch `team/ccsph2`
**File to create:** `hearing-command/hearing-command-api/src/test/java/uk/gov/moj/cpp/hearing/command/api/accesscontrol/HearingCommandApiAccessControlConfigTest.java`

**Measured state — re-verify it:**
- RAML: `hearing-command/hearing-command-api/src/raml/hearing-command-api.raml`, ~69 `name: hearing.*` actions.
- DRL: `hearing-command/hearing-command-api/src/main/resources/uk/gov/moj/cpp/hearing/command/api/accesscontrol/hearing-command-api.drl`, ~71 rules, **all** keyed on a specific `hearing.*` action name. There is no catch-all.
- **Exactly one RAML action has no rule: `hearing.replicate-shared-results`.** It is reachable — `@Handles("hearing.replicate-shared-results")` at `HearingCommandApi.java:352` — and no `.drl` in the repo mentions it.

**This looks like a live pre-existing defect, and it is not ours.** It has nothing to do with reserve-a-slot. Do not fix it, and do not delete the RAML entry. Add it to the allowlist with a comment marked clearly as a **suspected defect, not an intentional exemption**, naming the evidence (declared in RAML, `@Handles` at that line, no rule in any `.drl`, no catch-all rule) so that whoever owns it can act. Note in your report that this needs raising with that feature's owners rather than absorbed here.

Note there is already a `HearingCommandHandlerRamlConfigTest` in `hearing-command-handler`. It checks something different — `@Handles` annotations against the *messaging* RAML — so it is a style reference only, not a duplicate of what you are writing.

- [ ] **Step 1: Write the test**

Same shape as Task 2: comment-stripping, one-directional assertion, non-empty guard, stale-allowlist guard, allowlist entries commented with their reason.

- [ ] **Step 2: Run it**

```bash
mvn -o -pl hearing-command/hearing-command-api -am test -Dtest=HearingCommandApiAccessControlConfigTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS on arrival, with `hearing.replicate-shared-results` allowlisted.

- [ ] **Step 3: Prove it bites**

Comment out the rule for `hearing.release-provisional-hearing-slots` (added by this feature, at DRL line ~470) with `//`, then with `/* … */`; confirm FAIL each time naming that action; restore; confirm PASS; `git diff` clean. All runs in the report.

- [ ] **Step 4: Commit** — **skipped, no commits.**

---

### Task 4: label the fail-open test's expected server error

**Repo:** `cpp-context-listing` (**Maven**), branch `team/ccsph2n`
**File:** `listing-integration-test/src/test/java/uk/gov/moj/cpp/listing/it/BookingStatusIT.java`

A deferred Minor from review: `shouldFailOpenWithUnknownWhenCourtschedulerErrors` deliberately drives courtscheduler to a 500, and the production path logs an ERROR line. Its template's equivalent negative test carries `@ExpectedServerErrors` so log-triage tooling knows that ERROR is intended; ours does not.

- [ ] **Step 1: Read the template, then annotate**

Open `CourtScheduleDraftStatusIT` and find its server-error test — copy the annotation exactly as used there, including any argument it passes. **Do not guess the annotation's shape or its import**; if it is not present in that file, search the IT module for `ExpectedServerErrors`, and if it genuinely does not exist, stop and report rather than inventing one.

- [ ] **Step 2: Verify it compiles**

```bash
mvn -o -pl listing-integration-test -am test-compile
```

Expected: BUILD SUCCESS. **These ITs cannot run in this environment** — listing's IT stack needs Postgres, Artemis and WildFly, none of which are present, and there is no compose file. Compile-only is the expected outcome here; label it as such and do not imply the test ran.

- [ ] **Step 3: Commit** — **skipped, no commits.**

---

## Deliberately not in scope

- **Fixing `hearing.replicate-shared-results`.** Pre-existing, unrelated to this feature, and owned elsewhere. Task 3 documents it; raising it is a ticket, not a code change.
- **The judiciary REST-path-keyed rules in courtscheduler.** They are pre-existing and evidently working; Task 2 records them in the allowlist rather than churning them.
- **DEC-2** and the **Crown multi-day anchor-only hold** — product decisions.
- **LPT-2433 / LPT-2506 retest** — QA activity, unblocked by BUG-1's fix.
