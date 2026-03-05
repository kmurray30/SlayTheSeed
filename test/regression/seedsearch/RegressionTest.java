package seedsearch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regression test: runs the sim for a seed range and compares output to expected YAML files.
 * Brittle by design - any change in sim output fails the test.
 */
public class RegressionTest {

    private static final String CONFIG_FILE = "configs/regressionTestConfig.json";
    private static final String EXPECTED_DIR = "test/regression/expected";

    /** Filter fields to ignore when comparing SearchConfig to expected YAML. */
    private static final Set<String> FILTER_FIELDS = new HashSet<>(Arrays.asList(
            "requiredAct1Cards", "bannedAct1Cards", "requiredAct1Relics", "requiredAct1Potions",
            "requiredRelics", "requiredPotions", "requiredEvents", "requiredCombats",
            "minimumElites", "maximumElites", "minimumCombats", "maximumCombats", "minimumRestSites",
            "startSeed", "endSeed"  // Per-seed in regression loop; expected files may have full range
    ));

    /** Pattern to extract seed from "Seed: 1 (1)" or similar in YAML Run block. */
    private static final Pattern SEED_PATTERN = Pattern.compile("\\(([0-9]+)\\)");

    public static void run() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (!configFile.exists()) {
                printConfigNotFound(configFile);
                System.exit(1);
            }

            Gson gson = new Gson();
            JsonObject configJson;
            try (FileReader reader = new FileReader(configFile)) {
                configJson = gson.fromJson(reader, JsonObject.class);
            }

            int[] seedRange = parseSeedRange(configJson);
            SearchSettings settings = gson.fromJson(configJson, SearchSettings.class);
            applyPermissiveFilters(settings);

            int startSeed = seedRange[0];
            int endSeed = seedRange[1];
            File expectedDir = new File(EXPECTED_DIR);
            if (!expectedDir.exists() || !expectedDir.isDirectory()) {
                System.err.println("Expected results directory not found: " + EXPECTED_DIR + "/");
                printUsage();
                System.exit(1);
            }

            validateBeforeRun(configJson, settings, startSeed, endSeed, expectedDir);

            for (long seed = startSeed; seed < endSeed; seed++) {
                String expectedFilename = buildExpectedFilename(settings, seed);
                File expectedFile = new File(expectedDir, expectedFilename);

                settings.startSeed = seed;
                settings.endSeed = seed + 1;

                SeedResult result = SeedSearch.runWithSettings(settings, seed);

                String actualYaml = result.toYamlString(settings, seed);
                String expectedYaml = new String(Files.readAllBytes(expectedFile.toPath()), StandardCharsets.UTF_8);

                Yaml yamlParser = new Yaml();
                Object actualParsed = yamlParser.load(actualYaml);
                Object expectedParsed = yamlParser.load(expectedYaml);

                DiffResult diff = deepCompare(actualParsed, expectedParsed, "");
                String seedLabel = buildExpectedFilename(settings, seed).replace(".yaml", "");
                if (diff != null) {
                    System.err.println(seedLabel + ": FAIL ❌");
                    System.err.println("First difference at path: " + diff.path);
                    System.err.println("Expected: " + formatValue(diff.expectedValue));
                    System.err.println("Actual:   " + formatValue(diff.actualValue));
                    System.exit(1);
                }
                System.out.println(seedLabel + ": PASS ✅");
            }

            System.out.println("Regression test PASSED: " + (endSeed - startSeed) + " seeds");
            System.exit(0);

        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Build expected filename from config: &lt;character&gt;_a&lt;ascension&gt;_seed_&lt;seed&gt;.yaml
     * Example: THE_SILENT, ascension 20, seed 1 → silent_a20_seed_1.yaml
     */
    private static String buildExpectedFilename(SearchSettings settings, long seed) {
        String characterShorthand = settings.playerClass.name().toLowerCase().replace("the_", "");
        return characterShorthand + "_a" + settings.ascensionLevel + "_seed_" + seed + ".yaml";
    }

