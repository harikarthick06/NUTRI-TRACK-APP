-- NutriTrack core schema.
-- Conventions:
--   * Every user-owned table carries user_id/owner_user_id with a foreign key to users and an index
--     that starts with the owner column, so every private query is filtered and indexed by owner.
--   * Nutrient quantities use DECIMAL, never FLOAT/DOUBLE.
--   * Food nutrient amounts are stored in the nutrient's canonical unit (nutrient_definitions.unit)
--     per foods.basis_quantity of foods.basis_unit (normally per 100 g or per 100 mL).
--   * A missing food_nutrients row means "unknown", never zero. A stored 0 means a measured zero.

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    token_version   INT          NOT NULL DEFAULT 0,
    last_login_at   DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user (user_id, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_profiles (
    user_id               BIGINT PRIMARY KEY,
    name                  VARCHAR(100)  NOT NULL,
    age                   SMALLINT      NULL,
    height_cm             DECIMAL(5, 1) NULL,
    current_weight_kg     DECIMAL(5, 2) NULL,
    target_weight_kg      DECIMAL(5, 2) NULL,
    sex                   VARCHAR(16)   NULL COMMENT 'FEMALE, MALE or NULL (not provided); only used to pick reference values',
    activity_level        VARCHAR(20)   NULL COMMENT 'SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE',
    workout_days_per_week TINYINT       NULL,
    goal                  VARCHAR(20)   NULL COMMENT 'FAT_LOSS, MAINTENANCE, MUSCLE_GAIN',
    weight_unit           VARCHAR(4)    NOT NULL DEFAULT 'KG',
    height_unit           VARCHAR(4)    NOT NULL DEFAULT 'CM',
    energy_unit           VARCHAR(4)    NOT NULL DEFAULT 'KCAL',
    dietary_preference    VARCHAR(20)   NULL COMMENT 'VEGETARIAN, EGGETARIAN, NON_VEGETARIAN, VEGAN, OTHER',
    allergies             VARCHAR(1000) NULL,
    dietary_restrictions  VARCHAR(1000) NULL,
    calorie_target_kcal   INT           NULL,
    protein_target_g      DECIMAL(7, 2) NULL,
    carbs_target_g        DECIMAL(7, 2) NULL,
    fat_target_g          DECIMAL(7, 2) NULL,
    fiber_target_g        DECIMAL(7, 2) NULL,
    water_target_ml       INT           NULL,
    reference_system      VARCHAR(30)   NOT NULL DEFAULT 'NASEM_DRI',
    timezone              VARCHAR(64)   NOT NULL DEFAULT 'Asia/Kolkata',
    onboarding_completed  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profiles_age CHECK (age IS NULL OR (age BETWEEN 14 AND 120)),
    CONSTRAINT ck_user_profiles_workouts CHECK (workout_days_per_week IS NULL OR (workout_days_per_week BETWEEN 0 AND 14))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE nutrient_definitions (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    code                 VARCHAR(40)  NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    short_name           VARCHAR(40)  NULL,
    unit                 VARCHAR(8)   NOT NULL COMMENT 'Canonical unit: KCAL, G, MG, UG, ML',
    category             VARCHAR(20)  NOT NULL COMMENT 'ENERGY, MACRONUTRIENT, VITAMIN, MINERAL, FATTY_ACID, OTHER',
    display_order        INT          NOT NULL,
    form_note            VARCHAR(255) NULL COMMENT 'Chemical form or equivalence, e.g. RAE, DFE, alpha-tocopherol',
    usda_nutrient_id     INT          NULL COMMENT 'FoodData Central nutrient.id',
    usda_nutrient_number VARCHAR(10)  NULL COMMENT 'Legacy SR nutrient number',
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_nutrient_definitions_code UNIQUE (code),
    INDEX idx_nutrient_definitions_category (category, display_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE nutrient_reference_values (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    nutrient_id      INT            NOT NULL,
    reference_system VARCHAR(30)    NOT NULL COMMENT 'NASEM_DRI, ICMR_NIN_2020, DGA_2020',
    sex              VARCHAR(10)    NOT NULL DEFAULT 'ANY' COMMENT 'ANY, FEMALE, MALE',
    age_min          SMALLINT       NOT NULL,
    age_max          SMALLINT       NOT NULL,
    life_stage       VARCHAR(20)    NOT NULL DEFAULT 'GENERAL',
    value_type       VARCHAR(24)    NOT NULL COMMENT 'RDA, AI, UL, CDRR, LIMIT_PCT_ENERGY',
    amount           DECIMAL(14, 4) NOT NULL,
    applies_to       VARCHAR(30)    NOT NULL DEFAULT 'TOTAL_INTAKE' COMMENT 'TOTAL_INTAKE, SUPPLEMENTAL_ONLY, PREFORMED_ONLY, LINOLEIC_ACID',
    source_name      VARCHAR(255)   NOT NULL,
    source_url       VARCHAR(500)   NULL,
    source_version   VARCHAR(100)    NOT NULL,
    notes            VARCHAR(500)   NULL,
    created_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_nrv_nutrient FOREIGN KEY (nutrient_id) REFERENCES nutrient_definitions (id),
    CONSTRAINT uk_nrv UNIQUE (reference_system, nutrient_id, sex, age_min, life_stage, value_type),
    INDEX idx_nrv_lookup (reference_system, nutrient_id, age_min, age_max)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE food_data_sources (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    url             VARCHAR(500) NULL,
    version         VARCHAR(60)  NULL,
    license_note    VARCHAR(500) NULL,
    default_quality VARCHAR(20)  NOT NULL COMMENT 'VERIFIED, EXTERNAL, USER_ENTERED, ESTIMATED',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_food_data_sources_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE foods (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(255)   NOT NULL,
    brand               VARCHAR(255)   NULL,
    category            VARCHAR(100)   NULL,
    food_type           VARCHAR(20)    NOT NULL COMMENT 'GENERIC, BRANDED, RECIPE, CUSTOM',
    preparation_state   VARCHAR(20)    NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT 'RAW, COOKED, DRY, NOT_APPLICABLE',
    basis_quantity      DECIMAL(10, 3) NOT NULL DEFAULT 100,
    basis_unit          VARCHAR(4)     NOT NULL DEFAULT 'G' COMMENT 'G or ML',
    source_id           INT            NOT NULL,
    source_food_ref     VARCHAR(64)    NULL COMMENT 'IFCT code, SR NDB number, FDC id or barcode',
    data_quality        VARCHAR(20)    NOT NULL COMMENT 'VERIFIED, EXTERNAL, USER_ENTERED, ESTIMATED',
    completeness_pct    DECIMAL(5, 2)  NOT NULL DEFAULT 0,
    owner_user_id       BIGINT         NULL COMMENT 'Set for private custom foods and recipe foods',
    recipe_id           BIGINT         NULL,
    local_names         VARCHAR(1000)  NULL,
    barcode             VARCHAR(32)    NULL,
    edible_portion_pct  DECIMAL(5, 2)  NULL,
    cooked_yield_factor DECIMAL(6, 3)  NULL COMMENT 'Cooked weight / raw weight; only for RAW foods',
    cooked_yield_note   VARCHAR(255)   NULL,
    notes               VARCHAR(1000)  NULL,
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_foods_source FOREIGN KEY (source_id) REFERENCES food_data_sources (id),
    CONSTRAINT fk_foods_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_foods_source_ref UNIQUE (source_id, source_food_ref),
    CONSTRAINT ck_foods_basis CHECK (basis_quantity > 0),
    INDEX idx_foods_owner (owner_user_id),
    INDEX idx_foods_name (name),
    INDEX idx_foods_barcode (barcode),
    INDEX idx_foods_recipe (recipe_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE food_nutrients (
    food_id      BIGINT         NOT NULL,
    nutrient_id  INT            NOT NULL,
    amount       DECIMAL(16, 6) NOT NULL COMMENT 'Per foods.basis_quantity in nutrient canonical unit',
    value_origin VARCHAR(20)    NOT NULL DEFAULT 'REPORTED' COMMENT 'REPORTED, DERIVED, CALCULATED, USER_ENTERED',
    coverage_pct DECIMAL(5, 2)  NULL COMMENT 'For recipe foods: share of ingredient mass with data for this nutrient',
    PRIMARY KEY (food_id, nutrient_id),
    CONSTRAINT fk_food_nutrients_food FOREIGN KEY (food_id) REFERENCES foods (id) ON DELETE CASCADE,
    CONSTRAINT fk_food_nutrients_nutrient FOREIGN KEY (nutrient_id) REFERENCES nutrient_definitions (id),
    CONSTRAINT ck_food_nutrients_amount CHECK (amount >= 0),
    INDEX idx_food_nutrients_nutrient (nutrient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE food_portions (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    food_id       BIGINT         NOT NULL,
    label         VARCHAR(100)   NOT NULL COMMENT 'e.g. 1 medium idli, 1 cup',
    unit          VARCHAR(16)    NOT NULL COMMENT 'PIECE, CUP, TBSP, TSP, SERVING, SLICE, BOWL',
    unit_quantity DECIMAL(10, 3) NOT NULL DEFAULT 1,
    basis_amount  DECIMAL(10, 3) NOT NULL COMMENT 'Equivalent amount in foods.basis_unit (g or mL)',
    is_default    BOOLEAN        NOT NULL DEFAULT FALSE,
    source_note   VARCHAR(255)   NULL,
    CONSTRAINT fk_food_portions_food FOREIGN KEY (food_id) REFERENCES foods (id) ON DELETE CASCADE,
    CONSTRAINT uk_food_portions_label UNIQUE (food_id, label),
    CONSTRAINT ck_food_portions_amount CHECK (basis_amount > 0 AND unit_quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_favorite_foods (
    user_id    BIGINT      NOT NULL,
    food_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, food_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_food FOREIGN KEY (food_id) REFERENCES foods (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recipes (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id         BIGINT         NULL COMMENT 'NULL only for built-in system recipes',
    is_system             BOOLEAN        NOT NULL DEFAULT FALSE,
    system_key            VARCHAR(60)    NULL,
    name                  VARCHAR(200)   NOT NULL,
    description           VARCHAR(1000)  NULL,
    instructions          TEXT           NULL,
    servings              DECIMAL(6, 2)  NOT NULL DEFAULT 1,
    total_cooked_weight_g DECIMAL(10, 2) NULL COMMENT 'Weighed after cooking; NULL means sum of ingredient weights',
    serving_label         VARCHAR(100)   NULL,
    food_id               BIGINT         NULL COMMENT 'Food row that makes this recipe searchable and loggable',
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_recipes_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipes_food FOREIGN KEY (food_id) REFERENCES foods (id) ON DELETE SET NULL,
    CONSTRAINT uk_recipes_system_key UNIQUE (system_key),
    CONSTRAINT ck_recipes_servings CHECK (servings > 0),
    INDEX idx_recipes_owner (owner_user_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE foods
    ADD CONSTRAINT fk_foods_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id) ON DELETE SET NULL;

CREATE TABLE recipe_ingredients (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id    BIGINT         NOT NULL,
    food_id      BIGINT         NOT NULL,
    quantity     DECIMAL(10, 3) NOT NULL,
    unit         VARCHAR(16)    NOT NULL,
    portion_id   BIGINT         NULL,
    weight_state VARCHAR(24)    NOT NULL DEFAULT 'AS_DEFINED' COMMENT 'AS_DEFINED or COOKED_WEIGHT_OF_RAW_FOOD',
    basis_amount DECIMAL(12, 3) NOT NULL COMMENT 'Resolved amount in the food basis unit',
    sort_order   INT            NOT NULL DEFAULT 0,
    note         VARCHAR(255)   NULL,
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_ingredients_food FOREIGN KEY (food_id) REFERENCES foods (id),
    CONSTRAINT fk_recipe_ingredients_portion FOREIGN KEY (portion_id) REFERENCES food_portions (id) ON DELETE SET NULL,
    CONSTRAINT ck_recipe_ingredients_qty CHECK (quantity > 0 AND basis_amount > 0),
    INDEX idx_recipe_ingredients_recipe (recipe_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE food_diary_entries (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT         NOT NULL,
    food_id      BIGINT         NOT NULL,
    entry_date   DATE           NOT NULL,
    logged_at    DATETIME(6)    NOT NULL,
    meal_type    VARCHAR(20)    NOT NULL COMMENT 'BREAKFAST, MORNING_SNACK, LUNCH, EVENING_SNACK, DINNER, OTHER',
    quantity     DECIMAL(10, 3) NOT NULL,
    unit         VARCHAR(16)    NOT NULL,
    portion_id   BIGINT         NULL,
    weight_state VARCHAR(24)    NOT NULL DEFAULT 'AS_DEFINED',
    basis_amount DECIMAL(12, 3) NOT NULL COMMENT 'Resolved amount in the food basis unit, used for scaling',
    notes        VARCHAR(500)   NULL,
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_diary_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_food FOREIGN KEY (food_id) REFERENCES foods (id),
    CONSTRAINT fk_diary_portion FOREIGN KEY (portion_id) REFERENCES food_portions (id) ON DELETE SET NULL,
    CONSTRAINT ck_diary_qty CHECK (quantity > 0 AND basis_amount > 0),
    INDEX idx_diary_user_date (user_id, entry_date, meal_type),
    INDEX idx_diary_user_food (user_id, food_id, logged_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE diary_days (
    user_id    BIGINT       NOT NULL,
    day_date   DATE         NOT NULL,
    completed  BOOLEAN      NOT NULL DEFAULT FALSE,
    note       VARCHAR(500) NULL,
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, day_date),
    CONSTRAINT fk_diary_days_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE saved_meals (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(150) NOT NULL,
    meal_type  VARCHAR(20)  NULL,
    notes      VARCHAR(500) NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_saved_meals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_saved_meals_name UNIQUE (user_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE saved_meal_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    saved_meal_id BIGINT         NOT NULL,
    food_id       BIGINT         NOT NULL,
    quantity      DECIMAL(10, 3) NOT NULL,
    unit          VARCHAR(16)    NOT NULL,
    portion_id    BIGINT         NULL,
    weight_state  VARCHAR(24)    NOT NULL DEFAULT 'AS_DEFINED',
    basis_amount  DECIMAL(12, 3) NOT NULL,
    sort_order    INT            NOT NULL DEFAULT 0,
    CONSTRAINT fk_saved_meal_items_meal FOREIGN KEY (saved_meal_id) REFERENCES saved_meals (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_meal_items_food FOREIGN KEY (food_id) REFERENCES foods (id),
    CONSTRAINT fk_saved_meal_items_portion FOREIGN KEY (portion_id) REFERENCES food_portions (id) ON DELETE SET NULL,
    CONSTRAINT ck_saved_meal_items_qty CHECK (quantity > 0 AND basis_amount > 0),
    INDEX idx_saved_meal_items_meal (saved_meal_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE water_logs (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    log_date   DATE         NOT NULL,
    logged_at  DATETIME(6)  NOT NULL,
    amount_ml  INT          NOT NULL,
    note       VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_water_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_water_logs_amount CHECK (amount_ml > 0 AND amount_ml <= 5000),
    INDEX idx_water_logs_user_date (user_id, log_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE weight_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    log_date     DATE          NOT NULL,
    weight_kg    DECIMAL(5, 2) NOT NULL,
    waist_cm     DECIMAL(5, 1) NULL,
    body_fat_pct DECIMAL(4, 1) NULL,
    note         VARCHAR(255)  NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_weight_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_weight_logs_day UNIQUE (user_id, log_date),
    CONSTRAINT ck_weight_logs_values CHECK (weight_kg BETWEEN 20 AND 400
        AND (waist_cm IS NULL OR waist_cm BETWEEN 30 AND 300)
        AND (body_fat_pct IS NULL OR body_fat_pct BETWEEN 2 AND 75))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_nutrient_targets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT         NOT NULL,
    nutrient_id   INT            NOT NULL,
    target_amount DECIMAL(14, 4) NULL,
    upper_limit   DECIMAL(14, 4) NULL,
    created_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_unt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_unt_nutrient FOREIGN KEY (nutrient_id) REFERENCES nutrient_definitions (id),
    CONSTRAINT uk_unt UNIQUE (user_id, nutrient_id),
    CONSTRAINT ck_unt_values CHECK ((target_amount IS NULL OR target_amount >= 0) AND (upper_limit IS NULL OR upper_limit >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
