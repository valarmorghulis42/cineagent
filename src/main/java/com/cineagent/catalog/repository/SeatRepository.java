package com.cineagent.catalog.repository;

import com.cineagent.catalog.domain.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
  List<Seat> findByScreenIdAndActiveTrueOrderByRowLabelAscSeatNumberAsc(Long screenId);

  boolean existsByScreenId(Long screenId);
}
