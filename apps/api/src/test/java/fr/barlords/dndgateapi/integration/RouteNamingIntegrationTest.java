package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The routes are the contract of the front (and of the client generated from it): nouns in lower-case kebab-case,
 * the HTTP method says the action. No {@code getByAdventureId}, no {@code setStatus}.
 */
class RouteNamingIntegrationTest extends ApiIntegrationTestSupport {

    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void every_segment_of_every_route_is_lower_case_kebab_case() {
        TreeSet<String> offenders = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(mapping -> mapping.getPathPatternsCondition().getPatterns()
                .forEach(pattern -> pattern.getPatternString().lines().forEach(path -> {
                    if (path.startsWith("/v3/")) {
                        return; // the OpenAPI document, served by springdoc
                    }
                    for (String segment : path.split("/")) {
                        boolean variable = segment.startsWith("{") && segment.endsWith("}");
                        if (!segment.isEmpty() && !variable && !KEBAB_CASE.matcher(segment).matches()) {
                            offenders.add(path);
                        }
                    }
                })));

        Assertions.assertEquals(new TreeSet<String>(), offenders, "routes that are not lower-case kebab-case");
    }

    @Test
    void no_route_has_the_name_of_an_action_the_method_already_says() {
        TreeSet<String> offenders = new TreeSet<>();
        Pattern verbPrefix = Pattern.compile("(get|set|add|remove|send|create|update|delete)(-.*)?");
        handlerMapping.getHandlerMethods().keySet().forEach(mapping -> mapping.getPathPatternsCondition().getPatterns()
                .forEach(pattern -> {
                    for (String segment : pattern.getPatternString().split("/")) {
                        if (verbPrefix.matcher(segment).matches()) {
                            offenders.add(pattern.getPatternString());
                        }
                    }
                }));

        Assertions.assertEquals(new TreeSet<String>(), offenders);
    }

    // ---- the old names are gone (nobody consumes them yet, so there is no alias) ----

    private void assertGone(HttpMethod method, String path) {
        TestAccount account = registerAndLogin();
        ResponseEntity<String> response = call(method, path, account.token(), method == HttpMethod.GET ? null : Map.of());

        int status = response.getStatusCode().value();
        String body = String.valueOf(response.getBody());
        // 404 "no resource" or 405: the route is not there. A 400 "UUID is not valid" means the old name now falls
        // on a sibling route taking an id (/adventures/{id}): it is read as a bad id, not served.
        boolean routeIsGone = (status == 404 && body.contains("No static resource")) || status == 405
                || (status == 400 && body.contains("UUID is not valid"));
        Assertions.assertTrue(routeIsGone, method + " " + path + " still answers " + status + ": " + body);
    }

    @Test
    void the_old_routes_do_not_exist_any_more() {
        UUID id = UUID.randomUUID();
        List<String[]> gone = List.of(
                new String[]{"POST", "/accounts/log"},
                new String[]{"GET", "/adventures/getWhereImPlayer"},
                new String[]{"GET", "/adventures/getWhereImMaster"},
                new String[]{"PATCH", "/adventures/" + id + "/setVisibility"},
                new String[]{"PATCH", "/adventures/" + id + "/setStatus"},
                new String[]{"POST", "/adventures/" + id + "/sendInvitationCode/someone"},
                new String[]{"POST", "/adventures/joinByInvitationCode"},
                new String[]{"DELETE", "/adventures/" + id + "/quit"},
                new String[]{"GET", "/adventure-notes/getByAdventureId/" + id},
                new String[]{"GET", "/historical-notes/getByAdventureId/" + id},
                new String[]{"GET", "/messages/getByAdventureId"},
                new String[]{"GET", "/game-characters/getByAdventureId/" + id},
                new String[]{"GET", "/game-characters/getByOwnerId/" + id},
                new String[]{"GET", "/game-characters/getMyGameCharacters"},
                new String[]{"GET", "/spellbooks/getByOwnerId/" + id},
                new String[]{"GET", "/spellbooks/getMySpellBooks"},
                new String[]{"POST", "/spellbooks/" + id + "/addSpell"},
                new String[]{"DELETE", "/spellbooks/" + id + "/removeSpell/0"},
                new String[]{"GET", "/ratings/getByTargetedAccount"},
                new String[]{"GET", "/ratings/getByEmitterAndTargetedAccount"},
                new String[]{"GET", "/ratings/getMeanRateOfAccount"},
                new String[]{"GET", "/planning/availability/" + id},
                new String[]{"GET", "/planning/adventure/" + id});

        gone.forEach(route -> assertGone(HttpMethod.valueOf(route[0]), route[1]));
    }
}
