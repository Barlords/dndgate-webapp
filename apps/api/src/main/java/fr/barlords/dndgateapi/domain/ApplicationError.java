package fr.barlords.dndgateapi.domain;

public record ApplicationError(String context, String message, Object value, Throwable cause) {}
