package seedsearch;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.neow.NeowReward;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class SeedResult {

    private static void log(String message) {
        System.err.println(message);
        System.err.flush();
    }

    private ArrayList<Reward> miscRewards;
    private ArrayList<Reward> shopRewards;
    private ArrayList<Reward> cardRewards;
    private ArrayList<NeowReward> neowRewards;
    private ArrayList<String> events;
    private ArrayList<String> bosses;
    private ArrayList<String> monsters;
    private ArrayList<String> mapPath;
    private ArrayList<String> trueMapPath;
    private ArrayList<String> bossRelics;
    private ArrayList<String> relics;
    private ArrayList<String> rawCommonRelics;
    private ArrayList<String> rawUncommonRelics;
    private ArrayList<String> rawRareRelics;
    private ArrayList<String> rawBossRelics;
    private ArrayList<String> rawShopRelics;
    private ArrayList<FloorInfo> floorInfos;
    private int numElites;
    private int numCombats;
    private int numRestSites;
    private long seed;

    public SeedResult(long seed) {
        this.seed = seed;
        this.miscRewards = new ArrayList<>();
        this.shopRewards = new ArrayList<>();
        this.cardRewards = new ArrayList<>();
        this.neowRewards = new ArrayList<>();
        this.events = new ArrayList<>();
        this.bosses = new ArrayList<>();
        this.monsters = new ArrayList<>();
        this.mapPath = new ArrayList<>();
        this.trueMapPath = new ArrayList<>();
        this.bossRelics = new ArrayList<>();
        this.relics = new ArrayList<>();
        this.floorInfos = new ArrayList<>();
    }

    public void addFloorInfo(FloorInfo info) {
        floorInfos.add(info);
    }

    public void addCardReward(int floor, ArrayList<AbstractCard> cards) {
        Reward reward = Reward.makeCardReward(floor, cards);
        cardRewards.add(reward);
    }

    public void addCardReward(Reward reward) {
        cardRewards.add(reward);
    }

    public void addAllCardRewards(ArrayList<Reward> rewards) {
        cardRewards.addAll(rewards);
    }

    public void addMiscReward(Reward reward) {
        miscRewards.add(reward);
    }

    public void addShopReward(Reward reward) {
        shopRewards.add(reward);
    }

    public void addNeowRewards(ArrayList<NeowReward> neowRewards) {
        this.neowRewards = neowRewards;
    }

    public void registerCombat(String monsterName) {
        numCombats += 1;
        monsters.add(monsterName);
    }

    public void registerEliteCombat(String monsterName) {
        numElites += 1;
        registerCombat(monsterName);
    }

    public void registerBossCombat(String monsterName) {
        bosses.add(monsterName);
        registerCombat(monsterName);
    }

    public void registerEvent(String eventName) {
        events.add(eventName);
    }

    public void countRestSite() {
        numRestSites++;
    }

    public void addBossReward(ArrayList<String> bossRelics) {
        this.bossRelics.addAll(bossRelics);
    }

    public void addToMapPath(String mapSymbol) {
        mapPath.add(mapSymbol);
    }

    public void addToTrueMapPath(String mapSymbol) {
        trueMapPath.add(mapSymbol);
    }

    public void updateRelics() {
        relics = new ArrayList<>();
        for (AbstractRelic relic : AbstractDungeon.player.relics) {
            relics.add(relic.relicId);
        }
    }

    public void SetCommonRelicPool(ArrayList<String> relics){
        rawCommonRelics = new ArrayList<>(relics);
    }

    public void SetUncommonRelicPool(ArrayList<String> relics){
        rawUncommonRelics = new ArrayList<>(relics);
    }

    public void SetRareRelicPool(ArrayList<String> relics){
        rawRareRelics = new ArrayList<>(relics);
    }

    public void SetBossRelicPool(ArrayList<String> relics){
        rawBossRelics = new ArrayList<>(relics);
    }

    public void SetShopRelicPool(ArrayList<String> relics){
        rawShopRelics = new ArrayList<>(relics);
    }

    public boolean testFinalFilters(SearchSettings settings) {
        if (numCombats > settings.maximumCombats) {
            log(String.format("  Filter FAIL: numCombats=%d > maximumCombats=%d", numCombats, settings.maximumCombats));
            return false;
        }
        if (numCombats < settings.minimumCombats) {
            log(String.format("  Filter FAIL: numCombats=%d < minimumCombats=%d", numCombats, settings.minimumCombats));
            return false;
        }
        if (numElites > settings.maximumElites) {
            log(String.format("  Filter FAIL: numElites=%d > maximumElites=%d", numElites, settings.maximumElites));
            return false;
        }
        if (numElites < settings.minimumElites) {
            log(String.format("  Filter FAIL: numElites=%d < minimumElites=%d", numElites, settings.minimumElites));
            return false;
        }
        if (numRestSites < settings.minimumRestSites) {
            log(String.format("  Filter FAIL: numRestSites=%d < minimumRestSites=%d", numRestSites, settings.minimumRestSites));
            return false;
        }
        if (!events.containsAll(settings.requiredEvents)) {
            log(String.format("  Filter FAIL: missing requiredEvents. Have: %s, need: %s", events, settings.requiredEvents));
            return false;
        }
        if (!relics.containsAll(settings.requiredRelics)) {
            log(String.format("  Filter FAIL: missing requiredRelics. Have: %s, need: %s", relics, settings.requiredRelics));
            return false;
        }
        if (!monsters.containsAll(settings.requiredCombats)) {
            log(String.format("  Filter FAIL: missing requiredCombats. Have: %s, need: %s", monsters, settings.requiredCombats));
            return false;
        }
        ArrayList<String> allPotions = getAllPotionIds();
        for (String potion : settings.requiredPotions) {
            if (allPotions.contains(potion)){
                allPotions.remove(potion);
            } else{
                log(String.format("  Filter FAIL: missing requiredPotion '%s'. Have: %s", potion, getAllPotionIds()));
                return false;
            }
        }
        return true;
    }

    public boolean testAct1Filters(SearchSettings settings) {
        if (!relics.containsAll(settings.requiredAct1Relics)) {
            log(String.format("  Act1 filter FAIL: missing requiredAct1Relics. Have: %s, need: %s", relics, settings.requiredAct1Relics));
            return false;
        }
        ArrayList<String> allCards = getAllCardIds();
        for (String card : settings.bannedAct1Cards) {
            if (allCards.contains(card)) {
                log(String.format("  Act1 filter FAIL: bannedAct1Card '%s' found in deck", card));
                return false;
            }
        }
        for (String card : settings.requiredAct1Cards) {
            if (allCards.contains(card)) {
                allCards.remove(card);
            } else {
                log(String.format("  Act1 filter FAIL: missing requiredAct1Card '%s'. Have: %s", card, getAllCardIds()));
                return false;
            }
        }
        ArrayList<String> allPotions = getAllPotionIds();
        for (String potion : settings.requiredAct1Potions) {
            if (allPotions.contains(potion)){
                allPotions.remove(potion);
            } else{
                log(String.format("  Act1 filter FAIL: missing requiredAct1Potion '%s'. Have: %s", potion, getAllPotionIds()));
                return false;
            }
        }
        return true;
    }

    private ArrayList<String> getAllCardIds() {
        ArrayList<String> allCards = new ArrayList<>();
        for (Reward reward : cardRewards) {
            for (AbstractCard card : reward.cards) {
                allCards.add(card.cardID);
            }
        }
        for (Reward reward : miscRewards) {
            for (AbstractCard card : reward.cards) {
                allCards.add(card.cardID);
            }
        }
        return allCards;
    }

    private ArrayList<String> getAllPotionIds() {
        ArrayList<String> allPotions = new ArrayList<>();
        for (Reward reward : miscRewards) {
            for (AbstractPotion potion : reward.potions){
                allPotions.add(potion.ID);
            }
        }
        return allPotions;
    }

    public static String removeTextFormatting(String text) {
        if (text == null) return "";
        text = text.replaceAll("~|@(\\S+)~|@", "$1");
        text = text.replaceAll("#.|NL", "");
        if (text.startsWith("[ ") && text.endsWith(" ]")) {
            text = text.substring(2, text.length() - 2);
        }
        return text.trim();
    }

    public Reward getLastMiscReward() {
        return miscRewards.isEmpty() ? null : miscRewards.get(miscRewards.size() - 1);
    }

    public void printSeedStats(SearchSettings settings) {
        ArrayList<String> shopRelics = new ArrayList<>();
        ArrayList<String> shopCards = new ArrayList<>();
        ArrayList<String> shopPotions = new ArrayList<>();
        for (Reward shopReward : shopRewards) {
            shopRelics.addAll(shopReward.relics);
            for (AbstractCard card : shopReward.cards) {
                shopCards.add(card.name);
            }
            for (AbstractPotion potion : shopReward.potions)
            {
                shopPotions.add(potion.name);
            }
        }

        System.out.println(MessageFormat.format("Seed: {0} ({1})", SeedHelper.getString(seed), seed));
        if (settings.showNeowOptions) {
            System.out.println("Neow Options:");
            for (NeowReward reward : neowRewards) {
                System.out.println(removeTextFormatting(reward.optionLabel));
            }
        }
        if (settings.showCombats) {
            System.out.println(MessageFormat.format("{0} combats ({1} elite(s)):", numCombats, numElites));
            System.out.println(monsters);
        }
        if (settings.showBosses) {
            System.out.println("Bosses:");
            System.out.println(bosses);
        }
        if (settings.showBossRelics) {
            System.out.println("Boss relics:");
            System.out.println(bossRelics);
        }
        if (settings.showRelics) {
            System.out.println(MessageFormat.format("{0} relics:", relics.size()));
            System.out.println(relics);
        }
        if (settings.showShopRelics) {
            System.out.println("Shop relics:");
            System.out.println(shopRelics);
        }
        if (settings.showShopCards) {
            System.out.println("Shop cards:");
            System.out.println(shopCards);
        }
        if (settings.showShopPotions) {
            System.out.println("Shop potions:");
            System.out.println(shopPotions);
        }
        if (settings.showEvents) {
            System.out.println("Events:");
            System.out.println(events);
        }
        System.out.println("Map path:");
        System.out.println(mapPath);
        System.out.println("True map path:");
        ArrayList<String> combinedMapPath = new ArrayList<>();
        for (int i = 0; i < mapPath.size(); i++) {
            String mapPathItem = mapPath.get(i);
            String trueItem = trueMapPath.get(i);
            if (mapPathItem.equals(trueItem)) {
                combinedMapPath.add(trueItem);
            } else {
                combinedMapPath.add(String.format("%s/%s", mapPathItem, trueItem));
            }
        }
        System.out.println(combinedMapPath);
        if (settings.showCardChoices) {
            System.out.println("Card choices:");
            for (Reward reward : cardRewards) {
                if (reward.cards.size() > 0) {
                    System.out.println(String.format("Floor %d: %s", reward.floor, reward.cards));
                }
            }
        }
        if (settings.showPotions) {
            System.out.println("Potions:");
            for (Reward reward : miscRewards) {
                if (reward.potions.size() > 0) {
                    ArrayList<String> potionNames = new ArrayList<>();
                    for (AbstractPotion potion : reward.potions) {
                        potionNames.add(potion.name);
                    }
                    System.out.println(String.format("Floor %d: %s", reward.floor, potionNames));
                }
            }
        }
        if (settings.showOtherCards) {
            System.out.println("Other cards:");
            for (Reward reward : miscRewards) {
                if (reward.cards.size() > 0) {
                    System.out.println(String.format("Floor %d: %s", reward.floor, reward.cards));
                }
            }
        }
        if (settings.showRawRelicPools) {
            System.out.println("Raw common relic list:");
            System.out.println(rawCommonRelics);
            System.out.println("Raw uncommon relic list:");
            System.out.println(rawUncommonRelics);
            System.out.println("Raw rare relic list:");
            System.out.println(rawRareRelics);
            System.out.println("Raw boss relic list:");
            System.out.println(rawBossRelics);
            System.out.println("Raw shop relic list:");
            System.out.println(rawShopRelics);
        }
        System.out.println("#####################################");
    }

    /**
     * Writes floor-by-floor information to a YAML file in results/ folder.
     * Returns the absolute path to the file.
     */
    public String writeFloorYamlToFile(SearchSettings settings, long seedValue) {
        File resultsDir = new File("results");
        if (!resultsDir.exists()) {
            resultsDir.mkdirs();
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String characterName = settings.playerClass != null ? settings.playerClass.name() : "UNKNOWN";
        String filename = String.format("%s_%dasc_%s.yaml", characterName, settings.ascensionLevel, timestamp);
        File outputFile = new File(resultsDir, filename);
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("---\n");
            writer.write("SearchConfig:\n");
            writer.write(jsonToYaml(new Gson().toJsonTree(settings).getAsJsonObject(), 2));
            writer.write("\nRun:\n");
            writer.write("  Seed: " + SeedHelper.getString(seedValue) + " (" + seedValue + ")\n");
            writer.write("Floors:\n");
            for (FloorInfo info : floorInfos) {
                appendFloorInfoYaml(writer, info);
            }
            return outputFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write YAML file: " + e.getMessage());
        }
    }

    private static String jsonToYaml(JsonObject json, int indentSpaces) {
        StringBuilder sb = new StringBuilder();
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < indentSpaces; i++) indentBuilder.append(" ");
        String indent = indentBuilder.toString();
        for (String key : json.keySet()) {
            JsonElement value = json.get(key);
            if (value.isJsonNull()) {
                sb.append(indent).append(key).append(": null\n");
            } else if (value.isJsonPrimitive()) {
                JsonPrimitive prim = value.getAsJsonPrimitive();
                String valStr = prim.isString() ? prim.getAsString()
                        : prim.isBoolean() ? String.valueOf(prim.getAsBoolean())
                        : String.valueOf(prim.getAsNumber());
                sb.append(indent).append(key).append(": ").append(yamlEscape(valStr)).append("\n");
            } else if (value.isJsonArray()) {
                sb.append(indent).append(key).append(":\n");
                for (JsonElement item : value.getAsJsonArray()) {
                    String itemStr = item.isJsonPrimitive() ? item.getAsString() : item.toString();
                    if (item.isJsonPrimitive()) {
                        sb.append(indent).append("  - ").append(yamlEscape(itemStr)).append("\n");
                    } else {
                        sb.append(indent).append("  - ").append(itemStr).append("\n");
                    }
                }
            } else if (value.isJsonObject()) {
                sb.append(indent).append(key).append(":\n");
                sb.append(jsonToYaml(value.getAsJsonObject(), indentSpaces + 2));
            }
        }
        return sb.toString();
    }

    private void appendFloorInfoYaml(FileWriter writer, FloorInfo info) throws IOException {
        writer.write("  Floor " + info.floor + ":\n");
        if (info.path != null) {
            writer.write("    Path: " + info.path + "\n");
        }
        if (info.type != null) {
            writer.write("    Type: " + info.type + "\n");
        }
        if (info.name != null && !info.name.isEmpty()) {
            String nameKey = "Name:";
            if (info.type != null && info.type.equals("Event")) {
                nameKey = "Instance:";
            } else if (info.type != null && info.type.equals("Neow")) {
                nameKey = "Starting Relic:";
            }
            writer.write("    " + nameKey + " " + yamlEscape(info.name) + "\n");
        }
        if (info.type != null && info.type.equals("Neow")) {
            if (!info.neowOptions.isEmpty()) {
                writer.write("    Neow Options:\n");
                for (int optionIndex = 0; optionIndex < info.neowOptions.size(); optionIndex++) {
                    String opt = info.neowOptions.get(optionIndex);
                    if (optionIndex == info.neowChosenIndex) {
                        writer.write("      - \"" + opt.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n");
                    } else {
                        writer.write("      - " + yamlEscape(opt) + "\n");
                    }
                }
            }
            if (!info.neowChoice.isEmpty()) {
                writer.write("    Neow Choice:\n");
                for (String choice : info.neowChoice) {
                    writer.write("      - " + yamlEscape(choice) + "\n");
                }
            }
        }
        if (info.type != null && info.type.equals("Shop")) {
            if (!info.cardRewards.isEmpty()) {
                writer.write("    Cards:\n");
                for (String card : info.cardRewards) {
                    writer.write("      - " + yamlEscape(card) + "\n");
                }
            }
            if (!info.relics.isEmpty()) {
                writer.write("    Relics:\n");
                for (String relicId : info.relics) {
                    String displayName = getRelicDisplayName(relicId);
                    writer.write("      - " + yamlEscape(displayName) + "\n");
                }
            }
            if (!info.potions.isEmpty()) {
                writer.write("    Potions:\n");
                for (String potion : info.potions) {
                    writer.write("      - " + yamlEscape(potion) + "\n");
                }
            }
        } else {
            if (!info.cardRewards.isEmpty()) {
                String key = info.cardRewards.size() == 1 ? "Card Reward:" : "Card Rewards:";
                writer.write("    " + key + "\n");
                for (String card : info.cardRewards) {
                    writer.write("      - " + yamlEscape(card) + "\n");
                }
            }
            if (!info.relics.isEmpty()) {
                String key = info.relics.size() == 1 ? "Relic:" : "Relics:";
                writer.write("    " + key + "\n");
                for (String relicId : info.relics) {
                    String displayName = getRelicDisplayName(relicId);
                    writer.write("      - " + yamlEscape(displayName) + "\n");
                }
            }
            if (!info.potions.isEmpty()) {
                String key = info.potions.size() == 1 ? "Potion:" : "Potions:";
                writer.write("    " + key + "\n");
                for (String potion : info.potions) {
                    writer.write("      - " + yamlEscape(potion) + "\n");
                }
            }
        }
        if (!info.bossRelics.isEmpty()) {
            writer.write("    Boss Relic:\n");
            for (String relicId : info.bossRelics) {
                String displayName = getRelicDisplayName(relicId);
                writer.write("      - " + yamlEscape(displayName) + "\n");
            }
        }
    }

    private String getRelicDisplayName(String relicId) {
        try {
            return RelicLibrary.getRelic(relicId).name;
        } catch (Exception e) {
            return relicId;
        }
    }

    private static String yamlEscape(String s) {
        if (s == null) return "";
        if (s.contains(":") || s.contains("#") || s.startsWith(" ") || s.startsWith("-")) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return s;
    }
}
