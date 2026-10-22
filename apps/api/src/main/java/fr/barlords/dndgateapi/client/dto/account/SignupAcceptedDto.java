package fr.barlords.dndgateapi.client.dto.account;

/** The answer to a signup request: the same text whatever happened, so that it tells nothing about the address. */
public record SignupAcceptedDto(String message) {

    public static final SignupAcceptedDto INSTANCE =
            new SignupAcceptedDto("One last step before your account is validated: check your e-mail address");

}
