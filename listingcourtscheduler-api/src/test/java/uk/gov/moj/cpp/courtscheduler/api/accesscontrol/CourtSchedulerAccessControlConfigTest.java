package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Guards against the courtscheduler API's RAML and its Drools access-control rules drifting
 * apart.
 *
 * <p>The RAML declares the actions the API accepts; the DRL grants access to them. Nothing else
 * checks that every RAML action has a matching rule. An action declared in the RAML with no rule
 * is refused at runtime for every caller - including the system user - and every unit test still
 * passes. That is exactly how {@code courtscheduler.get.booking-status} went unreachable earlier
 * today: declared in RAML, implemented correctly, no access-control rule, 403 for everyone,
 * approved twice in review because nothing machine-checked the RAML/DRL correspondence.
 */
public class CourtSchedulerAccessControlConfigTest {

    private static final String PATH_TO_RAML = "src/raml/courtscheduler-api.raml";
    private static final String PATH_TO_DRL =
            "src/main/resources/uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl";

    private static final String CONTEXT_PREFIX = "courtscheduler.";

    /**
     * Actions with no name-keyed access-control rule, allowlisted with the reason. Every entry
     * here MUST be a deliberate, documented exception - not a place to quietly grow the list.
     *
     * <p>{@code courtscheduler.get.court_schedule} is covered by the rule named
     * {@code "API - Action - courtscheduler.get.court_schedule"} in the DRL, but that rule's
     * condition is keyed on {@code Action(name == "courtscheduler.get")} - a shorter, different
     * literal - not on the RAML action name itself. So the regex this test uses to extract DRL
     * action names never sees {@code courtscheduler.get.court_schedule}, even though the rule is
     * live and does cover it.
     *
     * <p>The remaining nine {@code courtscheduler.judiciary.*.availability*} actions are each
     * covered by a rule keyed on a REST path instead of the RAML action name, e.g.
     * {@code Action(name == "POST /judiciaries/availability-rules/add")} for
     * {@code courtscheduler.judiciary.add.availability.rule}. Verified pairing, read directly
     * from the DRL rule names and their {@code Action(name == "...")} conditions:
     * <ul>
     *   <li>{@code courtscheduler.judiciary.add.availability.rule} -
     *       {@code POST /judiciaries/availability-rules/add}</li>
     *   <li>{@code courtscheduler.judiciary.update.availability.rule} -
     *       {@code POST /judiciaries/availability-rules/update}</li>
     *   <li>{@code courtscheduler.judiciary.add.availability.rule.validate} -
     *       {@code POST /judiciaries/availability-rules/validate-add}</li>
     *   <li>{@code courtscheduler.judiciary.update.availability.rule.validate} -
     *       {@code POST /judiciaries/availability-rules/validate-update}</li>
     *   <li>{@code courtscheduler.judiciary.delete.availability.rule} -
     *       {@code POST /judiciaries/availability-rules/delete}</li>
     *   <li>{@code courtscheduler.judiciary.delete.availability.rule.validate} -
     *       {@code POST /judiciaries/availability-rules/validate-delete}</li>
     *   <li>{@code courtscheduler.judiciary.find.availability} - {@code GET /judiciaries}</li>
     *   <li>{@code courtscheduler.judiciary.find.availability.rule} -
     *       {@code GET /judiciaries/availability-rules}</li>
     *   <li>{@code courtscheduler.judiciary.get.availability.rule} -
     *       {@code GET /judiciaries/availability-rules/{ruleId}}</li>
     * </ul>
     *
     * <p>None of these ten is a gap: each is exempted here only because its rule is keyed on a
     * different literal to the one this test extracts from the RAML, not because access control
     * is missing.
     */
    private static final Set<String> ALLOWLISTED_ACTIONS_WITH_NO_NAME_KEYED_RULE = Set.of(
            "courtscheduler.get.court_schedule",
            "courtscheduler.judiciary.add.availability.rule",
            "courtscheduler.judiciary.update.availability.rule",
            "courtscheduler.judiciary.add.availability.rule.validate",
            "courtscheduler.judiciary.update.availability.rule.validate",
            "courtscheduler.judiciary.delete.availability.rule",
            "courtscheduler.judiciary.delete.availability.rule.validate",
            "courtscheduler.judiciary.find.availability",
            "courtscheduler.judiciary.find.availability.rule",
            "courtscheduler.judiciary.get.availability.rule"
    );

