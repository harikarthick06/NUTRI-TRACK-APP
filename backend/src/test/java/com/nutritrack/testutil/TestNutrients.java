package com.nutritrack.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nutritrack.nutrient.NutrientCatalog;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrient.NutrientDefinitionRepository;
import java.util.ArrayList;
import java.util.List;

/** The nutrient definitions from migration V2, for unit tests that do not start a database. */
public final class TestNutrients {
    private TestNutrients() {
    }

    private static final Object[][] DEFS = {
            {"ENERGY_KCAL", "KCAL", "ENERGY", 1008}, {"PROTEIN", "G", "MACRONUTRIENT", 1003},
            {"CARBOHYDRATE", "G", "MACRONUTRIENT", 1005}, {"AVAILABLE_CARBOHYDRATE", "G", "MACRONUTRIENT", null},
            {"FAT_TOTAL", "G", "MACRONUTRIENT", 1004}, {"FIBER", "G", "MACRONUTRIENT", 1079},
            {"SUGARS_TOTAL", "G", "MACRONUTRIENT", 2000}, {"SUGARS_ADDED", "G", "MACRONUTRIENT", 1235},
            {"CHOLESTEROL", "MG", "MACRONUTRIENT", 1253}, {"FAT_SATURATED", "G", "FATTY_ACID", 1258},
            {"FAT_MONOUNSATURATED", "G", "FATTY_ACID", 1292}, {"FAT_POLYUNSATURATED", "G", "FATTY_ACID", 1293},
            {"FAT_TRANS", "G", "FATTY_ACID", 1257}, {"OMEGA3_TOTAL", "G", "FATTY_ACID", null},
            {"OMEGA6_TOTAL", "G", "FATTY_ACID", null}, {"ALA", "G", "FATTY_ACID", 1404},
            {"LINOLEIC_ACID", "G", "FATTY_ACID", 1316}, {"EPA", "G", "FATTY_ACID", 1278}, {"DHA", "G", "FATTY_ACID", 1272},
            {"VITAMIN_A_RAE", "UG", "VITAMIN", 1106}, {"VITAMIN_C", "MG", "VITAMIN", 1162},
            {"VITAMIN_D", "UG", "VITAMIN", 1114}, {"VITAMIN_E", "MG", "VITAMIN", 1109}, {"VITAMIN_K", "UG", "VITAMIN", 1185},
            {"THIAMIN", "MG", "VITAMIN", 1165}, {"RIBOFLAVIN", "MG", "VITAMIN", 1166}, {"NIACIN", "MG", "VITAMIN", 1167},
            {"PANTOTHENIC_ACID", "MG", "VITAMIN", 1170}, {"VITAMIN_B6", "MG", "VITAMIN", 1175},
            {"BIOTIN", "UG", "VITAMIN", 1176}, {"FOLATE_DFE", "UG", "VITAMIN", 1190}, {"VITAMIN_B12", "UG", "VITAMIN", 1178},
            {"CALCIUM", "MG", "MINERAL", 1087}, {"IRON", "MG", "MINERAL", 1089}, {"MAGNESIUM", "MG", "MINERAL", 1090},
            {"PHOSPHORUS", "MG", "MINERAL", 1091}, {"POTASSIUM", "MG", "MINERAL", 1092}, {"SODIUM", "MG", "MINERAL", 1093},
            {"ZINC", "MG", "MINERAL", 1095}, {"COPPER", "MG", "MINERAL", 1098}, {"MANGANESE", "MG", "MINERAL", 1101},
            {"SELENIUM", "UG", "MINERAL", 1103}, {"IODINE", "UG", "MINERAL", 1100}, {"CHROMIUM", "UG", "MINERAL", 1096},
            {"MOLYBDENUM", "UG", "MINERAL", 1102}, {"CHLORIDE", "MG", "MINERAL", 1088}, {"FLUORIDE", "MG", "MINERAL", 1099},
            {"CHOLINE", "MG", "OTHER", 1180}, {"WATER", "G", "OTHER", 1051},
    };

    public static List<NutrientDefinition> definitions() {
        List<NutrientDefinition> out = new ArrayList<>();
        for (int i = 0; i < DEFS.length; i++) {
            Object[] d = DEFS[i];
            out.add(new NutrientDefinition(i + 1, (String) d[0], (String) d[0], (String) d[1], (String) d[2], (i + 1) * 10,
                    (Integer) d[3]));
        }
        return out;
    }

    public static NutrientCatalog catalog() {
        NutrientDefinitionRepository repo = mock(NutrientDefinitionRepository.class);
        when(repo.findAllByOrderByDisplayOrderAsc()).thenReturn(definitions());
        return new NutrientCatalog(repo);
    }
}
