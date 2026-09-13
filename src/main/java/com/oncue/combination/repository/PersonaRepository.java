package com.oncue.combination.repository;

import com.oncue.combination.model.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    @Query("select p from Persona p where p.key = :key and p.status = 'ACTIVE'")
    Optional<Persona> findActiveByKey(@Param("key") String key);
}
