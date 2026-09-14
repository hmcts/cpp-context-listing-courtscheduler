# Reserve a Slot — integration coverage and a contract guard (A, B, C)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cover the reserve-a-slot flow end-to-end on the code path production actually uses, prove listing's new pass-through against a stubbed courtscheduler, and stop a whole class of runtime-only breakage in listing's query API.

**Architecture:** Three independent tasks in two repos. No production code changes anywhere — with one exception in Task 1, a comment that is actively wrong.

**Spec:** https://claude.ai/code/artifact/90e6b0eb-dc8a-4697-a8d4-8d9086d283d6, and the BUG-3 / NEW-7a / NEW-10 / NEW-15 entries in `cpp-context-hearing/docs/reserve-a-slot-jira.md`

## Global Constraints

- **NO COMMITS.** No `git add`, `git commit`, `git stash`, `git rm`. Everything stays as unstaged working-tree edits. Any "Commit" step is an explicit no-op — read it, skip it, say so in your report.
- **Nothing in this feature is committed**, so `git diff` in either repo shows tens of files of prior, already-reviewed tickets. Do not revert, tidy or comment on any of it.
- **`cpp-context-listing-courtscheduler` is Gradle. `cpp-context-listing` is Maven.** Never mix them.
- **No production code changes**, except the single misleading Javadoc sentence named in Task 1 Step 4. If a test seems to need a production change to pass, **stop and report** — that means it has found a real defect, which is a finding, not something to code around.

## Honest note on the environment, which shapes what "done" means

Docker is running but currently hosts only a WireMock container. **Task 1's tests need the courtscheduler integration docker-compose stack** (`docker/docker-compose.integration.yml`, driven by the Gradle `integrationTest` task) and it may not come up in this environment. **Task 2's** tests are WireMock-based and stand a much better chance. **Task 3's** is a plain unit test and will certainly run.

So: attempt to run every test you write. If the stack cannot start, say so **plainly and prominently** in your report, show the failure, and fall back to proving the code compiles (`compileTestJava` / `test-compile`). **Never describe an unrun test as passing.** A test that only runs in CI is still worth writing; a test reported green that never executed is worse than no test at all.

---

## Why Task 1 exists, in one paragraph

`ProvisionalBookingIT.shouldHoldEverySessionOfAMultiSlotBookingAndDecrementEachExactlyOnceOnConfirm` reserves two sessions and then confirms with `PUT /hearingslots` (`update.hearing.slots`), asserting *"a reservation-backed booking must be confirmable through **the real share path**"*. **That claim is false.** BUG-3 established that listing's share posts `courtscheduler.list.hearings-in-sessions`, which lands on `SlotsUpdateService.listHearingSlots` → `updateListHearingSlots` — a different method, with its own release logic. The existing IT therefore proves the release on a path production does not take, while asserting in its own words that it is the one that matters. BUG-3 was found by a human noticing an unused `booking_id` column, not by any test. This task closes that hole.

---

## File Structure

| Task | Repo | File | Change |
|---|---|---|---|
| 1 | courtscheduler (Gradle) | `listingcourtscheduler-integration-test/src/test/java/uk/gov/moj/cpp/courtscheduler/integration/ProvisionalBookingIT.java` | **Modify** — 3 tests added, 1 comment corrected |
| 2 | listing (Maven) | `listing-integration-test/src/test/java/uk/gov/moj/cpp/listing/it/BookingStatusIT.java` | **Create** |
| 2 | listing | `listing-integration-test/src/test/java/uk/gov/moj/cpp/listing/utils/CourtSchedulerServiceStub.java` | **Modify** — stub helpers for the new endpoint |
| 2 | listing | `listing-integration-test/src/test/resources/endpoint.properties` | **Modify** — one URL entry |
| 3 | listing | `listing-query/listing-query-api/src/test/java/uk/gov/moj/cpp/listing/queryapi/ListingQueryApiAccessControlConfigTest.java` | **Create** |

**Build and test commands**

```bash
# Task 1 — courtscheduler, GRADLE
./gradlew :listingcourtscheduler-integration-test:compileTestJava
./gradlew :listingcourtscheduler-integration-test:integrationTest --tests '*ProvisionalBookingIT*'

# Task 2 — listing, MAVEN
mvn -o -pl listing-integration-test -am test-compile
mvn -o -pl listing-integration-test verify -Pintegration-test -Dit.test=BookingStatusIT

# Task 3 — listing, MAVEN
mvn -o -pl listing-query/listing-query-api -am test -Dtest=ListingQueryApiAccessControlConfigTest -Dsurefire.failIfNoSpecifiedTests=false
```

