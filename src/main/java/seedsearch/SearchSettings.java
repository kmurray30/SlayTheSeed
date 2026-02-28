package seedsearch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.characters.AbstractPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class SearchSettings {

    private static final String configName = "searchConfig.json";
    private static final String defaultConfigResource = "defaultSearchConfig.json";

    // Core search parameters

    public int ascensionLevel = 0;
    public AbstractPlayer.PlayerClass playerClass = AbstractPlayer.PlayerClass.IRONCLAD;
    public long startSeed = 0L;
    public long endSeed = 100L;
    public boolean verbose = true;
    public boolean exitAfterSearch = false;
    public int highestFloor = 55;
    public int ironcladUnlocks = 5;
    public int silentUnlocks = 5;
    public int defectUnlocks = 5;
    public int watcherUnlocks = 5;
    public int firstBoss = 3;
    public int secondBoss = 3;
    public int thirdBoss = 3;


    // Navigation

    public float eliteRoomWeight = 1.2f;
    public float monsterRoomWeight = 1f;
    public float restRoomWeight = 0f;
    public float shopRoomWeight = 0.9f;
    public float eventRoomWeight = 0.9f;
    public float wingBootsThreshold = 1f; // Wing boots charges are used if weight is changed by this amount

    // General decisions

    public ArrayList<String> relicsToBuy = new ArrayList<>(); // Use the ID for cards and relics
    public ArrayList<String> potionsToBuy = new ArrayList<>();
    public ArrayList<String> cardsToBuy = new ArrayList<>();
    public ArrayList<String> bossRelicsToTake = new ArrayList<>(); // Give them in priority order to always take a relic
    public boolean forceNeowLament = true; // Overrides Neow option below
    public int neowChoice = 3; // 3 is the boss relic trade
    public boolean useShovel = false;
    public boolean speedrunPace = true; // Do you reach Act 3 fast enough to skip Secret Portal?
    public boolean act4 = false;
    public boolean alwaysSpawnBottledTornado = true; // Assume you always have a power for Bottled Tornado to spawn
    public boolean alwaysSpawnBottledLightning = true; // Assume you always have a non-basic skill for Bottled Lightning to spawn
    public boolean alwaysSpawnBottledFlame = true; // Assume you always have a non-basic attack for Bottled Flame to spawn
    public boolean ignorePandoraCards = false; // Don't add the cards from Pandora's Box (as if you glitch it)

    // Event decisions

    public boolean takeSerpentGold = false;
    public boolean takeWarpedTongs = false;
    public boolean takeBigFishRelic = false;
    public boolean takeDeadAdventurerFight = false;
    public boolean takeMausoleumRelic = false;
    public boolean takeScrapOozeRelic = true;
    public boolean takeAddictRelic = true; // Always assume you pay, no taking Shame
    public boolean takeMysteriousSphereFight = false;
    public boolean takeRedMaskAct3 = true;
    public boolean takeMushroomFight = true;
    public boolean takeMaskedBanditFight = true;
    public boolean takeGoldenIdolWithoutCurse = true;
    public boolean takeGoldenIdolWithCurse = false;
    public boolean tradeGoldenIdolForBloody = true;
    public boolean takeCursedTome = true;
    public boolean tradeFaces = false;
    public boolean takeMindBloomGold = false; // Mind Bloom choices in order of priority
    public boolean takeMindBloomFight = true;
    public boolean takeMindBloomUpgrade = false;
    public boolean tradeGoldenIdolForMoney = true; // Moai Head event
    public boolean takePortal = false;
    public int numSensoryStoneCards = 1; // Keep it between 1 and 3, please!
    public boolean takeWindingHallsCurse = false;
    public boolean takeWindingHallsMadness = false;
    public boolean takeColosseumFight = false;
    public boolean takeDrugDealerRelic = false;
    public boolean takeDrugDealerTransform = true;
    public boolean takeLibraryCard = false;
    public boolean takeWeMeetAgainRelic = true;

    // Result filters

    public ArrayList<String> requiredAct1Cards = new ArrayList<>();
    public ArrayList<String> bannedAct1Cards = new ArrayList<>();
    public ArrayList<String> requiredAct1Relics = new ArrayList<>();
    public ArrayList<String> requiredAct1Potions = new ArrayList<>();
    public ArrayList<String> requiredRelics = new ArrayList<>();
    public ArrayList<String> requiredPotions = new ArrayList<>();
    public ArrayList<String> requiredEvents = new ArrayList<>();
    public ArrayList<String> requiredCombats = new ArrayList<>();
    public int minimumElites = 0;
    public int maximumElites = 15;  // Broad default: full run can have ~9 elites across acts
    public int minimumCombats = 0;
    public int maximumCombats = 100;  // Broad default: full run can have 50+ combats
    public int minimumRestSites = 0;

    // Output filters

    public boolean showNeowOptions = true;
    public boolean showCombats = true;
    public boolean showBosses = true;
    public boolean showBossRelics = true;
    public boolean showRelics = true;
    public boolean showShopRelics = true;
    public boolean showShopCards = true;
    public boolean showShopPotions = true;
    public boolean showEvents = true;
    public boolean showCardChoices = true;
    public boolean showPotions = true;
    public boolean showOtherCards = true;
    public boolean showRawRelicPools = false;

    public SearchSettings() {
    }

    /**
     * Loads default config from resource, then overlays searchConfig.json overrides from the working directory.
     */
    public static SearchSettings loadSettings() {
        Gson gson = new Gson();
        try {
            // Load defaults from bundled resource
            JsonObject mergedJson = loadDefaultConfig(gson);
            if (mergedJson == null) {
                return new SearchSettings();
            }

            // Overlay user overrides from searchConfig.json if it exists
            File userConfigFile = new File(configName);
            if (userConfigFile.exists()) {
                try (FileReader reader = new FileReader(userConfigFile)) {
                    JsonObject userJson = gson.fromJson(reader, JsonObject.class);
                    if (userJson != null) {
                        for (String key : userJson.keySet()) {
                            JsonElement value = userJson.get(key);
                            if (value != null) {
                                mergedJson.add(key, value.deepCopy());
                            }
                        }
                    }
                }
            }

            return gson.fromJson(mergedJson, SearchSettings.class);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(String.format("Could not load search settings: %s", e.getMessage()));
            return new SearchSettings();
        }
    }

    private static JsonObject loadDefaultConfig(Gson gson) {
        try (InputStream stream = SearchSettings.class.getResourceAsStream("/" + defaultConfigResource)) {
            if (stream == null) {
                System.out.println(String.format("Default config resource not found: %s", defaultConfigResource));
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                return json != null ? json : new JsonObject();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveSettings() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter writer = new FileWriter(configName);
            gson.toJson(this, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not serialize the search settings.");
        }
    }

    public boolean checkIds() {
        ArrayList<ArrayList<String>> relicLists = new ArrayList<>();
        relicLists.add(relicsToBuy);
        relicLists.add(bossRelicsToTake);
        relicLists.add(requiredAct1Relics);
        relicLists.add(requiredRelics);

        ArrayList<ArrayList<String>> cardLists = new ArrayList<>();
        cardLists.add(cardsToBuy);
        cardLists.add(requiredAct1Cards);
        cardLists.add(bannedAct1Cards);

        ArrayList<ArrayList<String>> potionLists = new ArrayList<>();
        potionLists.add(potionsToBuy);
        potionLists.add(requiredAct1Potions);
        potionLists.add(requiredPotions);

        ArrayList<ArrayList<String>> eventLists = new ArrayList<>();
        eventLists.add(requiredEvents);

        ArrayList<ArrayList<String>> encounterLists = new ArrayList<>();
        encounterLists.add(requiredCombats);

        boolean mistakesMade = false;

        for (ArrayList<String> relicList : relicLists) {
            ArrayList<String> mistakes = IdChecker.findBadRelicIds(relicList);
            if (mistakes.size() > 0) {
                System.out.println(String.format("WARNING: Bad relic ids/names found: %s", mistakes));
                mistakesMade = true;
            }
        }

        for (ArrayList<String> cardList : cardLists) {
            ArrayList<String> mistakes = IdChecker.findBadCardIds(cardList);
            if (mistakes.size() > 0) {
                System.out.println(String.format("WARNING: Bad card ids/names found: %s", mistakes));
                mistakesMade = true;
            }
        }

        for (ArrayList<String> potionList : potionLists) {
            ArrayList<String> mistakes = IdChecker.findBadPotionIds(potionList);
            if (mistakes.size() > 0) {
                System.out.println(String.format("WARNING: Bad potion ids/names found: %s", mistakes));
                mistakesMade = true;
            }
        }

        for (ArrayList<String> eventList : eventLists) {
            ArrayList<String> mistakes = IdChecker.findBadEventIds(eventList);
            if (mistakes.size() > 0) {
                System.out.println(String.format("WARNING: Bad event ids/names found: %s", mistakes));
                mistakesMade = true;
            }
        }

        for (ArrayList<String> encounterList : encounterLists) {
            ArrayList<String> mistakes = IdChecker.findBadEncounterIds(encounterList);
            if (mistakes.size() > 0) {
                System.out.println(String.format("WARNING: Bad encounter ids/names found: %s", mistakes));
                mistakesMade = true;
            }
        }

        return mistakesMade;
    }
}
