package fr.barlords.dndgateapi.client.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A date a person can mean: between the years 1900 and 2200. The format accepts years such as
 * +999999999, which PostgreSQL cannot store (the request would end as a 500); a null value is left to {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = DateValidator.ForDate.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDate {

    String message() default "the date must be between the years 1900 and 2200";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
