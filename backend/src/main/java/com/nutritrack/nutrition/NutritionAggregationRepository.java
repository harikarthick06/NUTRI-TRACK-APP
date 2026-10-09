package com.nutritrack.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Nutrient totals computed in MySQL from diary entries: SUM(amount per basis x entry basis amount / basis
 * quantity). Only the user's own entries for the requested range are read; nothing is loaded into memory to
 * be summed. Missing food_nutrients rows contribute nothing to the sum and are counted separately so reports
 * can show how complete each total is.
 */
@Repository
public class NutritionAggregationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public NutritionAggregationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Totals for one nutrient over a group of entries. */
    public record Total(String code, BigDecimal amount, int entriesWithData, int entriesPartial) {
    }

    /** Totals per date and nutrient over a date range (inclusive). */
    public Map<LocalDate, Map<String, Total>> totalsByDate(long userId, LocalDate from, LocalDate to) {
        Map<LocalDate, Map<String, Total>> out = new HashMap<>();
        jdbc.query("""
                SELECT e.entry_date, n.code,
                       SUM(fn.amount * e.basis_amount / f.basis_quantity) AS total,
                       COUNT(*) AS with_data,
                       SUM(CASE WHEN fn.coverage_pct IS NOT NULL AND fn.coverage_pct < 100 THEN 1 ELSE 0 END) AS partial
                FROM food_diary_entries e
                JOIN foods f ON f.id = e.food_id
                JOIN food_nutrients fn ON fn.food_id = e.food_id
                JOIN nutrient_definitions n ON n.id = fn.nutrient_id
                WHERE e.user_id = :uid AND e.entry_date BETWEEN :from AND :to
                GROUP BY e.entry_date, n.code
                """, params(userId, from, to), rs -> {
            LocalDate d = rs.getDate("entry_date").toLocalDate();
            String code = rs.getString("code");
            out.computeIfAbsent(d, k -> new HashMap<>()).put(code, new Total(code, rs.getBigDecimal("total"),
                    rs.getInt("with_data"), rs.getInt("partial")));
        });
        return out;
    }

    /** Totals per meal and nutrient for one date. */
    public Map<String, Map<String, Total>> totalsByMeal(long userId, LocalDate date) {
        Map<String, Map<String, Total>> out = new HashMap<>();
        jdbc.query("""
                SELECT e.meal_type, n.code,
                       SUM(fn.amount * e.basis_amount / f.basis_quantity) AS total,
                       COUNT(*) AS with_data,
                       SUM(CASE WHEN fn.coverage_pct IS NOT NULL AND fn.coverage_pct < 100 THEN 1 ELSE 0 END) AS partial
                FROM food_diary_entries e
                JOIN foods f ON f.id = e.food_id
                JOIN food_nutrients fn ON fn.food_id = e.food_id
                JOIN nutrient_definitions n ON n.id = fn.nutrient_id
                WHERE e.user_id = :uid AND e.entry_date = :from
                GROUP BY e.meal_type, n.code
                """, params(userId, date, date), rs -> {
            String meal = rs.getString("meal_type");
            String code = rs.getString("code");
            out.computeIfAbsent(meal, k -> new HashMap<>()).put(code, new Total(code, rs.getBigDecimal("total"),
                    rs.getInt("with_data"), rs.getInt("partial")));
        });
        return out;
    }

    /** Number of diary entries per date in a range. */
    public Map<LocalDate, Integer> entryCounts(long userId, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> out = new HashMap<>();
        jdbc.query("SELECT entry_date, COUNT(*) AS c FROM food_diary_entries WHERE user_id = :uid"
                        + " AND entry_date BETWEEN :from AND :to GROUP BY entry_date", params(userId, from, to),
                rs -> {
                    out.put(rs.getDate("entry_date").toLocalDate(), rs.getInt("c"));
                });
        return out;
    }

    /** Entries per meal for one date. */
    public Map<String, Integer> entryCountsByMeal(long userId, LocalDate date) {
        Map<String, Integer> out = new HashMap<>();
        jdbc.query("SELECT meal_type, COUNT(*) AS c FROM food_diary_entries WHERE user_id = :uid"
                + " AND entry_date = :from GROUP BY meal_type", params(userId, date, date), rs -> {
            out.put(rs.getString("meal_type"), rs.getInt("c"));
        });
        return out;
    }

    /** Selected nutrients for specific diary entries, scaled to the logged amount. */
    public Map<Long, Map<String, BigDecimal>> entryNutrients(long userId, Collection<Long> entryIds,
                                                             Collection<String> codes) {
        Map<Long, Map<String, BigDecimal>> out = new HashMap<>();
        if (entryIds.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT e.id, n.code, fn.amount * e.basis_amount / f.basis_quantity AS amount
                FROM food_diary_entries e
                JOIN foods f ON f.id = e.food_id
                JOIN food_nutrients fn ON fn.food_id = e.food_id
                JOIN nutrient_definitions n ON n.id = fn.nutrient_id
                WHERE e.user_id = :uid AND e.id IN (:ids) AND n.code IN (:codes)
                """, new MapSqlParameterSource("uid", userId).addValue("ids", entryIds)
                .addValue("codes", new ArrayList<>(codes)), rs -> {
            out.computeIfAbsent(rs.getLong("id"), k -> new HashMap<>()).put(rs.getString("code"),
                    rs.getBigDecimal("amount"));
        });
        return out;
    }

    private static MapSqlParameterSource params(long userId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("uid", userId).addValue("from", from).addValue("to", to);
    }

    public static List<String> macroCodes() {
        return List.of("ENERGY_KCAL", "PROTEIN", "CARBOHYDRATE", "FAT_TOTAL", "FIBER");
    }
}
