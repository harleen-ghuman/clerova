package com.clerova.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtractionRequest(
        @NotBlank String text
) {

}
