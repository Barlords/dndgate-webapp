package fr.barlords.dndgateapi.client.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;

/** The answer to a signup request: the same text whatever happened, so that it tells nothing about the address. */
public record SignupAcceptedDto(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {

    public static final SignupAcceptedDto INSTANCE =
            new SignupAcceptedDto("One last step before your account is validated: check your e-mail address");

}
