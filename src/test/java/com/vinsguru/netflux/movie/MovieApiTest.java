package com.vinsguru.netflux.movie;

import com.vinsguru.netflux.movie.dto.MovieDetails;
import com.vinsguru.netflux.movie.dto.MovieSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@Sql(scripts = "/sql/insert-movies.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/delete-movies.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MovieApiTest {

    @Autowired
    RestTestClient restTestClient;

    @Test
    @DisplayName("Should return full movie details by ID")
    void shouldReturnMovieDetailsById() {
        var movie = restTestClient.get()
                .uri("/api/movies/{id}", 1)
                .exchange()
                .expectStatus().isOk()
                .expectBody(MovieDetails.class)
                .returnResult()
                .getResponseBody();

        assertThat(movie).isEqualTo(new MovieDetails(
                1L,
                "The Godfather",
                8.7,
                20000,
                LocalDate.of(1972, 3, 14),
                245066411L,
                175,
                "/bd1.jpg",
                6000000L,
                "https://example.com/godfather",
                "Mafia family saga.",
                90.5,
                "/p1.jpg",
                "Francis Ford Coppola",
                List.of("Drama", "Crime"),
                List.of("Family", "Power")
        ));
    }

    @Test
    @DisplayName("Should return 404 ProblemDetail when movie ID does not exist")
    void shouldReturn404WhenMovieNotFound() {
        var problem = restTestClient.get()
                .uri("/api/movies/{id}", 999)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Movie not found: 999");
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when movie ID is not numeric")
    void shouldReturn400WhenIdIsNotNumeric() {
        assertBadRequest("/api/movies/abc");
    }

    @Test
    @DisplayName("Should return batch movies in request order, skipping unknown and duplicate IDs")
    void shouldReturnBatchInRequestOrder() {
        var ids = getSummaryIds("/api/movies/batch?ids=4,999,1,4");

        assertThat(ids).containsExactly(4L, 1L);
    }

    @Test
    @DisplayName("Should return empty list when none of the batch IDs exist")
    void shouldReturnEmptyListWhenNoBatchIdsFound() {
        var ids = getSummaryIds("/api/movies/batch?ids=998,999");

        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when batch IDs are missing")
    void shouldReturn400WhenBatchIdsMissing() {
        assertBadRequest("/api/movies/batch");
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when more than 100 batch IDs are requested")
    void shouldReturn400WhenBatchExceedsMaxIds() {
        var ids = LongStream.rangeClosed(1, 101)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        assertBadRequest("/api/movies/batch?ids=" + ids);
    }

    @Test
    @DisplayName("Should return popular movies by vote count, ties by ID and nulls last")
    void shouldReturnPopularMovies() {
        var ids = getSummaryIds("/api/movies/popular");

        assertThat(ids).containsExactly(4L, 3L, 1L, 2L, 6L, 5L);
    }

    @Test
    @DisplayName("Should limit popular movies")
    void shouldLimitPopularMovies() {
        var ids = getSummaryIds("/api/movies/popular?limit=2");

        assertThat(ids).containsExactly(4L, 3L);
    }

    @ParameterizedTest
    @CsvSource({"popular, 0", "popular, 101", "latest, 0", "latest, 101"})
    @DisplayName("Should return 400 ProblemDetail when limit is out of range")
    void shouldReturn400WhenLimitOutOfRange(String endpoint, int limit) {
        assertBadRequest("/api/movies/" + endpoint + "?limit=" + limit);
    }

    @Test
    @DisplayName("Should return latest movies by release date, ties by ID and nulls last")
    void shouldReturnLatestMovies() {
        var ids = getSummaryIds("/api/movies/latest");

        assertThat(ids).containsExactly(4L, 6L, 3L, 2L, 1L, 5L);
    }

    @Test
    @DisplayName("Should search by partial title ignoring case")
    void shouldSearchByTitle() {
        var ids = getSummaryIds("/api/movies/search?title=GODFATHER");

        assertThat(ids).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Should treat LIKE wildcards in the title as literal characters")
    void shouldTreatTitleWildcardsLiterally() {
        var ids = getSummaryIds("/api/movies/search?title=god_ather");

        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("Should search by partial director ignoring case")
    void shouldSearchByDirector() {
        var ids = getSummaryIds("/api/movies/search?director=nolan");

        assertThat(ids).containsExactly(4L, 3L);
    }

    @Test
    @DisplayName("Should search by exact genre ignoring case")
    void shouldSearchByGenreIgnoringCase() {
        var ids = getSummaryIds("/api/movies/search?genre=crime");

        assertThat(ids).containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("Should not match a partial genre")
    void shouldNotMatchPartialGenre() {
        var ids = getSummaryIds("/api/movies/search?genre=crim");

        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("Should combine search filters with AND logic")
    void shouldCombineSearchFilters() {
        var ids = getSummaryIds("/api/movies/search?genre=action&director=nolan&title=dark");

        assertThat(ids).containsExactly(3L);
    }

    @Test
    @DisplayName("Should limit search results")
    void shouldLimitSearchResults() {
        var ids = getSummaryIds("/api/movies/search?director=nolan&limit=1");

        assertThat(ids).containsExactly(4L);
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when no search parameter is provided")
    void shouldReturn400WhenNoSearchParams() {
        assertBadRequest("/api/movies/search");
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when all search parameters are blank")
    void shouldReturn400WhenSearchParamsBlank() {
        assertBadRequest("/api/movies/search?title={title}&genre={genre}", "", " ");
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when search limit is out of range")
    void shouldReturn400WhenSearchLimitOutOfRange() {
        assertBadRequest("/api/movies/search?title=god&limit=101");
    }

    private List<Long> getSummaryIds(String uri) {
        var movies = restTestClient.get()
                .uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<MovieSummary>>() {})
                .returnResult()
                .getResponseBody();
        return movies.stream()
                .map(MovieSummary::id)
                .toList();
    }

    private void assertBadRequest(String uri, Object... uriVariables) {
        var problem = restTestClient.get()
                .uri(uri, uriVariables)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(400);
    }
}
