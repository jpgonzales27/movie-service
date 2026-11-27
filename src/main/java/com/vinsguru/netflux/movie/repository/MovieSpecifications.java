package com.vinsguru.netflux.movie.repository;

import com.vinsguru.netflux.movie.entity.Movie;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Objects;

public class MovieSpecifications {

    private static final String DELIMITER = "\u001F";
    private static final char ESCAPE = '\\';

    public static Specification<Movie> hasTitle(String title) {
        return (root, query, cb) ->
                Objects.isNull(title) ? cb.conjunction() : containsIgnoreCase(cb, root.get("title"), title);
    }

    public static Specification<Movie> hasDirector(String director) {
        return (root, query, cb) ->
                Objects.isNull(director) ? cb.conjunction() : containsIgnoreCase(cb, root.get("director"), director);
    }

    /*
     * Exact, case-insensitive array membership. array_position is case-sensitive and Hibernate rejects
     * a derived (lower-cased) array as its argument, so the array is joined into a delimited, lower-cased
     * string and matched against the delimited genre instead.
     */
    public static Specification<Movie> hasGenre(String genre) {
        return (root, query, cb) -> {
            if (Objects.isNull(genre)) {
                return cb.conjunction();
            }
            var joined = cb.function("array_to_string", String.class, root.get("genres"), cb.literal(DELIMITER));
            var delimited = cb.concat(cb.concat(DELIMITER, cb.lower(joined)), DELIMITER);
            var pattern = "%" + DELIMITER + escapeLike(genre.toLowerCase()) + DELIMITER + "%";
            return cb.like(delimited, pattern, ESCAPE);
        };
    }

    private static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> column, String value) {
        return cb.like(cb.lower(column), "%" + escapeLike(value.toLowerCase()) + "%", ESCAPE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
