package com.nutritrack.recipe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Calculates nutrients for the built-in recipes seeded by Flyway, using the same engine as user recipes. */
@Component
public class SystemRecipeInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SystemRecipeInitializer.class);
    private final RecipeService recipeService;

    public SystemRecipeInitializer(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = recipeService.materializeSystemRecipes();
        if (n > 0) {
            log.info("Calculated nutrients for {} built-in recipes", n);
        }
    }
}
