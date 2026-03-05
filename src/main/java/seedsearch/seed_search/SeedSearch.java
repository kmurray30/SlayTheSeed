package seedsearch.seed_search;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import seedsearch.core.SearchSettings;
import seedsearch.core.SeedResult;

import static java.lang.System.exit;

public class SeedSearch {

    public static boolean loadingEnabled = true;
    public static SearchSettings settings;

    private static void log(String message) {
        System.err.println(message);
        System.err.flush();
    }

    private static void unlockBosses(String[] bosslist, int unlockLevel) {
        for (int i = 0; i < unlockLevel; i++) {
            if (i >= 3) {
                break;
            }
            UnlockTracker.unlockPref.putInteger(bosslist[i], 2);
            UnlockTracker.bossSeenPref.putInteger(bosslist[i], 1);
        }
    }

    private static boolean isPlayerClassValid(SearchSettings settings) {
        if (settings.playerClass == null) {
            System.out.println("Invalid playerClass specified in search settings.");
            System.out.println("Possible values: ");
            for (AbstractPlayer.PlayerClass c: AbstractPlayer.PlayerClass.values()) {
                System.out.println(c.name());
            }
            return false;
        }
        return true;
    }

    /**
     * Sets up UnlockTracker and runs a single seed with the given settings.
     * Call only after game is initialized (CardLibrary, RelicLibrary, etc.).
     * Returns SeedResult if the seed passes filters; throws if it fails.
     */
    public static SeedResult runWithSettings(SearchSettings settings, long seed) {
        if (!isPlayerClassValid(settings)) {
            throw new IllegalArgumentException("Invalid playerClass in search settings");
        }
        setupUnlockTrackerForRun(settings);
        SeedRunner runner = new SeedRunner(settings);
        boolean passed = runner.runSeed(seed);
        if (!passed) {
            throw new RuntimeException("Seed " + seed + " failed filter (see stderr for details)");
        }
        return runner.getSeedResult();
    }

    public static void setupUnlockTrackerForRun(SearchSettings settings) {
        String[] expectedBaseUnlocks = {"The Silent", "Defect", "Watcher"};
        String[] firstBossUnlocks = {"GUARDIAN", "GHOST", "SLIME"};
        String[] secondBossUnlocks = {"CHAMP", "AUTOMATON", "COLLECTOR"};
        String[] thirdBossUnlocks = {"CROW", "DONUT", "WIZARD"};
        UnlockTracker.unlockPref.data.clear();
        UnlockTracker.bossSeenPref.data.clear();
        for (String key : expectedBaseUnlocks) {
            UnlockTracker.unlockPref.putInteger(key, 2);
        }
        unlockBosses(firstBossUnlocks, settings.firstBoss);
        unlockBosses(secondBossUnlocks, settings.secondBoss);
        unlockBosses(thirdBossUnlocks, settings.thirdBoss);
        UnlockTracker.resetUnlockProgress(AbstractPlayer.PlayerClass.IRONCLAD);
        UnlockTracker.unlockProgress.putInteger("IRONCLADUnlockLevel", settings.ironcladUnlocks);
        UnlockTracker.resetUnlockProgress(AbstractPlayer.PlayerClass.THE_SILENT);
        UnlockTracker.unlockProgress.putInteger("THE_SILENTUnlockLevel", settings.silentUnlocks);
        UnlockTracker.resetUnlockProgress(AbstractPlayer.PlayerClass.DEFECT);
        UnlockTracker.unlockProgress.putInteger("DEFECTUnlockLevel", settings.defectUnlocks);
        UnlockTracker.resetUnlockProgress(AbstractPlayer.PlayerClass.WATCHER);
        UnlockTracker.unlockProgress.putInteger("WATCHERUnlockLevel", settings.watcherUnlocks);
        UnlockTracker.retroactiveUnlock();
        UnlockTracker.refresh();
    }

    public static void search() {
        loadingEnabled = false;
        log("SeedSearch starting...");
        settings = SearchSettings.loadSettings();
        if (!isPlayerClassValid(settings)) {
            exit(1);
        }
        setupUnlockTrackerForRun(settings);
        SeedRunner runner = new SeedRunner(settings);
        ArrayList<Long> foundSeeds = new ArrayList<>();
        long seedCount = settings.endSeed - settings.startSeed;
        log(String.format("Search range: seeds %d to %d (exclusive) = %d seeds", settings.startSeed, settings.endSeed, seedCount));

        File runFolder = createRunFolder(settings);
        for (long seed = settings.startSeed; seed < settings.endSeed; seed++) {
            try {
                log(String.format("Simulating seed %d...", seed));
                boolean passed = runner.runSeed(seed);
                if (passed) {
                    foundSeeds.add(seed);
                    log(String.format("Seed %d: PASSED", seed));
                    if (settings.verbose) {
                        runner.getSeedResult().printSeedStats(settings);
                        String yamlPath = runner.getSeedResult().writeFloorYamlToFile(settings, seed, runFolder);
                        log("YAML output: " + yamlPath);
                    }
                } else {
                    log(String.format("Seed %d: FAILED (filter rejection - see above for which filter)", seed));
                }
            } catch (Exception exception) {
                log(String.format("Seed %d: EXCEPTION - %s", seed, exception.getMessage()));
                exception.printStackTrace();
            }
        }
        log(String.format("%d seeds found: %s", foundSeeds.size(), foundSeeds));

        if (settings.exitAfterSearch) {
            exit(0);
        } else {
            log("Search complete. Manually close this program when finished.");
        }
    }

    /**
     * Creates search_results/&lt;character&gt;_a&lt;ascension&gt;_&lt;timestamp&gt;/ and returns the folder.
     */
    private static File createRunFolder(SearchSettings settings) {
        File resultsDir = new File("search_results");
        if (!resultsDir.exists()) {
            resultsDir.mkdirs();
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String characterName = settings.playerClass != null ? settings.playerClass.name() : "UNKNOWN";
        String folderName = String.format("%s_a%d_%s", characterName, settings.ascensionLevel, timestamp);
        File runFolder = new File(resultsDir, folderName);
        runFolder.mkdirs();
        return runFolder;
    }

}
