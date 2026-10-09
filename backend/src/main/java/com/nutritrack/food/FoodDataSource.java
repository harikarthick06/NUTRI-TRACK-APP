package com.nutritrack.food;

import com.nutritrack.common.Enums.DataQuality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_data_sources")
public class FoodDataSource {
    public static final String IFCT = "IFCT_2017";
    public static final String USDA_SR28 = "USDA_SR28";
    public static final String USDA_FDC = "USDA_FDC";
    public static final String OPEN_FOOD_FACTS = "OPEN_FOOD_FACTS";
    public static final String USER = "USER";
    public static final String RECIPE = "NUTRITRACK_RECIPE";

    @Id
    private Integer id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    private String url;

    private String version;

    @Column(name = "license_note")
    private String licenseNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_quality", nullable = false, length = 20)
    private DataQuality defaultQuality;

    protected FoodDataSource() {
    }

    public Integer getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getVersion() { return version; }
    public String getLicenseNote() { return licenseNote; }
    public DataQuality getDefaultQuality() { return defaultQuality; }
}
