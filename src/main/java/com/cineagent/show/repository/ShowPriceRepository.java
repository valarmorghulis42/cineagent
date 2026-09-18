package com.cineagent.show.repository;

import com.cineagent.show.domain.ShowPrice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowPriceRepository extends JpaRepository<ShowPrice, Long> {
  List<ShowPrice> findByShowId(Long showId);
}