Check the repo's actual IT invocation before trusting the exact form above — `listingcourtscheduler-integration-test/build.gradle` defines the `integrationTest` task, and listing's IT module has its own profile. Report what actually worked.

---

### Task 1: prove the reserve-a-slot flow on the path listing really uses

**Files:** `ProvisionalBookingIT.java` only.

**Interfaces:** consumes the class's existing harness — `postCommand`, `putCommand`, `getCommand`, `getRequestParams`, `databaseSeeder.insertCourtSchedule`, `databaseReader.allocatedListings()`, `databaseReader.courtScheduleById`, and the private helpers `allocatedListingsForHearingId`, `availableSlots`, `confirmPayload`, `bookableCourtSchedule` (which seeds 5 slots). **Reuse all of them; add new helpers only where none fits.**

**Read first.** Read `ProvisionalBookingIT.java` in full — particularly the long Javadoc on the existing multi-slot test and the `confirmPayload` helper, which documents why `source` must be present. Then read `listingcourtscheduler-integration-test/src/test/resources/courtscheduler.list.hearings-in-sessions.json`: its shape is `hearingSlots[].courtScheduleIds[]`, with `bookingId` a sibling of `hearingId`, **not** inside `courtScheduleIds[]`. That nesting is the single most common way to get this wrong.

- [ ] **Step 1: Write the three tests**

Add to `ProvisionalBookingIT`. Build payloads with a small private helper rather than reusing the placeholder fixture if the placeholders do not fit — the fixture has two hearings and fixed placeholder names, which suits neither test well. Follow `confirmPayload`'s style: build the JSON in a `String.format`, and say in a comment why each field is there.

```java
    /**
     * BUG-3. The existing multi-slot test above confirms through {@code PUT /hearingslots}
     * ({@code update.hearing.slots}). Listing does not use that path at share: it posts
     * {@code courtscheduler.list.hearings-in-sessions}, which lands on
     * {@code updateListHearingSlots}. Until BUG-3 that method released only by the real hearing id,
     * so the bookingId-keyed hold survived the share and the session stayed decremented twice until
     * the 01:00 purge. This is the same arithmetic as the test above, on the path production takes.
     */
    @Test
    void shouldReleaseTheHoldWhenListingConfirmsThroughListHearingsInSessions() throws Exception {
        final String courtScheduleId = UUID.randomUUID().toString();
        final CourtSchedule session = bookableCourtSchedule(courtScheduleId);
        databaseSeeder.insertCourtSchedule(session);

        final String bookingId = reserveOneSession(courtScheduleId);
        assertThat("the pick must hold a slot", availableSlots(courtScheduleId), is(4));

        final String hearingId = UUID.randomUUID().toString();
        final Response listResponse = postCommand("/hearings",
                "application/vnd.courtscheduler.list.hearings-in-sessions+json", SYSTEM_USER_ID,
                listHearingsPayload(hearingId, bookingId, session));

        assertThat("listing's real share path must accept a bookingId: "
                + listResponse.readEntity(String.class), listResponse.getStatus(), is(OK.getStatusCode()));

        assertThat("the bookingId-keyed hold must be released on the list path",
                allocatedListingsForHearingId(bookingId), hasSize(0));

        final List<AllocatedListing> booked = allocatedListingsForHearingId(hearingId);
        assertThat(booked, hasSize(1));
        assertThat("the confirmed row must carry booking_id — it is what makes an already-shared "
                + "booking distinguishable from an expired one", booked.get(0).getBookingId(), is(bookingId));
        assertThat(booked.get(0).getExpiresAt(), is(nullValue()));

        assertThat("net effect must be exactly one decrement: 5 -> 4 (hold) -> 3 (list) -> 4 (release)",
                availableSlots(courtScheduleId), is(4));
    }

    /**
     * NEW-15. A clerk who re-picks must not hold both sessions. The re-pick comes back under the
     * same bookingId, and because a reservation's hearing_id IS its bookingId, saveBookedSlots'
     * hearing-wide release wipes the previous pick before taking the new one.
     */
    @Test
    void shouldReleaseTheFirstSessionWhenRePickingUnderTheSameBookingId() throws Exception {
        final String sessionAId = UUID.randomUUID().toString();
        final String sessionBId = UUID.randomUUID().toString();
        databaseSeeder.insertCourtSchedule(bookableCourtSchedule(sessionAId));
        databaseSeeder.insertCourtSchedule(bookableCourtSchedule(sessionBId));

        final String bookingId = reserveOneSession(sessionAId);
        assertThat(availableSlots(sessionAId), is(4));

        reserveOneSession(sessionBId, bookingId);

        assertThat("the abandoned session must get its slot back", availableSlots(sessionAId), is(5));
        assertThat("the newly picked session must be held", availableSlots(sessionBId), is(4));

        final List<AllocatedListing> held = allocatedListingsForHearingId(bookingId);
        assertThat("only the new pick may survive", held, hasSize(1));
        assertThat(held.get(0).getCourtScheduleId(), is(sessionBId));
    }

    /**
     * NEW-7a. The pre-share gate must tell an expired hold from an already-shared booking. Only an
     * end-to-end run can show the transition, because RESERVED and SHARED are produced by two
     * different rows written by two different code paths.
     */
    @Test
    void shouldReportReservedBeforeShareAndSharedAfterIt() throws Exception {
        final String courtScheduleId = UUID.randomUUID().toString();
        final CourtSchedule session = bookableCourtSchedule(courtScheduleId);
        databaseSeeder.insertCourtSchedule(session);

        final String bookingId = reserveOneSession(courtScheduleId);

        assertThat(bookingStatusOf(bookingId), is("RESERVED"));

        final String hearingId = UUID.randomUUID().toString();
        postCommand("/hearings", "application/vnd.courtscheduler.list.hearings-in-sessions+json",
                SYSTEM_USER_ID, listHearingsPayload(hearingId, bookingId, session));

        assertThat("after the share the hold is gone but the booking is not expired — reporting "
                + "anything but SHARED here is what would block a legitimate re-share",
                bookingStatusOf(bookingId), is("SHARED"));

        assertThat(bookingStatusOf(UUID.randomUUID().toString()), is("NONE"));
    }
```

