package com.nutritrack.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL aggregations for reports and exports. Every query is scoped to one user. */
@Repository
public class ReportRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FoodUsage(long foodId, String name, String brand, int timesLogged, BigDecimal energyKcal) {
    }

    public record MealEnergy(String mealType, int entries, BigDecimal energyKcal) {
    }

    /** One diary entry with its macros scaled to the logged amount; null means the food has no value. */
    public record DiaryRow(long id, LocalDate date, Instant loggedAt, String mealType, String foodName, String brand,
                           String dataQuality, BigDecimal quantity, String unit, String portionLabel,
                           String weightState, BigDecimal basisAmount, String basisUnit, BigDecimal energyKcal,
                           BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG, BigDecimal fiberG,
                           String notes) {
    }

    private static MapSqlParameterSource p(long userId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("uid", userId).addValue("from", from).addValue("to", to);
    }

    public Set<LocalDate> completedDays(long userId, LocalDate from, LocalDate to) {
        Set<LocalDate> out = new HashSet<>();
        jdbc.query("SELECT day_date FROM diary_days WHERE user_id = :uid AND completed = TRUE"
                + " AND day_date BETWEEN :from AND :to", p(userId, from, to),
                rs -> {
                    out.add(rs.getDate(1).toLocalDate());
                });
        return out;
    }

    public List<FoodUsage> topFoods(long userId, LocalDate from, LocalDate to, int limit) {
        return jdbc.query("""
                SELECT f.id, f.name, f.brand, COUNT(*) AS times,
                       ROUND(SUM(fn.amount * e.basis_amount / f.basis_quantity), 0) AS kcal
                FROM food_diary_entries e
                JOIN foods f ON f.id = e.food_id
                LEFT JOIN food_nutrients fn ON fn.food_id = f.id
                     AND fn.nutrient_id = (SELECT id FROM nutrient_definitions WHERE code = 'ENERGY_KCAL')
                WHERE e.user_id = :uid AND e.entry_date BETWEEN :from AND :to
                GROUP BY f.id, f.name, f.brand
                ORDER BY kcal IS NULL, kcal DESC, times DESC
                LIMIT :limit
                """, p(userId, from, to).addValue("limit", limit), (rs, i) -> new FoodUsage(rs.getLong("id"),
                rs.getString("name"), rs.getString("brand"), rs.getInt("times"), rs.getBigDecimal("kcal")));
    }

    public List<MealEnergy> energyByMeal(long userId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT e.meal_type, COUNT(*) AS entries, ROUND(SUM(fn.amount * e.basis_amount / f.basis_quantity), 0) AS kcal
                FROM food_diary_entries e
                JOIN foods f ON f.id = e.food_id
                LEFT JOIN food_nutrients fn ON fn.food_id = f.id
                     AND fn.nutrient_id = (SELECT id FROM nutrient_definitions WHERE code = 'ENERGY_KCAL')
                WHERE e.user_id = :uid AND e.entry_date BETWEEN :from AND :to
                GROUP BY e.meal_type
                """, p(userId, from, to), (rs, i) -> new MealEnergy(rs.getString("meal_type"), rs.getInt("entries"),
                rs.getBigDecimal("kcal")));
    }

    public List<DiaryRow> diary(long userId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT e.id, e.entry_date, e.logged_at, e.meal_type, f.name, f.brand, f.data_quality, e.quantity,
                       e.unit, fp.label AS portion_label, e.weight_state, e.basis_amount, f.basis_unit, e.notes,
                       MAX(CASE WHEN n.code = 'ENERGY_KCAL' THEN fn.amount * e.basis_amount / f.basis_quantity END) AS kcal,
                       MAX(CASE WHEN n.code = 'PROTEIN' THEN fn.amount * e.basis_amount / f.basis_quantity END) AS protein,
                       MAX(CASE WHEN n.code = 'CARBOHYDRATE' THEN fn.amount * e.basis_amount / f.basis_quantity END) AS carbs,
                       MAX(CASE WHEN n.code = 'FAT_TOTAL' THEN fn.amount * e.basis_amount / f.basis_quantity END) AS fat,
                       MAX(CASE WHEN n.code = 'FIBER' THEN fn.amount * e.basis_amount / f.basis_quantity END) AS fiber
                FROM food_diary_entries e
                JOIN foods f ON f.id = e.food_id
                LEFT JOIN food_portions fp ON fp.id = e.portion_id
                LEFT JOIN food_nutrients fn ON fn.food_id = f.id
                LEFT JOIN nutrient_definitions n ON n.id = fn.nutrient_id
                     AND n.code IN ('ENERGY_KCAL', 'PROTEIN', 'CARBOHYDRATE', 'FAT_TOTAL', 'FIBER')
                WHERE e.user_id = :uid AND e.entry_date BETWEEN :from AND :to
                GROUP BY e.id, e.entry_date, e.logged_at, e.meal_type, f.name, f.brand, f.data_quality, e.quantity,
                         e.unit, fp.label, e.weight_state, e.basis_amount, f.basis_unit, e.notes
                ORDER BY e.entry_date, FIELD(e.meal_type, 'BREAKFAST', 'MORNING_SNACK', 'LUNCH', 'EVENING_SNACK',
                         'DINNER', 'OTHER'), e.logged_at, e.id
                """, p(userId, from, to), (rs, i) -> new DiaryRow(rs.getLong("id"),
                rs.getDate("entry_date").toLocalDate(), rs.getObject("logged_at", java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),
                rs.getString("meal_type"), rs.getString("name"), rs.getString("brand"), rs.getString("data_quality"),
                rs.getBigDecimal("quantity"), rs.getString("unit"), rs.getString("portion_label"),
                rs.getString("weight_state"), rs.getBigDecimal("basis_amount"), rs.getString("basis_unit"),
                rs.getBigDecimal("kcal"), rs.getBigDecimal("protein"), rs.getBigDecimal("carbs"),
                rs.getBigDecimal("fat"), rs.getBigDecimal("fiber"), rs.getString("notes")));
    }
}
