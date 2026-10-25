package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.plan.FeatureNotIncludedException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanErrorHandlerTest {

    private final PlanErrorHandler handler = new PlanErrorHandler();

    @Test
    void a_reached_limit_answers_409_with_the_limit_and_the_upgrade() {
        ProblemDetail problem = handler.handlePlanLimitReachedException(
                new PlanLimitReachedException(PlanLimit.SPELL_BOOKS, 2, "adventurer", "hero"));

        assertEquals(409, problem.getStatus());
        assertEquals("urn:dndgate:plan-limit-reached", problem.getType().toString());
        assertEquals("spellBooks", problem.getProperties().get("limit"));
        assertEquals(2, problem.getProperties().get("max"));
        assertEquals("adventurer", problem.getProperties().get("planId"));
        assertEquals("hero", problem.getProperties().get("upgradePlanId"));
    }

    @Test
    void a_missing_feature_answers_403_and_keeps_an_empty_upgrade() {
        ProblemDetail problem = handler.handleFeatureNotIncludedException(
                new FeatureNotIncludedException(Feature.VIDEO, "legendary", null));

        assertEquals(403, problem.getStatus());
        assertEquals("urn:dndgate:feature-not-included", problem.getType().toString());
        assertEquals("video", problem.getProperties().get("feature"));
        assertEquals("legendary", problem.getProperties().get("planId"));
        assertTrue(problem.getProperties().containsKey("upgradePlanId"));
        assertNull(problem.getProperties().get("upgradePlanId"));
    }
}
