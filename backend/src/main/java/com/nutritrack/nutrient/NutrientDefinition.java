package com.nutritrack.nutrient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "nutrient_definitions")
public class NutrientDefinition {
    @Id
    private Integer id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", length = 40)
    private String shortName;

    @Column(nullable = false, length = 8)
    private String unit;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "form_note")
    private String formNote;

    @Column(name = "usda_nutrient_id")
    private Integer usdaNutrientId;

    @Column(name = "usda_nutrient_number", length = 10)
    private String usdaNutrientNumber;

    protected NutrientDefinition() {
    }

    public NutrientDefinition(Integer id, String code, String name, String unit, String category, int displayOrder,
                              Integer usdaNutrientId) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.shortName = name;
        this.unit = unit;
        this.category = category;
        this.displayOrder = displayOrder;
        this.usdaNutrientId = usdaNutrientId;
    }

    public Integer getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getShortName() { return shortName; }
    public String getUnit() { return unit; }
    public String getCategory() { return category; }
    public int getDisplayOrder() { return displayOrder; }
    public String getFormNote() { return formNote; }
    public Integer getUsdaNutrientId() { return usdaNutrientId; }
    public String getUsdaNutrientNumber() { return usdaNutrientNumber; }
}