You will need three private helpers, in the style of the existing ones:

- `reserveOneSession(String courtScheduleId)` → posts `POST /provisionalBooking` for one session and returns the minted bookingId.
- `reserveOneSession(String courtScheduleId, String bookingId)` → the same, with `"bookingId"` in the request body so the booking is reused.
- `listHearingsPayload(String hearingId, String bookingId, CourtSchedule session)` → `{"hearingSlots":[{"hearingId":…,"bookingId":…,"courtScheduleIds":[{"courtScheduleId":…,"hearingStartTime":…}]}]}`. **`bookingId` is a sibling of `hearingId`, never inside `courtScheduleIds[]`.**
- `bookingStatusOf(String bookingId)` → `GET /provisionalBooking/status?bookingIds=<id>` via `getCommand(getRequestParams(...))` with accept `application/vnd.courtscheduler.get.booking-status+json`, returning `bookings[0].status`.

Check the existing `courtscheduler.create.provisional.booking.json` fixture before writing `reserveOneSession` — reuse it with a replace if it fits one session.

- [ ] **Step 2: Run them**

```bash
./gradlew :listingcourtscheduler-integration-test:compileTestJava
./gradlew :listingcourtscheduler-integration-test:integrationTest --tests '*ProvisionalBookingIT*'
```

Expected: all tests pass, including the three pre-existing ones. **If the docker-compose stack will not start, stop, report exactly that with the error, and confirm `compileTestJava` succeeds.** Do not report unrun tests as passing.

If a test fails for a *product* reason rather than a harness one, that is a **finding** — report it, do not change production code to make it pass.

- [ ] **Step 3: Prove they are not vacuous** (only if the stack ran)

For `shouldReleaseTheHoldWhenListingConfirmsThroughListHearingsInSessions`, temporarily omit `bookingId` from `listHearingsPayload` and confirm the test FAILS on the hold-release assertion; restore it. Put both outputs in your report. That is what proves the test covers BUG-3 rather than merely passing.

- [ ] **Step 4: Correct the misleading comment**

In the existing multi-slot test's Javadoc, the phrase **"the real share path"** describing `PUT /hearingslots` is wrong and will mislead the next reader exactly as it misled this feature. Change that sentence to say it confirms through the `update.hearing.slots` path, and add one line noting that listing's share uses `list.hearings-in-sessions`, covered by the new test. Change nothing else about that test.

- [ ] **Step 5: Commit** — **skipped, no commits.**

