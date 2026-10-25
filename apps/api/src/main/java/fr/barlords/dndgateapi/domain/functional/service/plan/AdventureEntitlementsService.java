package fr.barlords.dndgateapi.domain.functional.service.plan;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.AdventureEntitlements;
import fr.barlords.dndgateapi.domain.functional.model.plan.LimitUsage;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.AdventureEntitlementsApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class AdventureEntitlementsService implements AdventureEntitlementsApi {

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;
    private final EntitlementApi entitlementApi;
    private final GameCharacterPersistenceSpi gameCharacters;
    private final MessagePersistenceSpi messages;
    private final AdventureNotePersistenceSpi adventureNotes;
    private final HistoricalNotePersistenceSpi historicalNotes;

    @Override
    public AdventureEntitlements of(UUID accountId, UUID adventureId) {
        Adventure adventure = adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);
        Plan masterPlan = entitlementApi.masterPlanOf(adventure);

        Map<PlanLimit, LimitUsage> limits = new EnumMap<>(PlanLimit.class);
        for (PlanLimit limit : PlanLimit.values()) {
            if (limit.scope() == PlanLimit.Scope.ADVENTURE) {
                limits.put(limit, new LimitUsage(used(limit, accountId, adventureId), masterPlan.limit(limit)));
            }
        }
        return new AdventureEntitlements(entitlementApi.availableFeatures(accountId, adventure), limits);
    }

    private long used(PlanLimit limit, UUID accountId, UUID adventureId) {
        return switch (limit) {
            case CHARACTERS_PER_PLAYER_AND_ADVENTURE -> gameCharacters.countByOwnerIdAndAdventureId(accountId, adventureId);
            case MESSAGES_PER_ADVENTURE -> messages.countByAdventureId(adventureId);
            case MASTER_NOTES_PER_ADVENTURE -> adventureNotes.countByAdventureId(adventureId);
            case HISTORICAL_NOTES_PER_ADVENTURE -> historicalNotes.countByAdventureId(adventureId);
            // Non-player characters arrive with their own task: nothing is used until then
            case NPCS_PER_ADVENTURE -> 0;
            default -> throw new IllegalStateException("Not an adventure limit: " + limit);
        };
    }
}
