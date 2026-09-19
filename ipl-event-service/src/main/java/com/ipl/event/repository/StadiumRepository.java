package com.ipl.event.repository;

import com.ipl.event.entity.Stadium;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {

    List<Stadium> findByCityIgnoreCase(String city);

    boolean existsByNameIgnoreCase(String name);
}