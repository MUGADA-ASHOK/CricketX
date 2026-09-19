package com.ipl.event.repository;

import com.ipl.event.entity.Franchise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FranchiseRepository extends JpaRepository<Franchise, Long> {

    Optional<Franchise> findByUserId(Long userId);

    Optional<Franchise> findByTeamCode(String teamCode);

    boolean existsByUserId(Long userId);

    boolean existsByTeamCode(String teamCode);

    boolean existsByFranchiseName(String franchiseName);
}