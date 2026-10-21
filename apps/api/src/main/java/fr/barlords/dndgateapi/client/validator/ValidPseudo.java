package fr.barlords.dndgateapi.client.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A pseudo: 3 to 30 characters, made of letters (any language), digits, "_", "-" and ".".
 * It is shown to other players and written in e-mails, so it cannot carry markup, spaces inside, or control characters.
 * Spaces around it are tolerated, they are removed afterwards.
 */
@Documented
@Constraint(validatedBy = {})
@ReportAsSingleViolation
// 3 to 30 characters once the spaces around are removed; the limit on the raw value only stops absurd padding
@Size(max = 60)
@Pattern(regexp = "^\\s*[\\p{L}\\p{N}_.\\-]{3,30}\\s*$")
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPseudo {

    String message() default "le pseudo ne peut contenir que des lettres, des chiffres, '_', '-' et '.', sans espace";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
