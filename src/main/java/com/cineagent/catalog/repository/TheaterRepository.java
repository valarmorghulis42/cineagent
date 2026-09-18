package com.cineagent.catalog.repository;

import com.cineagent.catalog.domain.Theater;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

  /**
   * JOIN FETCH city — TheaterResponse.from() dereferences t.getCity() in the controller, after
   * this service method's transaction has closed (open-in-view=false). Without the fetch join
   * this throws LazyInitializationException on every call, including the public
   * GET /cities/{cityId}/theaters browse endpoint. Read paths must fetch inside the transaction;
   * see the spring-boot-conventions skill.
   */
  @Query("select t from Theater t join fetch t.city where t.city.id = :cityId and t.active = true")
  Page<Theater> findByCityIdAndActiveTrue(@Param("cityId") Long cityId, Pageable pageable);

  @Query("select t from Theater t join fetch t.city where t.id = :id")
  Optional<Theater> findByIdFetchCity(@Param("id") Long id);
}
