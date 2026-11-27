package com.vinsguru.netflux.movie.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Objects;

public record MovieSearchRequest(String title,
                                 String genre,
                                 String director,
                                 @Min(1) @Max(100) Integer limit) {

    private static final int DEFAULT_LIMIT = 20;

    public MovieSearchRequest {
        title = blankToNull(title);
        genre = blankToNull(genre);
        director = blankToNull(director);
        limit = Objects.isNull(limit) ? DEFAULT_LIMIT : limit;
    }

    @AssertTrue(message = "At least one of title, genre or director is required")
    public boolean isAtLeastOnePresent() {
        return Objects.nonNull(title) || Objects.nonNull(genre) || Objects.nonNull(director);
    }

    private static String blankToNull(String value) {
        return Objects.isNull(value) || value.isBlank() ? null : value.strip();
    }
}