---

### Task 2: prove listing's booking-status pass-through end to end

**Files:** `BookingStatusIT.java` (create), `CourtSchedulerServiceStub.java` (modify), `endpoint.properties` (modify).

**Interfaces:** consumes `POST /listing-query-api/query/api/rest/listing/bookingStatus`, request media type `application/vnd.listing.query.booking.status+json`, body `{"bookingIds":[…]}`, answering `{"bookings":[{bookingId, safeToShare, status}]}`.

**Read first.** `CourtScheduleDraftStatusIT.java` is the template — same kind of endpoint, same WireMock stubbing, same `AbstractIT`. Copy its shape, including how it reads its URL from `endpoint.properties` via `PropertyUtil` and how it posts and reads the body. Then read `CourtSchedulerServiceStub` to see how a **GET** with query parameters is stubbed (`stubFor(get(urlPathEqualTo(...)))` around lines 181-231) — the downstream call here is `GET /provisionalBooking/status?bookingIds=…`.

- [ ] **Step 1: Add the endpoint entry and the stub helpers**

In `endpoint.properties`, beside the draft-status entry:

```
listing.query.booking-status=listing-service/query/api/rest/listing/bookingStatus
```

In `CourtSchedulerServiceStub`, add a constant for `/provisionalBooking/status` and two helpers, matching the file's existing style:
- one stubbing a 200 with a supplied `bookings` array body
- one stubbing a server error, mirroring `stubSearchCourtSchedulesByIdServerError`

- [ ] **Step 2: Write the tests**

```java
    @Test
    void shouldReturnCourtschedulerStatusesVerbatim() {
        CourtSchedulerServiceStub.stubBookingStatus(
                "[{\"bookingId\":\"bk-1\",\"safeToShare\":true,\"status\":\"RESERVED\"},"
              + "{\"bookingId\":\"bk-2\",\"safeToShare\":false,\"status\":\"NONE\"}]");

        final Response response = postBookingStatusCheck("bk-1", "bk-2");

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonArray bookings = readBody(response).getJsonArray("bookings");
        assertThat(bookings.size(), is(2));
        assertThat(bookings.getJsonObject(0).getString("status"), is("RESERVED"));
        assertThat(bookings.getJsonObject(1).getString("status"), is("NONE"));
        assertThat(bookings.getJsonObject(1).getBoolean("safeToShare"), is(false));
    }

    @Test
    void shouldPassALegacyStatusThroughUntouched() {
        CourtSchedulerServiceStub.stubBookingStatus(
                "[{\"bookingId\":\"legacy-1\",\"safeToShare\":true,\"status\":\"LEGACY\"}]");

        final JsonArray bookings = readBody(postBookingStatusCheck("legacy-1")).getJsonArray("bookings");

        assertThat("a pre-reserve-a-slot magistrates draft must not be reported as expired",
                bookings.getJsonObject(0).getString("status"), is("LEGACY"));
    }

    @Test
    void shouldFailOpenWithUnknownWhenCourtschedulerErrors() {
        CourtSchedulerServiceStub.stubBookingStatusServerError();

        final Response response = postBookingStatusCheck("bk-1", "bk-2");

        assertThat("a courtscheduler outage must not fail the request", response.getStatus(), is(OK.getStatusCode()));
        final JsonArray bookings = readBody(response).getJsonArray("bookings");
        assertThat(bookings.size(), is(2));
        for (int i = 0; i < bookings.size(); i++) {
            assertThat(bookings.getJsonObject(i).getString("status"), is("UNKNOWN"));
            assertThat("blocking every share in the building during a blip is worse than letting "
                    + "an advisory check pass", bookings.getJsonObject(i).getBoolean("safeToShare"), is(true));
        }
    }
```

The third test is the one that earns its keep: `UNKNOWN` is listing's own invention for an unreachable courtscheduler, it is the branch that fires in a real outage, and today it has only mock-level coverage.

Add the `postBookingStatusCheck(String...)` and `readBody(Response)` helpers modelled on `CourtScheduleDraftStatusIT`'s equivalents.

- [ ] **Step 3: Run them**

```bash
mvn -o -pl listing-integration-test -am test-compile
mvn -o -pl listing-integration-test verify -Pintegration-test -Dit.test=BookingStatusIT
```

Expected: PASS. WireMock is already running in this environment, so these have a good chance. If the IT harness will not start, report exactly that and fall back to proving `test-compile` succeeds. **Never report an unrun test as passing.**

