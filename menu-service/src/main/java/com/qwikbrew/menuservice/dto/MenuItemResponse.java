package com.qwikbrew.menuservice.dto;

import com.qwikbrew.menuservice.model.MenuItem;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuItemResponse {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private String emoji;
    private Boolean vegetarian;
    private Boolean spicy;
    private Boolean available;
    private Integer prepMinutes;
    private Integer calories;
    private Boolean featured;
    private List<String> tags;
    private List<AddOnDto> addOns;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AddOnDto {
        private String name;
        private BigDecimal extraPrice;
    }

    public static MenuItemResponse from(MenuItem m) {
        List<AddOnDto> addOns = m.getAvailableAddOns() == null ? List.of() :
            m.getAvailableAddOns().stream()
                .map(a -> new AddOnDto(a.getName(), a.getExtraPrice()))
                .toList();
        return MenuItemResponse.builder()
            .id(m.getId()).name(m.getName()).description(m.getDescription())
            .price(m.getPrice()).category(m.getCategory()).emoji(m.getEmoji())
            .vegetarian(m.getVegetarian()).spicy(m.getSpicy()).available(m.getAvailable())
            .prepMinutes(m.getPrepMinutes()).calories(m.getCalories())
            .featured(m.getFeatured()).tags(m.getTags()).addOns(addOns)
            .build();
    }
}