    /**
     * Pre-flight validation: before running any seeds, verify expected files exist and match config.
     */
    @SuppressWarnings("unchecked")
    private static void validateBeforeRun(JsonObject configJson, SearchSettings settings,
                                         int startSeed, int endSeed, File expectedDir) {
        Yaml yamlParser = new Yaml();

        for (long seed = startSeed; seed < endSeed; seed++) {
            String expectedFilename = buildExpectedFilename(settings, seed);
            File expectedFile = new File(expectedDir, expectedFilename);

            if (!expectedFile.exists()) {
                System.err.println("Expected file missing: " + expectedDir.getName() + "/" + expectedFilename);
                printUsage();
                System.exit(1);
            }

            Object expectedParsed = null;
            try {
                String expectedYaml = new String(Files.readAllBytes(expectedFile.toPath()), StandardCharsets.UTF_8);
                expectedParsed = yamlParser.load(expectedYaml);
            } catch (Exception exception) {
                System.err.println("Failed to parse expected file: " + expectedFile.getAbsolutePath());
                exception.printStackTrace();
                System.exit(1);
            }

            if (!(expectedParsed instanceof Map)) {
                System.err.println("Expected file has invalid structure (root is not a map): " + expectedFilename);
                System.exit(1);
            }

            Map<String, Object> rootMap = (Map<String, Object>) expectedParsed;
            Map<String, Object> searchConfig = (Map<String, Object>) rootMap.get("SearchConfig");
            Map<String, Object> runBlock = (Map<String, Object>) rootMap.get("Run");

            if (searchConfig == null) {
                System.err.println("Expected file missing SearchConfig: " + expectedFilename);
                System.exit(1);
            }
            if (runBlock == null) {
                System.err.println("Expected file missing Run block: " + expectedFilename);
                System.exit(1);
            }

            Object expectedPlayerClass = searchConfig.get("playerClass");
            String configPlayerClass = settings.playerClass.name();
            if (expectedPlayerClass == null || !expectedPlayerClass.toString().equals(configPlayerClass)) {
                System.err.println("playerClass mismatch in " + expectedFilename
                        + ": config has " + configPlayerClass
                        + ", expected file has " + (expectedPlayerClass != null ? expectedPlayerClass : "null"));
                printUsage();
                System.exit(1);
            }

            Object expectedAscension = searchConfig.get("ascensionLevel");
            int configAscension = settings.ascensionLevel;
            int expectedAscensionInt = expectedAscension instanceof Number
                    ? ((Number) expectedAscension).intValue()
                    : Integer.parseInt(String.valueOf(expectedAscension));
            if (expectedAscensionInt != configAscension) {
                System.err.println("ascensionLevel mismatch in " + expectedFilename
                        + ": config has " + configAscension + ", expected file has " + expectedAscensionInt);
                printUsage();
                System.exit(1);
            }

            Object seedValue = runBlock.get("Seed");
            if (seedValue != null) {
                Matcher matcher = SEED_PATTERN.matcher(seedValue.toString());
                if (matcher.find()) {
                    long expectedSeed = Long.parseLong(matcher.group(1));
                    if (expectedSeed != seed) {
                        System.err.println("Seed mismatch in " + expectedFilename
                                + ": file contains seed " + expectedSeed + ", config expects seed " + seed);
                        printUsage();
                        System.exit(1);
                    }
                }
            }

            compareNonFilterSettings(configJson, searchConfig, expectedFilename);
        }
    }

