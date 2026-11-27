package com.vinsguru.netflux.movie.service;

import com.vinsguru.netflux.movie.dto.MovieDetails;
import com.vinsguru.netflux.movie.dto.MovieSearchRequest;
import com.vinsguru.netflux.movie.dto.MovieSummary;
import com.vinsguru.netflux.movie.entity.Movie;
import com.vinsguru.netflux.movie.exceptions.MovieNotFoundException;
import com.vinsguru.netflux.movie.mapper.MovieMapper;
import com.vinsguru.netflux.movie.repository.MovieRepository;
import com.vinsguru.netflux.movie.repository.MovieSpecifications;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MovieService {

    private static final Sort BY_VOTE_COUNT = Sort.by(
            Sort.Order.desc("voteCount").nullsLast(),
            Sort.Order.asc("id")
    );
    private static final Sort BY_RELEASE_DATE = Sort.by(
            Sort.Order.desc("releaseDate").nullsLast(),
            Sort.Order.asc("id")
    );

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public MovieDetails findById(Long id) {
        return movieRepository.findById(id)
                .map(MovieMapper::toDetails)
                .orElseThrow(() -> new MovieNotFoundException(id));
    }

    public List<MovieSummary> findByIds(List<Long> ids) {
        var distinctIds = ids.stream()
                .distinct()
                .toList();
        var moviesById = movieRepository.findAllById(distinctIds)
                .stream()
                .collect(Collectors.toMap(Movie::getId, Function.identity()));
        return distinctIds.stream()
                .filter(moviesById::containsKey)
                .map(moviesById::get)
                .map(MovieMapper::toSummary)
                .toList();
    }

    public List<MovieSummary> findPopular(int limit) {
        return toSummaries(movieRepository.findAllBy(BY_VOTE_COUNT, Limit.of(limit)));
    }

    public List<MovieSummary> findLatest(int limit) {
        return toSummaries(movieRepository.findAllBy(BY_RELEASE_DATE, Limit.of(limit)));
    }

    public List<MovieSummary> search(MovieSearchRequest request) {
        var spec = Specification.allOf(
                MovieSpecifications.hasTitle(request.title()),
                MovieSpecifications.hasGenre(request.genre()),
                MovieSpecifications.hasDirector(request.director())
        );
        var movies = movieRepository.findBy(spec, query -> query.sortBy(BY_VOTE_COUNT)
                .limit(request.limit())
                .all());
        return toSummaries(movies);
    }

    private List<MovieSummary> toSummaries(List<Movie> movies) {
        return movies.stream()
                .map(MovieMapper::toSummary)
                .toList();
    }
}
