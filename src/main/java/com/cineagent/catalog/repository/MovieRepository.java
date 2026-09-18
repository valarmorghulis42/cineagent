package com.cineagent.catalog.repository;

import com.cineagent.catalog.domain.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
  Page<Movie> findByActiveTrue(Pageable pageable);
}
