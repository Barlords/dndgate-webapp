package fr.barlords.dndgateapi.client.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ValidationErrorResponse {
    private String field;
    private String message;
}