    // Matches a RAML "name: <value>" mapping entry, e.g. "          name: courtscheduler.create"
    private static final Pattern RAML_NAME_PATTERN = Pattern.compile("name:\\s*(\\S+)");

    // Matches a Drools access-control rule condition, e.g. Action(name == "courtscheduler.create")
    private static final Pattern DRL_ACTION_PATTERN = Pattern.compile("Action\\(name == \"([^\"]+)\"\\)");

    /**
     * Every action the API declares must have a name-keyed access-control rule, unless it is
     * explicitly allowlisted above with a reason. Without one the framework refuses the request
     * for every caller, at runtime, with every unit test green.
     *
     * <p>Deliberately one-directional. The DRL legitimately carries rules with no action in this
     * RAML - e.g. {@code courtscheduler.extend.multiday.hearing} - and rules keyed on REST paths
     * rather than action names, so asserting set equality would fail on arrival for pre-existing
     * reasons, and a test that fails on arrival gets disabled rather than fixed.
     */
    @Test
    public void everyRamlActionHasAnAccessControlRule() throws Exception {
        final Set<String> ramlActions = ramlActionNames();
        final Set<String> ruleActions = drlActionNames();

        assertThat("the RAML parse found no actions - the extraction is broken, not the config",
                ramlActions, is(not(empty())));

        final Set<String> missing = new TreeSet<>(ramlActions);
        missing.removeAll(ruleActions);
        missing.removeAll(ALLOWLISTED_ACTIONS_WITH_NO_NAME_KEYED_RULE);

        assertThat("courtscheduler API actions with no access-control rule - every caller gets "
                + "refused at runtime: " + missing, missing, is(empty()));
    }

    /**
     * Guards against the allowlist above going stale - e.g. if
     * {@code courtscheduler.get.court_schedule} ever gained its own name-keyed rule, it must be
     * removed from the allowlist, or this test would keep passing while silently hiding the fact
     * that the allowlist no longer matches reality.
     */
    @Test
    public void allowlistedActionsAreStillWithoutARuleAndStillDeclaredInRaml() throws Exception {
        final Set<String> ramlActions = ramlActionNames();
        final Set<String> ruleActions = drlActionNames();

        for (final String allowlisted : ALLOWLISTED_ACTIONS_WITH_NO_NAME_KEYED_RULE) {
            assertThat("allowlisted action '" + allowlisted + "' is no longer declared in the "
                    + "RAML - remove it from the allowlist", ramlActions, hasItem(allowlisted));
            assertThat("allowlisted action '" + allowlisted + "' now has a name-keyed "
                    + "access-control rule - remove it from the allowlist, it is no longer a gap",
                    ruleActions, is(not(hasItem(allowlisted))));
        }
    }

    private Set<String> ramlActionNames() throws Exception {
        final Set<String> names = new TreeSet<>();
        for (final String line : readLines(PATH_TO_RAML)) {
            final Matcher matcher = RAML_NAME_PATTERN.matcher(line);
            if (matcher.find()) {
                final String name = matcher.group(1);
                if (name.startsWith(CONTEXT_PREFIX)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private Set<String> drlActionNames() throws Exception {
        final Set<String> names = new TreeSet<>();
        final String content = withoutComments(readFile(PATH_TO_DRL));
        final Matcher matcher = DRL_ACTION_PATTERN.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * Drools honours both {@code //} line comments and {@code /* ... *}{@code /} block comments -
     * a rule hidden behind either is disabled at runtime exactly as if it were deleted, so it
     * must not count as an access-control rule here. Without this, commenting out a rule to
     * "temporarily" disable it would leave this guard green while the action it covered is
     * refused for everyone. Strip block comments first (they can span, and hide, whole {@code //}
     * lines), then line comments, then match what's left.
     */
    private String withoutComments(final String drlContent) {
        final String withoutBlockComments = drlContent.replaceAll("(?s)/\\*.*?\\*/", "");
        return withoutBlockComments.replaceAll("//[^\\n]*", "");
    }

    private List<String> readLines(final String path) throws Exception {
        return Files.readAllLines(new File(path).toPath(), StandardCharsets.UTF_8);
    }

    private String readFile(final String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }
}
