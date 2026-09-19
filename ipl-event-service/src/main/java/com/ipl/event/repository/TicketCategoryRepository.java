package com.ipl.event.repository;

import com.ipl.event.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCategoryRepository
        extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findByStadiumId(Long stadiumId);

    boolean existsByStadiumIdAndCategoryNameIgnoreCase(
            Long stadiumId,
            String categoryName
    );
}