package fr.barlords.dndgateapi.client.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DateValidator {

    public static final LocalDate MIN = LocalDate.of(1900, 1, 1);

    public static final LocalDate MAX = LocalDate.of(2200, 12, 31);

    private DateValidator() {
    }

    static boolean inRange(LocalDate date) {
        return !date.isBefore(MIN) && !date.isAfter(MAX);
    }

    public static class ForDate implements ConstraintValidator<ValidDate, LocalDate> {

        @Override
        public boolean isValid(LocalDate date, ConstraintValidatorContext context) {
            return date == null || inRange(date);
        }
    }

    public static class ForDateTime implements ConstraintValidator<ValidDate, LocalDateTime> {

        @Override
        public boolean isValid(LocalDateTime dateTime, ConstraintValidatorContext context) {
            return dateTime == null || inRange(dateTime.toLocalDate());
        }
    }

}
