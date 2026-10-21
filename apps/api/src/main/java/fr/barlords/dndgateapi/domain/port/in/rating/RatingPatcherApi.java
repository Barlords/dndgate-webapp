package fr.barlords.dndgateapi.domain.port.in.rating;

import fr.barlords.dndgateapi.domain.functional.model.Rating;

import java.util.UUID;

public interface RatingPatcherApi {

    Rating patch(UUID accountId, Rating rating);

}
