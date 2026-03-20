package com.qwikbrew.menuservice.repository;

import com.qwikbrew.menuservice.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, String> {

    List<MenuItem> findByAvailableTrue();

    List<MenuItem> findByCategoryIgnoreCaseAndAvailableTrue(String category);

    List<MenuItem> findByFeaturedTrueAndAvailableTrue();

    @Query("SELECT m FROM MenuItem m WHERE m.available = true AND " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(m.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<MenuItem> search(String q);

    @Query("SELECT DISTINCT m.category FROM MenuItem m WHERE m.available = true")
    List<String> findDistinctCategories();
}
