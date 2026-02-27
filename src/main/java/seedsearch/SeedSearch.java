package seedsearch;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

import java.util.ArrayList;

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

    public static void search() {
        loadingEnabled = false;
        log("SeedSearch starting...");
        settings = SearchSettings.loadSettings();
        if (!isPlayerClassValid(settings)) {
            exit(1);
        }
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
        SeedRunner runner = new SeedRunner(settings);
        ArrayList<Long> foundSeeds = new ArrayList<>();
        long seedCount = settings.endSeed - settings.startSeed;
        log(String.format("Search range: seeds %d to %d (exclusive) = %d seeds", settings.startSeed, settings.endSeed, seedCount));
        for (long seed = settings.startSeed; seed < settings.endSeed; seed++) {
            try {
                log(String.format("Simulating seed %d...", seed));
                boolean passed = runner.runSeed(seed);
                if (passed) {
                    foundSeeds.add(seed);
                    log(String.format("Seed %d: PASSED", seed));
                    if (settings.verbose) {
                        runner.getSeedResult().printSeedStats(settings);
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

}
