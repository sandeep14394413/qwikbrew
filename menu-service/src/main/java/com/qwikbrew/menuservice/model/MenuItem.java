package com.qwikbrew.menuservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "menu_items", indexes = {
    @Index(name = "idx_menu_category",  columnList = "category"),
    @Index(name = "idx_menu_available", columnList = "available")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private String category;   // BREAKFAST | LUNCH | DRINKS | SNACKS | DESSERTS

    @Column(nullable = false)
    private String emoji;

    @Column(nullable = false) @Builder.Default private Boolean vegetarian  = true;
    @Column(nullable = false) @Builder.Default private Boolean spicy        = false;
    @Column(nullable = false) @Builder.Default private Boolean available    = true;
    @Column(nullable = false) @Builder.Default private Integer prepMinutes  = 10;
    @Column(nullable = false) @Builder.Default private Integer calories     = 0;
    @Column(nullable = false) @Builder.Default private Boolean featured     = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "menu_item_tags",   joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "tag")
    private List<String> tags;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "menu_item_addons", joinColumns = @JoinColumn(name = "item_id"))
    private List<AddOn> availableAddOns;

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AddOn {
        private String     name;
        private BigDecimal extraPrice;
    }
}
