package com.qwikbrew.menuservice.dto;

import com.qwikbrew.menuservice.model.MenuItem;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuItemRequest {
    @NotBlank  private String name;
               private String description;
    @NotNull @Positive private BigDecimal price;
    @NotBlank  private String category;
    @NotBlank  private String emoji;
    @Builder.Default private Boolean vegetarian  = true;
    @Builder.Default private Boolean spicy       = false;
    @Builder.Default private Integer prepMinutes = 10;
    @Builder.Default private Integer calories    = 0;
    @Builder.Default private Boolean featured    = false;
    private List<String> tags;
    private List<MenuItem.AddOn> addOns;
}