- [ ] **Step 4: Commit** — **skipped, no commits.**

---

### Task 3: stop RAML and access control from drifting apart

**Files:** `ListingQueryApiAccessControlConfigTest.java` (create), in `listing-query/listing-query-api/src/test/java/uk/gov/moj/cpp/listing/queryapi/`.

**Why.** Listing's query API has no config test. An action declared in the RAML with no matching Drools rule is refused at runtime for every caller, and a media-type typo 404s or 406s — in both cases with every unit test still green. NEW-10 added an endpoint whose RAML/JAX-RS/DRL agreement was verified only by eye in review.

**Read first.** `listing-command/listing-command-handler/src/test/java/uk/gov/moj/cpp/listing/command/handler/ListingCommandHandlerRamlConfigTest.java` is the nearest precedent for style — it reads the RAML as lines and compares against reflection. Yours compares two files instead, which is simpler.

**The state of the world, already measured — do not re-derive it, but do re-verify it:**
- The RAML declares **21** distinct actions (lines matching `name: <action>` inside `(mapping)` blocks).
- The DRL declares **22** rules (`Action(name == "…")`).
- Every RAML action already has a rule. The extra DRL rule is **`listing.public.list`**, which has no RAML action in this file.

**Therefore the assertion must be one-directional:** every RAML action has a Drools rule. Asserting set equality would fail immediately on `listing.public.list`, for a pre-existing reason unrelated to this feature — a test that fails on arrival gets disabled, not fixed.

- [ ] **Step 1: Write the test**

```java
    private static final String PATH_TO_RAML = "src/raml/listing-query-api.raml";
    private static final String PATH_TO_DRL =
            "src/main/resources/uk/gov/moj/cpp/listing/queryapi/accesscontrol/listing-query-api.drl";

    /**
     * Every action the query API declares must have an access-control rule. Without one the
     * framework refuses the request for every caller, at runtime, with every unit test green.
     *
     * <p>Deliberately one-directional. The DRL legitimately carries rules with no action in this
     * RAML — {@code listing.public.list} is one — so asserting set equality would fail on arrival
     * for a pre-existing reason, and a test that fails on arrival gets disabled rather than fixed.
     */
    @Test
    public void everyRamlActionHasAnAccessControlRule() throws Exception {
        final Set<String> ramlActions = ramlActionNames();
        final Set<String> ruleActions = drlActionNames();

        assertThat("the RAML parse found no actions - the extraction is broken, not the config",
                ramlActions, is(not(empty())));

        final Set<String> missing = new TreeSet<>(ramlActions);
        missing.removeAll(ruleActions);

        assertThat("query API actions with no access-control rule - every caller gets refused at "
                + "runtime: " + missing, missing, is(empty()));
    }
```

Extract with explicit, commented regexes — `name:\s*(\S+)` on lines inside the RAML, and `Action\(name == "([^"]+)"\)` on the DRL. Filter the RAML matches to those starting with the context prefix `listing.` so unrelated `name:` keys elsewhere in the RAML cannot pollute the set.

The `ramlActions is not empty` assertion matters more than it looks: if the RAML format shifts and the regex silently matches nothing, `missing` is empty too and the test passes forever while checking nothing.

- [ ] **Step 2: Run it, and prove it is not vacuous**

```bash
mvn -o -pl listing-query/listing-query-api -am test -Dtest=ListingQueryApiAccessControlConfigTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS — the config is currently correct, including NEW-10's new rule.

Then prove it bites: temporarily comment out the `listing.query.booking.status` rule in the DRL, re-run, and confirm it FAILS naming that action. Restore the rule. Put both outputs in your report, and confirm with `git diff` that the DRL is back exactly as you found it.

- [ ] **Step 3: Commit** — **skipped, no commits.**

---

## Deliberately not in scope

- **Any production code change.** These three tasks are coverage and guard-rails. A test that cannot pass without touching production code has found a defect — report it as a finding.
- **A `bookingId`-aware IT in `HearingSlotIT`** — it exercises `list.hearings-in-sessions` with pre-seeded rows and no reservation. Leave it; Task 1 covers the reservation case in the file where the reservation harness already lives.
- **FE tests.** BUG-4, NEW-11…14 are deferred by standing instruction.
- **The purge job (LPT-2433 / LPT-2506)** — unblocked by BUG-1 and ready for its own retest, but that is a QA activity, not code.
