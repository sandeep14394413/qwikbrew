package com.qwikbrew.menuservice;

import com.qwikbrew.menuservice.dto.MenuItemRequest;
import com.qwikbrew.menuservice.dto.MenuItemResponse;
import com.qwikbrew.menuservice.model.MenuItem;
import com.qwikbrew.menuservice.repository.MenuItemRepository;
import com.qwikbrew.menuservice.service.MenuService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock MenuItemRepository repo;
    @InjectMocks MenuService  menuService;

    private MenuItem masalaDosa;
    private MenuItem coldBrew;
    private MenuItem vegBurger;

    @BeforeEach
    void setUp() {
        masalaDosa = MenuItem.builder()
            .id("item-001").name("Masala Dosa").category("BREAKFAST")
            .price(new BigDecimal("80")).emoji("🥞")
            .vegetarian(true).spicy(false).available(true).featured(true)
            .calories(320).prepMinutes(12)
            .tags(List.of("Veg", "Bestseller"))
            .build();

        coldBrew = MenuItem.builder()
            .id("item-002").name("Cold Brew Coffee").category("DRINKS")
            .price(new BigDecimal("120")).emoji("☕")
            .vegetarian(true).spicy(false).available(true).featured(true)
            .calories(90).prepMinutes(3)
            .tags(List.of("Cold", "Refreshing"))
            .build();

        vegBurger = MenuItem.builder()
            .id("item-003").name("Veg Burger").category("LUNCH")
            .price(new BigDecimal("130")).emoji("🍔")
            .vegetarian(true).spicy(true).available(true).featured(false)
            .calories(480).prepMinutes(8)
            .build();
    }

    // ── findItems ─────────────────────────────────────────────────────────────

    @Test @DisplayName("findItems — no filter returns all available items")
    void findItems_noFilter_returnsAllAvailable() {
        when(repo.findByAvailableTrue()).thenReturn(List.of(masalaDosa, coldBrew, vegBurger));
        List<MenuItemResponse> result = menuService.findItems(null, null, null);
        assertThat(result).hasSize(3);
        assertThat(result).extracting(MenuItemResponse::getName)
            .containsExactlyInAnyOrder("Masala Dosa", "Cold Brew Coffee", "Veg Burger");
    }

    @Test @DisplayName("findItems — category filter returns only matching items")
    void findItems_withCategory_filtersCorrectly() {
        when(repo.findByCategoryIgnoreCaseAndAvailableTrue("BREAKFAST"))
            .thenReturn(List.of(masalaDosa));
        List<MenuItemResponse> result = menuService.findItems("BREAKFAST", null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Masala Dosa");
    }

    @Test @DisplayName("findItems — vegetarian=true filters correctly")
    void findItems_vegetarianFilter_returnsOnlyVeg() {
        when(repo.findByAvailableTrue()).thenReturn(List.of(masalaDosa, coldBrew, vegBurger));
        List<MenuItemResponse> result = menuService.findItems(null, true, null);
        assertThat(result).allSatisfy(item -> assertThat(item.getVegetarian()).isTrue());
    }

    @Test @DisplayName("findItems — unknown category returns empty list")
    void findItems_unknownCategory_returnsEmpty() {
        when(repo.findByCategoryIgnoreCaseAndAvailableTrue("PIZZA"))
            .thenReturn(Collections.emptyList());
        assertThat(menuService.findItems("PIZZA", null, null)).isEmpty();
    }

    // ── getItem ───────────────────────────────────────────────────────────────

    @Test @DisplayName("getItem — known id returns correct item")
    void getItem_existingId_returnsItem() {
        when(repo.findById("item-001")).thenReturn(Optional.of(masalaDosa));
        MenuItemResponse result = menuService.getItem("item-001");
        assertThat(result.getId()).isEqualTo("item-001");
        assertThat(result.getPrice()).isEqualByComparingTo("80");
        assertThat(result.getEmoji()).isEqualTo("🥞");
    }

    @Test @DisplayName("getItem — unknown id throws RuntimeException")
    void getItem_unknownId_throws() {
        when(repo.findById("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> menuService.getItem("bad"))
            .isInstanceOf(RuntimeException.class);
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test @DisplayName("search — partial name match returns results")
    void search_partialName_returnsMatch() {
        when(repo.search("dosa")).thenReturn(List.of(masalaDosa));
        List<MenuItemResponse> result = menuService.search("dosa");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Masala Dosa");
    }

    @Test @DisplayName("search — no match returns empty list")
    void search_noMatch_returnsEmpty() {
        when(repo.search("pizza")).thenReturn(Collections.emptyList());
        assertThat(menuService.search("pizza")).isEmpty();
    }

    // ── getFeatured ───────────────────────────────────────────────────────────

    @Test @DisplayName("getFeatured — returns only featured items")
    void getFeatured_returnsFeaturedOnly() {
        when(repo.findByFeaturedTrueAndAvailableTrue()).thenReturn(List.of(masalaDosa, coldBrew));
        List<MenuItemResponse> result = menuService.getFeatured();
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(item -> assertThat(item.getFeatured()).isTrue());
    }

    // ── createItem ────────────────────────────────────────────────────────────

    @Test @DisplayName("createItem — saves item with uppercase category")
    void createItem_savesWithUppercaseCategory() {
        MenuItemRequest req = MenuItemRequest.builder()
            .name("Upma").description("Semolina breakfast").price(new BigDecimal("70"))
            .category("breakfast").emoji("🥣").vegetarian(true).spicy(false)
            .prepMinutes(10).calories(280).featured(false).build();
        MenuItem saved = MenuItem.builder().id("new-1").name("Upma")
            .category("BREAKFAST").price(new BigDecimal("70")).emoji("🥣")
            .vegetarian(true).spicy(false).available(true).build();
        when(repo.save(any(MenuItem.class))).thenReturn(saved);
        MenuItemResponse result = menuService.createItem(req);
        assertThat(result.getName()).isEqualTo("Upma");
        assertThat(result.getCategory()).isEqualTo("BREAKFAST");
        verify(repo).save(any(MenuItem.class));
    }

    // ── setAvailability ───────────────────────────────────────────────────────

    @Test @DisplayName("setAvailability false — marks item unavailable")
    void setAvailability_false_marksUnavailable() {
        when(repo.findById("item-001")).thenReturn(Optional.of(masalaDosa));
        when(repo.save(any())).thenReturn(masalaDosa);
        menuService.setAvailability("item-001", false);
        verify(repo).save(argThat(item -> !item.getAvailable()));
    }

    @Test @DisplayName("setAvailability — unknown item throws RuntimeException")
    void setAvailability_unknownItem_throws() {
        when(repo.findById("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> menuService.setAvailability("bad", true))
            .isInstanceOf(RuntimeException.class);
    }

    // ── deleteItem ────────────────────────────────────────────────────────────

    @Test @DisplayName("deleteItem — delegates to repository")
    void deleteItem_callsRepoDeleteById() {
        doNothing().when(repo).deleteById("item-001");
        menuService.deleteItem("item-001");
        verify(repo).deleteById("item-001");
    }

    // ── getCategories ─────────────────────────────────────────────────────────

    @Test @DisplayName("getCategories — returns all distinct categories")
    void getCategories_returnsDistinctList() {
        when(repo.findDistinctCategories())
            .thenReturn(List.of("BREAKFAST", "LUNCH", "DRINKS", "SNACKS", "DESSERTS"));
        List<String> result = menuService.getCategories();
        assertThat(result).hasSize(5).contains("BREAKFAST", "DRINKS");
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    @Test @DisplayName("MenuItemResponse.from — maps entity fields correctly")
    void menuItemResponseFrom_mapsFields() {
        MenuItemResponse resp = MenuItemResponse.from(masalaDosa);
        assertThat(resp.getId()).isEqualTo("item-001");
        assertThat(resp.getName()).isEqualTo("Masala Dosa");
        assertThat(resp.getCategory()).isEqualTo("BREAKFAST");
        assertThat(resp.getPrice()).isEqualByComparingTo("80");
        assertThat(resp.getVegetarian()).isTrue();
        assertThat(resp.getSpicy()).isFalse();
        assertThat(resp.getCalories()).isEqualTo(320);
        assertThat(resp.getTags()).contains("Veg", "Bestseller");
    }
}