    /**
     * Compare non-filter SearchConfig fields between config and expected YAML.
     */
    private static void compareNonFilterSettings(JsonObject configJson, Map<String, Object> expectedSearchConfig,
                                                 String expectedFilename) {
        for (String key : expectedSearchConfig.keySet()) {
            if (FILTER_FIELDS.contains(key)) {
                continue;
            }
            if (!configJson.has(key)) {
                continue;
            }
            Object configValue = configJson.get(key);
            Object expectedValue = expectedSearchConfig.get(key);
            if (!settingsValuesEqual(configValue, expectedValue)) {
                System.err.println("Settings mismatch in " + expectedFilename + " for field '" + key + "':");
                System.err.println("  Config has:   " + configValue);
                System.err.println("  Expected has: " + expectedValue);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static boolean settingsValuesEqual(Object configValue, Object expectedValue) {
        if (configValue == null && expectedValue == null) return true;
        if (configValue == null || expectedValue == null) {
            return isEmptyEquivalent(configValue, expectedValue);
        }

        if (configValue instanceof JsonPrimitive) {
            JsonPrimitive primitive = (JsonPrimitive) configValue;
            if (primitive.isNumber()) {
                double configNum = primitive.getAsDouble();
                if (expectedValue instanceof Number) {
                    return configNum == ((Number) expectedValue).doubleValue();
                }
                try {
                    return configNum == Double.parseDouble(expectedValue.toString());
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean() == Boolean.parseBoolean(String.valueOf(expectedValue));
            }
            return primitive.getAsString().equals(expectedValue.toString());
        }

        if (configValue instanceof Number && expectedValue instanceof Number) {
            return ((Number) configValue).doubleValue() == ((Number) expectedValue).doubleValue();
        }
        return configValue.toString().equals(expectedValue.toString());
    }

    /** Treat null and empty list/array as equivalent for comparison. */
    private static boolean isEmptyEquivalent(Object configValue, Object expectedValue) {
        if (configValue == null && expectedValue == null) return true;
        if (configValue == null) {
            return isEmptyCollection(expectedValue);
        }
        if (expectedValue == null) {
            return isEmptyJsonArray(configValue);
        }
        return false;
    }

    private static boolean isEmptyCollection(Object value) {
        if (value instanceof List) return ((List<?>) value).isEmpty();
        return false;
    }

    private static boolean isEmptyJsonArray(Object value) {
        if (value instanceof com.google.gson.JsonArray) {
            return ((com.google.gson.JsonArray) value).isEmpty();
        }
        return false;
    }

    private static void printConfigNotFound(File configFile) {
        System.err.println("Regression test config not found: " + configFile.getAbsolutePath());
        System.err.println("Create configs/regressionTestConfig.json (see configs/defaultSearchConfig.json for structure).");
        printUsage();
    }

    private static void printUsage() {
        System.err.println("Usage: Run from project root. Config: configs/regressionTestConfig.json");
        System.err.println("Expected files: test/regression/expected/<character>_a<ascension>_seed_<seed>.yaml");
        System.err.println("Example: test/regression/expected/silent_a20_seed_1.yaml for THE_SILENT, ascension 20, seed 1");
    }

    private static int[] parseSeedRange(JsonObject config) {
        if (!config.has("seedRange")) {
            System.err.println(CONFIG_FILE + " must contain seedRange: [start, end]");
            System.exit(1);
        }
        JsonArray arr = config.getAsJsonArray("seedRange");
        if (arr.size() != 2) {
            System.err.println("seedRange must have exactly 2 elements: [start, end]");
            System.exit(1);
        }
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()};
    }

    private static void applyPermissiveFilters(SearchSettings settings) {
        settings.requiredAct1Cards = new ArrayList<>();
        settings.bannedAct1Cards = new ArrayList<>();
        settings.requiredAct1Relics = new ArrayList<>();
        settings.requiredAct1Potions = new ArrayList<>();
        settings.requiredRelics = new ArrayList<>();
        settings.requiredPotions = new ArrayList<>();
        settings.requiredEvents = new ArrayList<>();
        settings.requiredCombats = new ArrayList<>();
        settings.minimumElites = 0;
        settings.maximumElites = 20;
        settings.minimumCombats = 0;
        settings.maximumCombats = 100;
        settings.minimumRestSites = 0;
    }

    private static class DiffResult {
        final String path;
        final Object actualValue;
        final Object expectedValue;

        DiffResult(String path, Object actualValue, Object expectedValue) {
            this.path = path;
            this.actualValue = actualValue;
            this.expectedValue = expectedValue;
        }
    }

    /**
     * Deep-compare two YAML-parsed structures. Returns null if equal, or DiffResult for first difference.
     */
    @SuppressWarnings("unchecked")
    private static DiffResult deepCompare(Object actual, Object expected, String path) {
        if (actual == null && expected == null) {
            return null;
        }
        if (actual == null) {
            return new DiffResult(path, null, expected);
        }
        if (expected == null) {
            return new DiffResult(path, actual, null);
        }

        if (actual instanceof Map && expected instanceof Map) {
            Map<String, Object> actualMap = (Map<String, Object>) actual;
            Map<String, Object> expectedMap = (Map<String, Object>) expected;

            for (String key : expectedMap.keySet()) {
                if (path.equals("SearchConfig") && FILTER_FIELDS.contains(key)) {
                    continue;  // Skip filter fields in SearchConfig (e.g. startSeed, endSeed vary per run)
                }
                String subPath = path.isEmpty() ? key : path + "." + key;
                if (!actualMap.containsKey(key)) {
                    return new DiffResult(subPath + " (missing in actual)", null, expectedMap.get(key));
                }
                DiffResult diff = deepCompare(actualMap.get(key), expectedMap.get(key), subPath);
                if (diff != null) {
                    return diff;
                }
            }
            for (String key : actualMap.keySet()) {
                if (path.equals("SearchConfig") && FILTER_FIELDS.contains(key)) {
                    continue;
                }
                if (!expectedMap.containsKey(key)) {
                    return new DiffResult((path.isEmpty() ? key : path + "." + key) + " (extra in actual)",
                            actualMap.get(key), null);
                }
            }
            return null;
        }

        if (actual instanceof List && expected instanceof List) {
            List<?> actualList = (List<?>) actual;
            List<?> expectedList = (List<?>) expected;
            if (actualList.size() != expectedList.size()) {
                return new DiffResult(path + " (list size)", actualList.size(), expectedList.size());
            }
            for (int index = 0; index < actualList.size(); index++) {
                String subPath = path + "[" + index + "]";
                DiffResult diff = deepCompare(actualList.get(index), expectedList.get(index), subPath);
                if (diff != null) {
                    return diff;
                }
            }
            return null;
        }

        if (valuesEqual(actual, expected)) {
            return null;
        }
        return new DiffResult(path, actual, expected);
    }

    private static boolean valuesEqual(Object actual, Object expected) {
        if (actual.equals(expected)) {
            return true;
        }
        if (actual instanceof Number && expected instanceof Number) {
            return ((Number) actual).doubleValue() == ((Number) expected).doubleValue();
        }
        if (actual instanceof Number && expected instanceof String) {
            try {
                return Double.parseDouble(expected.toString()) == ((Number) actual).doubleValue();
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (actual instanceof String && expected instanceof Number) {
            try {
                return Double.parseDouble(actual.toString()) == ((Number) expected).doubleValue();
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Map || value instanceof List) {
            return value.toString();
        }
        return String.valueOf(value);
    }

}
