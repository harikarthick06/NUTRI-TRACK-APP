-- Built-in recipe serving labels such as "1 katori (150 g)" already include their weight, but V4-era code
-- appended it again ("1 katori (150 g) (150 g)"). Restore the label as written. New databases are unaffected:
-- their recipe foods are created after this runs, by code that no longer adds the weight twice.
UPDATE food_portions p
JOIN recipes r ON r.food_id = p.food_id
SET p.label = r.serving_label
WHERE p.is_default = TRUE
  AND r.serving_label REGEXP '\\([0-9.]+ ?(g|ml)\\)'
  AND p.label = CONCAT(r.serving_label, ' (', ROUND(p.basis_amount), ' g)');
