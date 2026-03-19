package com.qwikbrew.menuservice.controller;

import com.qwikbrew.menuservice.dto.MenuItemRequest;
import com.qwikbrew.menuservice.dto.MenuItemResponse;
import com.qwikbrew.menuservice.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // ── Public endpoints ──────────────────────────────────
    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getAllItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean vegetarian,
            @RequestParam(required = false) Boolean available) {
        return ResponseEntity.ok(menuService.findItems(category, vegetarian, available));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<MenuItemResponse> getItem(@PathVariable String itemId) {
        return ResponseEntity.ok(menuService.getItem(itemId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MenuItemResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(menuService.search(q));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(menuService.getCategories());
    }

    @GetMapping("/featured")
    public ResponseEntity<List<MenuItemResponse>> getFeatured() {
        return ResponseEntity.ok(menuService.getFeaturedItems());
    }

    // ── Admin endpoints ───────────────────────────────────
    @PostMapping
    public ResponseEntity<MenuItemResponse> createItem(@Valid @RequestBody MenuItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createItem(req));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<MenuItemResponse> updateItem(
            @PathVariable String itemId,
            @Valid @RequestBody MenuItemRequest req) {
        return ResponseEntity.ok(menuService.updateItem(itemId, req));
    }

    @PatchMapping("/{itemId}/availability")
    public ResponseEntity<MenuItemResponse> toggleAvailability(
            @PathVariable String itemId,
            @RequestParam boolean available) {
        return ResponseEntity.ok(menuService.setAvailability(itemId, available));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable String itemId) {
        menuService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
