package fr.barlords.dndgateapi.domain.port.in.rating;

import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.Upserted;

import java.util.UUID;

public interface RatingUpdaterApi {

    Upserted<Rating> createOrUpdate(UUID accountId, Rating rating);

}
