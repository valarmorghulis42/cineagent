package com.cineagent.catalog.service;

import com.cineagent.catalog.api.dto.CityDtos.CityRequest;
import com.cineagent.catalog.api.dto.MovieDtos.MovieRequest;
import com.cineagent.catalog.api.dto.ScreenDtos.ScreenRequest;
import com.cineagent.catalog.api.dto.TheaterDtos.TheaterRequest;
import com.cineagent.catalog.domain.City;
import com.cineagent.catalog.domain.Movie;
import com.cineagent.catalog.domain.Screen;
import com.cineagent.catalog.domain.Theater;
import com.cineagent.catalog.repository.CityRepository;
import com.cineagent.catalog.repository.MovieRepository;
import com.cineagent.catalog.repository.ScreenRepository;
import com.cineagent.catalog.repository.TheaterRepository;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD + public browse for reference data: cities, theaters, screens, movies. */
@Service
@Transactional
public class CatalogService {

  private final CityRepository cityRepository;
  private final TheaterRepository theaterRepository;
  private final ScreenRepository screenRepository;
  private final MovieRepository movieRepository;

  public CatalogService(
      CityRepository cityRepository,
      TheaterRepository theaterRepository,
      ScreenRepository screenRepository,
      MovieRepository movieRepository) {
    this.cityRepository = cityRepository;
    this.theaterRepository = theaterRepository;
    this.screenRepository = screenRepository;
    this.movieRepository = movieRepository;
  }

  // --- City ---

  public City createCity(CityRequest req) {
    return cityRepository.save(new City(req.name(), req.state(), req.country(), req.timezone()));
  }

  public City updateCity(Long id, CityRequest req) {
    City city = getCity(id);
    city.update(req.name(), req.state(), req.country(), req.timezone());
    return city;
  }

  @Transactional(readOnly = true)
  public City getCity(Long id) {
    return cityRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, id));
  }

  @Transactional(readOnly = true)
  public Page<City> listActiveCities(Pageable pageable) {
    return cityRepository.findByActiveTrue(pageable);
  }

  public void deactivateCity(Long id) {
    getCity(id).setActive(false);
  }

  // --- Theater ---

  public Theater createTheater(TheaterRequest req) {
    City city = getCity(req.cityId());
    return theaterRepository.save(new Theater(city, req.name(), req.address()));
  }

  public Theater updateTheater(Long id, TheaterRequest req) {
    Theater theater = getTheater(id);
    theater.update(req.name(), req.address());
    return theater;
  }

  @Transactional(readOnly = true)
  public Theater getTheater(Long id) {
    // findByIdFetchCity, not findById — TheaterResponse.from() touches t.getCity() and this
    // read must not hand back a lazy proxy the controller can't safely dereference.
    return theaterRepository
        .findByIdFetchCity(id)
        .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, id));
  }

  @Transactional(readOnly = true)
  public Page<Theater> listTheatersByCity(Long cityId, Pageable pageable) {
    return theaterRepository.findByCityIdAndActiveTrue(cityId, pageable);
  }

  // --- Screen ---

  public Screen createScreen(ScreenRequest req) {
    Theater theater = getTheater(req.theaterId());
    return screenRepository.save(new Screen(theater, req.name()));
  }

  @Transactional(readOnly = true)
  public Screen getScreen(Long id) {
    return screenRepository
        .findByIdFetchTheater(id)
        .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, id));
  }

  // --- Movie ---

  public Movie createMovie(MovieRequest req) {
    return movieRepository.save(
        new Movie(
            req.title(),
            req.language(),
            req.durationMinutes(),
            req.certification(),
            req.synopsis(),
            req.releaseDate()));
  }

  public Movie updateMovie(Long id, MovieRequest req) {
    Movie movie = getMovie(id);
    movie.update(
        req.title(),
        req.language(),
        req.durationMinutes(),
        req.certification(),
        req.synopsis(),
        req.releaseDate());
    return movie;
  }

  @Transactional(readOnly = true)
  public Movie getMovie(Long id) {
    return movieRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, id));
  }

  @Transactional(readOnly = true)
  public Page<Movie> listActiveMovies(Pageable pageable) {
    return movieRepository.findByActiveTrue(pageable);
  }
}
