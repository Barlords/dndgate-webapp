package fr.barlords.dndgateapi.client.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    public static final int MIN_LENGTH = 8;

    public static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // required-ness is the job of @NotNull
        }

        return password.codePointCount(0, password.length()) >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && !password.isBlank();
    }

}
