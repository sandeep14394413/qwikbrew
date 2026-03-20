package com.qwikbrew.menuservice.controller;

import com.qwikbrew.menuservice.dto.*;
import com.qwikbrew.menuservice.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("UP"); }

    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> list(
            @RequestParam(required = false) String  category,
            @RequestParam(required = false) Boolean vegetarian,
            @RequestParam(required = false) Boolean available) {
        return ResponseEntity.ok(menuService.findItems(category, vegetarian, available));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<MenuItemResponse> get(@PathVariable String itemId) {
        return ResponseEntity.ok(menuService.getItem(itemId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MenuItemResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(menuService.search(q));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(menuService.getCategories());
    }

    @GetMapping("/featured")
    public ResponseEntity<List<MenuItemResponse>> featured() {
        return ResponseEntity.ok(menuService.getFeatured());
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createItem(req));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<MenuItemResponse> update(
            @PathVariable String itemId,
            @Valid @RequestBody MenuItemRequest req) {
        return ResponseEntity.ok(menuService.updateItem(itemId, req));
    }

    @PatchMapping("/{itemId}/availability")
    public ResponseEntity<MenuItemResponse> availability(
            @PathVariable String itemId,
            @RequestParam boolean available) {
        return ResponseEntity.ok(menuService.setAvailability(itemId, available));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable String itemId) {
        menuService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
