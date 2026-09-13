package com.oncue.combination.repository;

import com.oncue.combination.model.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    @Query("select s from Scenario s where s.key = :key and s.status = 'ACTIVE'")
    Optional<Scenario> findActiveByKey(@Param("key") String key);
}
