package fr.barlords.dndgateapi.domain.port.in.rating;

import fr.barlords.dndgateapi.domain.functional.model.Rating;

import java.util.UUID;

public interface RatingCreatorApi {

    Rating create(UUID accountId, Rating rating);

}
