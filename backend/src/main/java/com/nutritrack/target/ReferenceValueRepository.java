package com.nutritrack.target;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only access to the seeded reference values (NASEM DRI, DGA limits). */
@Repository
public class ReferenceValueRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReferenceValueRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ReferenceRow(String nutrientCode, String referenceSystem, String sex, int ageMin, int ageMax,
                               String lifeStage, String valueType, BigDecimal amount, String appliesTo,
                               String sourceName, String sourceUrl, String sourceVersion, String notes) {
    }

    private static final String SELECT = """
            SELECT n.code, r.reference_system, r.sex, r.age_min, r.age_max, r.life_stage, r.value_type, r.amount,
                   r.applies_to, r.source_name, r.source_url, r.source_version, r.notes
            FROM nutrient_reference_values r
            JOIN nutrient_definitions n ON n.id = r.nutrient_id
            """;

    public List<ReferenceRow> forSystem(String system) {
        return jdbc.query(SELECT + " WHERE r.reference_system = :system", new MapSqlParameterSource("system", system),
                (rs, i) -> map(rs));
    }

    public List<ReferenceRow> search(String system, String code) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        if (system != null) {
            sql.append(" AND r.reference_system = :system");
            p.addValue("system", system);
        }
        if (code != null) {
            sql.append(" AND n.code = :code");
            p.addValue("code", code);
        }
        sql.append(" ORDER BY n.display_order, r.value_type, r.sex, r.age_min");
        return jdbc.query(sql.toString(), p, (rs, i) -> map(rs));
    }

    public List<String> systems() {
        return jdbc.queryForList("SELECT DISTINCT reference_system FROM nutrient_reference_values ORDER BY 1",
                new MapSqlParameterSource(), String.class);
    }

    private static ReferenceRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReferenceRow(rs.getString("code"), rs.getString("reference_system"), rs.getString("sex"),
                rs.getInt("age_min"), rs.getInt("age_max"), rs.getString("life_stage"), rs.getString("value_type"),
                rs.getBigDecimal("amount"), rs.getString("applies_to"), rs.getString("source_name"),
                rs.getString("source_url"), rs.getString("source_version"), rs.getString("notes"));
    }
}
