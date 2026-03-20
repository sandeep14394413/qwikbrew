package com.qwikbrew.menuservice.service;

import com.qwikbrew.menuservice.dto.MenuItemRequest;
import com.qwikbrew.menuservice.dto.MenuItemResponse;
import com.qwikbrew.menuservice.model.MenuItem;
import com.qwikbrew.menuservice.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MenuService {

    private final MenuItemRepository repo;

    @Cacheable(value = "menu", key = "'all'")
    @Transactional(readOnly = true)
    public List<MenuItemResponse> findItems(String category, Boolean vegetarian, Boolean available) {
        List<MenuItem> items;
        if (category != null && !category.isBlank()) {
            items = repo.findByCategoryIgnoreCaseAndAvailableTrue(category);
        } else {
            items = available != null && !available
                ? repo.findAll()
                : repo.findByAvailableTrue();
        }
        if (vegetarian != null) {
            items = items.stream().filter(i -> i.getVegetarian().equals(vegetarian)).toList();
        }
        return items.stream().map(MenuItemResponse::from).toList();
    }

    @Cacheable(value = "menu", key = "#itemId")
    @Transactional(readOnly = true)
    public MenuItemResponse getItem(String itemId) {
        return repo.findById(itemId)
            .map(MenuItemResponse::from)
            .orElseThrow(() -> new RuntimeException("Menu item not found: " + itemId));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> search(String q) {
        return repo.search(q).stream().map(MenuItemResponse::from).toList();
    }

    @Cacheable(value = "categories")
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return repo.findDistinctCategories();
    }

    @Cacheable(value = "menu", key = "'featured'")
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getFeatured() {
        return repo.findByFeaturedTrueAndAvailableTrue().stream().map(MenuItemResponse::from).toList();
    }

    @CacheEvict(value = "menu", allEntries = true)
    public MenuItemResponse createItem(MenuItemRequest req) {
        MenuItem item = MenuItem.builder()
            .name(req.getName()).description(req.getDescription())
            .price(req.getPrice()).category(req.getCategory().toUpperCase())
            .emoji(req.getEmoji()).vegetarian(req.getVegetarian())
            .spicy(req.getSpicy()).prepMinutes(req.getPrepMinutes())
            .calories(req.getCalories()).featured(req.getFeatured())
            .tags(req.getTags()).availableAddOns(req.getAddOns())
            .build();
        return MenuItemResponse.from(repo.save(item));
    }

    @CacheEvict(value = "menu", allEntries = true)
    public MenuItemResponse updateItem(String itemId, MenuItemRequest req) {
        MenuItem item = repo.findById(itemId)
            .orElseThrow(() -> new RuntimeException("Menu item not found: " + itemId));
        item.setName(req.getName());
        item.setDescription(req.getDescription());
        item.setPrice(req.getPrice());
        item.setCategory(req.getCategory().toUpperCase());
        item.setEmoji(req.getEmoji());
        item.setVegetarian(req.getVegetarian());
        item.setSpicy(req.getSpicy());
        item.setCalories(req.getCalories());
        item.setFeatured(req.getFeatured());
        item.setTags(req.getTags());
        return MenuItemResponse.from(repo.save(item));
    }

    @CacheEvict(value = "menu", allEntries = true)
    public MenuItemResponse setAvailability(String itemId, boolean available) {
        MenuItem item = repo.findById(itemId)
            .orElseThrow(() -> new RuntimeException("Menu item not found: " + itemId));
        item.setAvailable(available);
        return MenuItemResponse.from(repo.save(item));
    }

    @CacheEvict(value = "menu", allEntries = true)
    public void deleteItem(String itemId) {
        repo.deleteById(itemId);
    }
}
