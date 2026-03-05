package seedsearch.core.engine;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.colorless.JAX;
import com.megacrit.cardcrawl.cards.colorless.Madness;
import com.megacrit.cardcrawl.cards.curses.*;
import com.megacrit.cardcrawl.cards.red.Strike_Red;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.*;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.beyond.*;
import com.megacrit.cardcrawl.events.city.*;
import com.megacrit.cardcrawl.events.exordium.*;
import com.megacrit.cardcrawl.events.shrines.*;
import com.megacrit.cardcrawl.helpers.CardHelper;
import com.megacrit.cardcrawl.helpers.EventHelper;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.neow.NeowReward;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.*;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rewards.chests.AbstractChest;
import com.megacrit.cardcrawl.rewards.chests.BossChest;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.CharSelectInfo;
import com.megacrit.cardcrawl.shop.Merchant;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import seedsearch.core.CombatRewards;
import seedsearch.core.FloorInfo;
import seedsearch.core.Reward;
import seedsearch.core.SeedResult;
import seedsearch.core.SearchSettings;
import seedsearch.core.StandaloneHooks;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.megacrit.cardcrawl.helpers.MonsterHelper.*;

/**
 * Pausable run engine that extracts simulation logic from SeedRunner.
 * Supports init(), run(policy), applyChoice(), getSeedResult(), getActMap().
 * When policy.getPathForCurrentAct() returns non-null, auto-advances through path without pausing for PathChoice.
 */
public class RunEngine {

    public enum Phase {
        INIT,
        NEOW_PENDING,
        AFTER_NEOW,
        NEED_PATH,
        IN_PATH,
        BOSS_REWARDS,
        BOSS_RELIC_PENDING,
        ACT_TRANSITION,
        DONE
    }

    private AbstractPlayer player;
    private int currentAct;
    private int actFloor;
    private int bootsCharges = 0;
    private SeedResult seedResult;
    private SearchSettings settings;
    private long currentSeed;

    private Phase phase = Phase.INIT;
    private DecisionPoint pendingDecisionPoint;
    private Object pendingChoice;

    private ArrayList<NeowReward> neowRewards;
    private ArrayList<MapRoomNode> currentPath;
    private ArrayList<ArrayList<MapRoomNode>> currentMap;

    private ArrayList<String> bossRelicStrings;

    public void init(long seed, AbstractPlayer.PlayerClass playerClass, int ascension, SearchSettings settings) {
        this.settings = settings;
        AbstractDungeon.fadeColor = Settings.SHADOW_COLOR;
        CharacterManager characterManager = new CharacterManager();
        CardCrawlGame.characterManager = characterManager;
        characterManager.setChosenCharacter(playerClass);
        this.currentSeed = seed;
        AbstractDungeon.ascensionLevel = ascension;
        Settings.seedSet = true;
        settings.checkIds();

        Settings.seed = seed;
        AbstractDungeon.generateSeeds();
        player = AbstractDungeon.player;
        AbstractDungeon.reset();
        resetCharacter();
        CombatRewards.clear();

        currentAct = 0;
        actFloor = 0;
        bootsCharges = 0;
        seedResult = new SeedResult(currentSeed);
        new Exordium(player, new ArrayList<>());
        phase = Phase.NEOW_PENDING;
    }

    /**
     * Runs until completion, a decision point (returns it), or error.
     * When policy provides a choice, applies it and continues. When policy returns null, returns the DecisionPoint.
     */
    public DecisionPoint run(RunPolicy policy) {
        if (pendingDecisionPoint != null) {
            throw new IllegalStateException("Paused: call applyChoice() before run()");
        }

        while (phase != Phase.DONE) {
            switch (phase) {
                case NEOW_PENDING:
                    return handleNeow(policy);
                case AFTER_NEOW:
                    advanceAfterNeow();
                    break;
                case NEED_PATH:
                    return handleNeedPath(policy);
                case IN_PATH:
                    advanceInPath(policy);
                    break;
                case BOSS_REWARDS:
                    advanceBossRewards(policy);
                    break;
                case BOSS_RELIC_PENDING:
                    return handleBossRelic(policy);
                case ACT_TRANSITION:
                    advanceActTransition();
                    break;
                default:
                    throw new IllegalStateException("Unexpected phase: " + phase);
            }
        }
        return null;
    }

    public void applyChoice(Object choice) {
        if (pendingDecisionPoint == null) {
            throw new IllegalStateException("Not paused: no decision to apply");
        }
        pendingChoice = choice;
        applyPendingChoice();
        pendingDecisionPoint = null;
        pendingChoice = null;
    }

    public DecisionPoint getCurrentDecision() {
        return pendingDecisionPoint;
    }

    public SeedResult getSeedResult() {
        return seedResult;
    }

    public ActMapSnapshot getActMap() {
        if (currentMap == null) return null;
        ActMapSnapshot snapshot = new ActMapSnapshot();
        for (int y = 0; y < currentMap.size(); y++) {
            ArrayList<MapRoomNode> row = currentMap.get(y);
            for (int x = 0; x < row.size(); x++) {
                MapRoomNode node = row.get(x);
                if (node != null && node.room != null) {
                    String roomType = getRoomTypeName(node.room);
                    String symbol = getRoomSymbol(node.room);
                    snapshot.nodes.add(new ActMapSnapshot.MapNode(y, x, roomType, symbol));
                }
            }
        }
        for (int y = 0; y < currentMap.size(); y++) {
            ArrayList<MapRoomNode> row = currentMap.get(y);
            for (int x = 0; x < row.size(); x++) {
                MapRoomNode node = row.get(x);
                if (node != null && node.getEdges() != null) {
                    for (MapEdge edge : node.getEdges()) {
                        snapshot.edges.add(new ActMapSnapshot.MapEdge(y, x, edge.dstY, edge.dstX));
                    }
                }
            }
        }
        return snapshot;
    }

    /**
     * Returns serializable run state for UI display.
     */
    public RunState getRunState() {
        ArrayList<String> relicIds = new ArrayList<>();
        for (AbstractRelic r : player.relics) {
            relicIds.add(r.relicId);
        }
        ArrayList<String> deckCardIds = new ArrayList<>();
        for (AbstractCard c : player.masterDeck.group) {
            deckCardIds.add(c.cardID);
        }
        ArrayList<String> potionIds = new ArrayList<>();
        for (AbstractPotion p : player.potions) {
            potionIds.add(p.ID);
        }
        String actName = currentAct == 0 ? "Exordium" : currentAct == 1 ? "The City" : currentAct == 2 ? "The Beyond" : "The Ending";
        return new RunState(currentSeed, AbstractDungeon.floorNum,
                player.gold, player.maxHealth, player.currentHealth, relicIds, deckCardIds, potionIds, currentAct, actName);
    }

    /**
     * Computes the best path for the given map. Used by ConfigPolicy when path is needed.
     */
    public ArrayList<MapRoomNode> findMapPath(ArrayList<ArrayList<MapRoomNode>> map) {
        if (bootsCharges > 0) {
            return findBootsPath(map);
        }
        float[][] weights = new float[15][7];
        float[][] pathWeights = new float[15][7];
        ArrayList<ArrayList<MapRoomNode>> parents = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 7; j++) {
                weights[i][j] = getRoomScore(map.get(i).get(j).room);
                if (i == 0) {
                    pathWeights[i][j] = weights[i][j];
                } else {
                    pathWeights[i][j] = 100000f;
                }
            }
        }
        for (int floor = 0; floor < 14; floor++) {
            ArrayList<MapRoomNode> floorParents = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                floorParents.add(null);
            }
            for (int x = 0; x < 7; x++) {
                MapRoomNode node = map.get(floor).get(x);
                if (node.room == null) continue;
                ArrayList<MapEdge> edges = node.getEdges();
                for (MapEdge edge : edges) {
                    int targetX = edge.dstX;
                    float testWeight = weights[floor + 1][targetX] + pathWeights[floor][x];
                    if (testWeight < pathWeights[floor + 1][targetX]) {
                        pathWeights[floor + 1][targetX] = testWeight;
                        floorParents.set(targetX, node);
                    }
                }
            }
            parents.add(floorParents);
        }
        int best_top = 0;
        float best_score = 100000f;
        for (int x = 0; x < 7; x++) {
            if (pathWeights[14][x] < best_score) {
                best_score = pathWeights[14][x];
                best_top = x;
            }
        }
        ArrayList<MapRoomNode> path = new ArrayList<>(15);
        int next_x = best_top;
        path.add(map.get(14).get(best_top));
        for (int y = 14; y > 0; y--) {
            MapRoomNode parent = parents.get(y - 1).get(next_x);
            path.add(0, parent);
            next_x = parent.x;
        }
        return path;
    }

    private ArrayList<MapRoomNode> findBootsPath(ArrayList<ArrayList<MapRoomNode>> map) {
        float[][] weights = new float[15][7];
        float[][][] pathWeights = new float[15][7][4];
        ArrayList<ArrayList<ArrayList<MapRoomNode>>> parents = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 7; j++) {
                weights[i][j] = getRoomScore(map.get(i).get(j).room);
                for (int k = 0; k < 4; k++) {
                    pathWeights[i][j][k] = i == 0 ? weights[i][j] : 100000f;
                }
            }
        }
        for (int floor = 0; floor < 14; floor++) {
            ArrayList<ArrayList<MapRoomNode>> floorParents = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                ArrayList<MapRoomNode> floorSubList = new ArrayList<>(7);
                for (int j = 0; j < 7; j++) floorSubList.add(null);
                floorParents.add(floorSubList);
            }
            for (int x = 0; x < 7; x++) {
                MapRoomNode node = map.get(floor).get(x);
                if (node.room == null) continue;
                ArrayList<MapEdge> edges = node.getEdges();
                ArrayList<Integer> nextXs = new ArrayList<>(0);
                for (MapEdge edge : edges) nextXs.add(edge.dstX);
                for (int nx = 0; nx < 7; nx++) {
                    for (int wing_uses = 0; wing_uses <= bootsCharges; wing_uses++) {
                        if (nextXs.contains(nx)) {
                            float testWeight = weights[floor + 1][nx] + pathWeights[floor][x][wing_uses];
                            if (testWeight < pathWeights[floor + 1][nx][wing_uses]) {
                                pathWeights[floor + 1][nx][wing_uses] = testWeight;
                                floorParents.get(wing_uses).set(nx, node);
                            }
                        } else if (wing_uses > 0) {
                            float testWeight = weights[floor + 1][nx] + pathWeights[floor][x][wing_uses - 1];
                            if (testWeight < pathWeights[floor + 1][nx][wing_uses]) {
                                pathWeights[floor + 1][nx][wing_uses] = testWeight;
                                floorParents.get(wing_uses).set(nx, node);
                            }
                        }
                    }
                }
            }
            parents.add(floorParents);
        }
        int[] best_top = {0, 0, 0, 0};
        float[] best_score = {100000f, 100000f, 100000f, 100000f};
        for (int uses = 0; uses < 4; uses++) {
            for (int x = 0; x < 7; x++) {
                if (pathWeights[14][x][uses] < best_score[uses]) {
                    best_score[uses] = pathWeights[14][x][uses];
                    best_top[uses] = x;
                }
            }
        }
        int best_uses = 0;
        if (best_score[0] - best_score[1] >= settings.wingBootsThreshold) best_uses = 1;
        if (best_score[1] - best_score[2] >= settings.wingBootsThreshold) best_uses = 2;
        if (best_score[2] - best_score[3] >= settings.wingBootsThreshold) best_uses = 3;
        int cur_uses = best_uses;
        ArrayList<MapRoomNode> path = new ArrayList<>(15);
        int next_x = best_top[cur_uses];
        path.add(map.get(14).get(best_top[cur_uses]));
        for (int y = 14; y > 0; y--) {
            MapRoomNode parent = parents.get(y - 1).get(cur_uses).get(next_x);
            boolean isConnected = false;
            for (MapEdge edge : parent.getEdges()) {
                if (edge.dstX == next_x) { isConnected = true; break; }
            }
            if (!isConnected) cur_uses -= 1;
            path.add(0, parent);
            next_x = parent.x;
        }
        if (best_uses != 0) bootsCharges -= best_uses;
        return path;
    }

    private float getRoomScore(AbstractRoom room) {
        if (room instanceof TreasureRoom) return 0f;
        if (room instanceof MonsterRoomElite) return settings.eliteRoomWeight;
        if (room instanceof MonsterRoom) return settings.monsterRoomWeight;
        if (room instanceof RestRoom) return settings.restRoomWeight;
        if (room instanceof ShopRoom) return settings.shopRoomWeight;
        if (room instanceof EventRoom) return settings.eventRoomWeight;
        return 0f;
    }

    private String getRoomTypeName(AbstractRoom room) {
        if (room instanceof EventRoom) return "event";
        if (room instanceof MonsterRoomElite) return "elite";
        if (room instanceof MonsterRoom) return "monster";
        if (room instanceof ShopRoom) return "shop";
        if (room instanceof TreasureRoom) return "treasure";
        if (room instanceof RestRoom) return "rest";
        return "unknown";
    }

    private String getRoomSymbol(AbstractRoom room) {
        if (room instanceof EventRoom) return "?";
        if (room instanceof MonsterRoomElite) return "E";
        if (room instanceof MonsterRoom) return "M";
        if (room instanceof ShopRoom) return "S";
        if (room instanceof TreasureRoom) return "T";
        if (room instanceof RestRoom) return "R";
        return "?";
    }

    private void resetCharacter() {
        player.relics = new ArrayList<>();
        try {
            Method m = AbstractPlayer.class.getDeclaredMethod("initializeStarterRelics", AbstractPlayer.PlayerClass.class);
            m.setAccessible(true);
            m.invoke(player, settings.playerClass);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Reflection error when initializing player relics", e);
        }
        player.potions = new ArrayList<>();
        player.masterDeck = new CardGroup(CardGroup.CardGroupType.MASTER_DECK);
        CharSelectInfo info = player.getLoadout();
        player.maxHealth = info.maxHp;
        player.gold = info.gold;
        CardCrawlGame.music.dispose();
        CardCrawlGame.music.update();
    }

    private DecisionPoint handleNeow(RunPolicy policy) {
        if (neowRewards == null) {
            neowRewards = getNeowRewards();
            seedResult.addNeowRewards(neowRewards);
        }
        DecisionPoint.NeowChoice dp = new DecisionPoint.NeowChoice();
        for (NeowReward r : neowRewards) {
            dp.options.add(SeedResult.removeTextFormatting(r.optionLabel));
        }
        Object choice = policy.choose(dp);
        if (choice != null) {
            applyNeowChoice((Integer) choice);
            phase = Phase.AFTER_NEOW;
            return run(policy);
        }
        pendingDecisionPoint = dp;
        return dp;
    }

    private void applyPendingChoice() {
        if (pendingDecisionPoint instanceof DecisionPoint.NeowChoice) {
            applyNeowChoice((Integer) pendingChoice);
            phase = Phase.AFTER_NEOW;
        } else if (pendingDecisionPoint instanceof DecisionPoint.BossRelicChoice) {
            applyBossRelicChoice((String) pendingChoice);
            phase = Phase.ACT_TRANSITION;
        } else if (pendingDecisionPoint instanceof DecisionPoint.PathChoice) {
            applyPathChoice(String.valueOf(pendingChoice));
            phase = Phase.IN_PATH;
        }
    }

    private void applyPathChoice(String nodeId) {
        String[] parts = nodeId.split(",");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid node ID: " + nodeId);
        int startY = Integer.parseInt(parts[0].trim());
        int startX = Integer.parseInt(parts[1].trim());
        ArrayList<MapRoomNode> path = findPathFromNode(AbstractDungeon.map, startY, startX);
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("No path from node: " + nodeId);
        currentPath = path;
        currentMap = AbstractDungeon.map;
    }

    private ArrayList<MapRoomNode> findPathFromNode(ArrayList<ArrayList<MapRoomNode>> map, int startY, int startX) {
        if (bootsCharges > 0) {
            return findBootsPathFromNode(map, startY, startX);
        }
        float[][] weights = new float[15][7];
        float[][] pathWeights = new float[15][7];
        ArrayList<ArrayList<MapRoomNode>> parents = new ArrayList<>();
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 7; x++) {
                weights[y][x] = getRoomScore(map.get(y).get(x).room);
                pathWeights[y][x] = (y == startY && x == startX) ? weights[y][x] : 100000f;
            }
        }
        for (int floor = 0; floor < 14; floor++) {
            ArrayList<MapRoomNode> floorParents = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) floorParents.add(null);
            for (int x = 0; x < 7; x++) {
                MapRoomNode node = map.get(floor).get(x);
                if (node == null || node.room == null) continue;
                for (MapEdge edge : node.getEdges()) {
                    int targetX = edge.dstX;
                    float testWeight = weights[floor + 1][targetX] + pathWeights[floor][x];
                    if (testWeight < pathWeights[floor + 1][targetX]) {
                        pathWeights[floor + 1][targetX] = testWeight;
                        floorParents.set(targetX, node);
                    }
                }
            }
            parents.add(floorParents);
        }
        int bestTop = 0;
        float bestScore = 100000f;
        for (int x = 0; x < 7; x++) {
            if (pathWeights[14][x] < bestScore) {
                bestScore = pathWeights[14][x];
                bestTop = x;
            }
        }
        if (bestScore >= 100000f) return null;
        ArrayList<MapRoomNode> path = new ArrayList<>(15);
        int nextX = bestTop;
        path.add(map.get(14).get(bestTop));
        for (int y = 14; y > 0; y--) {
            MapRoomNode parent = parents.get(y - 1).get(nextX);
            if (parent == null) return null;
            path.add(0, parent);
            nextX = parent.x;
        }
        return path;
    }

    private ArrayList<MapRoomNode> findBootsPathFromNode(ArrayList<ArrayList<MapRoomNode>> map, int startY, int startX) {
        float[][] weights = new float[15][7];
        float[][][] pathWeights = new float[15][7][4];
        ArrayList<ArrayList<ArrayList<MapRoomNode>>> parents = new ArrayList<>();
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 7; x++) {
                weights[y][x] = getRoomScore(map.get(y).get(x).room);
                for (int k = 0; k < 4; k++) {
                    pathWeights[y][x][k] = (y == startY && x == startX) ? weights[y][x] : 100000f;
                }
            }
        }
        for (int floor = 0; floor < 14; floor++) {
            ArrayList<ArrayList<MapRoomNode>> floorParents = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                ArrayList<MapRoomNode> subList = new ArrayList<>(7);
                for (int j = 0; j < 7; j++) subList.add(null);
                floorParents.add(subList);
            }
            for (int x = 0; x < 7; x++) {
                MapRoomNode node = map.get(floor).get(x);
                if (node == null || node.room == null) continue;
                ArrayList<Integer> nextXs = new ArrayList<>();
                for (MapEdge edge : node.getEdges()) nextXs.add(edge.dstX);
                for (int nx = 0; nx < 7; nx++) {
                    for (int wingUses = 0; wingUses <= bootsCharges; wingUses++) {
                        if (nextXs.contains(nx)) {
                            float testWeight = weights[floor + 1][nx] + pathWeights[floor][x][wingUses];
                            if (testWeight < pathWeights[floor + 1][nx][wingUses]) {
                                pathWeights[floor + 1][nx][wingUses] = testWeight;
                                floorParents.get(wingUses).set(nx, node);
                            }
                        } else if (wingUses > 0) {
                            float testWeight = weights[floor + 1][nx] + pathWeights[floor][x][wingUses - 1];
                            if (testWeight < pathWeights[floor + 1][nx][wingUses]) {
                                pathWeights[floor + 1][nx][wingUses] = testWeight;
                                floorParents.get(wingUses).set(nx, node);
                            }
                        }
                    }
                }
            }
            parents.add(floorParents);
        }
        int[] bestTop = new int[4];
        float[] bestScore = new float[]{100000f, 100000f, 100000f, 100000f};
        for (int uses = 0; uses <= bootsCharges; uses++) {
            for (int x = 0; x < 7; x++) {
                if (pathWeights[14][x][uses] < bestScore[uses]) {
                    bestScore[uses] = pathWeights[14][x][uses];
                    bestTop[uses] = x;
                }
            }
        }
        int bestUses = 0;
        if (bestScore[0] >= 100000f) return null;
        if (bootsCharges >= 1 && bestScore[1] < 100000f && bestScore[0] - bestScore[1] >= settings.wingBootsThreshold) bestUses = 1;
        if (bootsCharges >= 2 && bestScore[2] < 100000f && bestScore[1] - bestScore[2] >= settings.wingBootsThreshold) bestUses = 2;
        if (bootsCharges >= 3 && bestScore[3] < 100000f && bestScore[2] - bestScore[3] >= settings.wingBootsThreshold) bestUses = 3;
        int curUses = bestUses;
        int nextX = bestTop[curUses];
        ArrayList<MapRoomNode> path = new ArrayList<>(15);
        path.add(map.get(14).get(bestTop[curUses]));
        for (int y = 14; y > 0; y--) {
            MapRoomNode parent = parents.get(y - 1).get(curUses).get(nextX);
            if (parent == null) return null;
            boolean isConnected = false;
            for (MapEdge edge : parent.getEdges()) {
                if (edge.dstX == nextX) { isConnected = true; break; }
            }
            if (!isConnected) curUses -= 1;
            path.add(0, parent);
            nextX = parent.x;
        }
        if (bestUses != 0) bootsCharges -= bestUses;
        return path;
    }

    private void applyNeowChoice(int choiceIndex) {
        NeowReward neowReward = settings.forceNeowLament ? new NeowReward(true) : neowRewards.get(choiceIndex);
        claimNeowReward(neowReward);
        String startingRelicName = player.relics.isEmpty() ? "" : RelicLibrary.getRelic(player.relics.get(0).relicId).name;
        FloorInfo neowFloorInfo = new FloorInfo(0, null, "Neow");
        neowFloorInfo.name = startingRelicName;
        int chosenIndex = settings.forceNeowLament ? 0 : choiceIndex;
        for (int i = 0; i < neowRewards.size(); i++) {
            neowFloorInfo.neowOptions.add(SeedResult.removeTextFormatting(neowRewards.get(i).optionLabel));
        }
        neowFloorInfo.neowChosenIndex = chosenIndex;
        Reward neowRewardResult = seedResult.getLastMiscReward();
        if (neowRewardResult != null) {
            for (AbstractCard card : neowRewardResult.cards) neowFloorInfo.neowChoice.add(card.name);
            for (String relicId : neowRewardResult.relics) {
                try { neowFloorInfo.neowChoice.add(RelicLibrary.getRelic(relicId).name); }
                catch (Exception e) { neowFloorInfo.neowChoice.add(relicId); }
            }
            for (AbstractPotion potion : neowRewardResult.potions) neowFloorInfo.neowChoice.add(potion.name);
        }
        seedResult.addFloorInfo(neowFloorInfo);
    }

    private ArrayList<NeowReward> getNeowRewards() {
        NeowEvent.rng = new Random(Settings.seed);
        ArrayList<NeowReward> rewards = new ArrayList<>();
        rewards.add(new NeowReward(0));
        rewards.add(new NeowReward(1));
        rewards.add(new NeowReward(2));
        rewards.add(new NeowReward(3));
        return rewards;
    }

    private void claimNeowReward(NeowReward neowOption) {
        Reward reward = new Reward(0);
        AbstractDungeon.getCurrMapNode().room = new EmptyRoom();
        StandaloneHooks.obtainedRelic = null;
        StandaloneHooks.rewardCards = null;
        StandaloneHooks.resetObtainedCards();
        neowOption.activate();
        if (StandaloneHooks.obtainedRelic != null) awardRelic(StandaloneHooks.obtainedRelic, reward);
        if (StandaloneHooks.rewardCards != null) seedResult.addCardReward(0, StandaloneHooks.rewardCards);
        if (StandaloneHooks.obtainedCards.size() > 0) {
            for (AbstractCard card : StandaloneHooks.obtainedCards) addInvoluntaryCardReward(card, reward);
        }
        if (CombatRewards.combatPotions.size() > 0) reward.addPotions(CombatRewards.combatPotions);
        if (neowOption.type == NeowReward.NeowRewardType.TRANSFORM_CARD) {
            AbstractCard removedCard = player.masterDeck.group.get(1);
            AbstractDungeon.transformCard(removedCard, false, NeowEvent.rng);
            player.masterDeck.removeCard(removedCard);
            addInvoluntaryCardReward(AbstractDungeon.getTransformedCard(), reward);
        }
        if (neowOption.type == NeowReward.NeowRewardType.TRANSFORM_TWO_CARDS) {
            AbstractCard removedCard = player.masterDeck.group.get(1);
            AbstractDungeon.transformCard(removedCard, false, NeowEvent.rng);
            player.masterDeck.removeCard(removedCard);
            addInvoluntaryCardReward(AbstractDungeon.getTransformedCard(), reward);
            removedCard = player.masterDeck.group.get(1);
            AbstractDungeon.transformCard(removedCard, false, NeowEvent.rng);
            player.masterDeck.removeCard(removedCard);
            addInvoluntaryCardReward(AbstractDungeon.getTransformedCard(), reward);
        }
        if (neowOption.drawback == NeowReward.NeowRewardDrawback.CURSE) {
            addInvoluntaryCardReward(AbstractDungeon.getCardWithoutRng(AbstractCard.CardRarity.CURSE), reward);
        }
        seedResult.addMiscReward(reward);
    }

    private void advanceAfterNeow() {
        if (!settings.speedrunPace) CardCrawlGame.playtime = 900F;
        else CardCrawlGame.playtime = 0F;
        if (settings.showRawRelicPools) {
            seedResult.SetCommonRelicPool(AbstractDungeon.commonRelicPool);
            seedResult.SetUncommonRelicPool(AbstractDungeon.uncommonRelicPool);
            seedResult.SetRareRelicPool(AbstractDungeon.rareRelicPool);
            seedResult.SetBossRelicPool(AbstractDungeon.bossRelicPool);
            seedResult.SetShopRelicPool(AbstractDungeon.shopRelicPool);
        }
        phase = Phase.NEED_PATH;
    }

    private DecisionPoint handleNeedPath(RunPolicy policy) {
        List<MapRoomNode> path;
        if (currentAct == 3 && settings.act4) {
            path = new ArrayList<>();
            path.add(AbstractDungeon.map.get(0).get(3));
            path.add(AbstractDungeon.map.get(1).get(3));
            path.add(AbstractDungeon.map.get(2).get(3));
        } else {
            path = policy.getPathForCurrentAct(this);
        }
        if (path != null) {
            currentPath = new ArrayList<>(path);
            currentMap = AbstractDungeon.map;
            phase = Phase.IN_PATH;
            return run(policy);
        }
        currentMap = AbstractDungeon.map;
        DecisionPoint.PathChoice pathChoice = new DecisionPoint.PathChoice(AbstractDungeon.floorNum);
        ArrayList<MapRoomNode> row0 = AbstractDungeon.map.get(0);
        for (int x = 0; x < row0.size(); x++) {
            MapRoomNode node = row0.get(x);
            if (node != null && node.room != null) {
                pathChoice.nextNodeIds.add(node.y + "," + node.x);
            }
        }
        pendingDecisionPoint = pathChoice;
        return pathChoice;
    }

    private void advanceInPath(RunPolicy policy) {
        runPath(currentPath, policy);
        phase = Phase.BOSS_REWARDS;
    }

    private void advanceBossRewards(RunPolicy policy) {
        getBossRewards(policy);
    }

    private DecisionPoint handleBossRelic(RunPolicy policy) {
        DecisionPoint.BossRelicChoice dp = new DecisionPoint.BossRelicChoice(AbstractDungeon.floorNum);
        dp.relicIds.addAll(bossRelicStrings);
        Object choice = policy.choose(dp);
        if (choice != null) {
            applyBossRelicChoice((String) choice);
            phase = Phase.ACT_TRANSITION;
            return run(policy);
        }
        pendingDecisionPoint = dp;
        return dp;
    }

    private void applyBossRelicChoice(String relicId) {
        Reward bossRelicReward = new Reward(AbstractDungeon.floorNum);
        if (relicId != null && !relicId.isEmpty() && bossRelicStrings.contains(relicId)) {
            doRelicPickupLogic(RelicLibrary.getRelic(relicId), bossRelicReward);
        }
        seedResult.addMiscReward(bossRelicReward);
    }

    private void advanceActTransition() {
        seedResult.updateRelics();
        if (currentAct == 0 && !seedResult.testAct1Filters(settings)) {
            phase = Phase.DONE;
            return;
        }
        currentAct += 1;
        if (currentAct == 3 && !settings.act4) {
            seedResult.updateRelics();
            phase = Phase.DONE;
            return;
        }
        initNextAct();
        phase = Phase.NEED_PATH;
    }

    private void initNextAct() {
        if (currentAct == 1) {
            new TheCity(player, AbstractDungeon.specialOneTimeEventList);
        } else if (currentAct == 2) {
            new TheBeyond(player, AbstractDungeon.specialOneTimeEventList);
        } else if (currentAct == 3 && settings.act4) {
            new TheEnding(player, AbstractDungeon.specialOneTimeEventList);
            AbstractDungeon.floorNum += 1;
        }
    }

    private void runPath(ArrayList<MapRoomNode> path, RunPolicy policy) {
        for (actFloor = 0; actFloor < path.size(); actFloor++) {
            AbstractDungeon.floorNum += 1;
            if (AbstractDungeon.floorNum > settings.highestFloor) break;
            AbstractDungeon.miscRng = new Random(currentSeed + (long) AbstractDungeon.floorNum);
            MapRoomNode node = path.get(actFloor);
            RoomType result = getRoomType(node);
            if (result == RoomType.EVENT) {
                EventHelper.RoomResult eventRoll = EventHelper.roll();
                switch (eventRoll) {
                    case ELITE: result = RoomType.ELITE; node.room = new MonsterRoomElite(); break;
                    case MONSTER: result = RoomType.MONSTER; node.room = new MonsterRoom(); break;
                    case SHOP: result = RoomType.SHOP; node.room = new ShopRoom(); break;
                    case TREASURE: result = RoomType.TREASURE; node.room = new TreasureRoom(); break;
                    default: break;
                }
            }
            AbstractDungeon.currMapNode = node;
            if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
                AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
            }
            CombatRewards.clear();
            processFloor(node, result, path);
        }
        seedResult.addToMapPath("BOSS");
        seedResult.addToTrueMapPath("BOSS");
    }

    private enum RoomType { EVENT, ELITE, MONSTER, SHOP, TREASURE, REST }

    private RoomType getRoomType(MapRoomNode node) {
        if (node.room instanceof EventRoom) { seedResult.addToMapPath("?"); return RoomType.EVENT; }
        if (node.room instanceof MonsterRoomElite) { seedResult.addToMapPath("E"); return RoomType.ELITE; }
        if (node.room instanceof MonsterRoom) { seedResult.addToMapPath("M"); return RoomType.MONSTER; }
        if (node.room instanceof ShopRoom) { seedResult.addToMapPath("S"); return RoomType.SHOP; }
        if (node.room instanceof TreasureRoom) { seedResult.addToMapPath("T"); return RoomType.TREASURE; }
        seedResult.addToMapPath("R"); return RoomType.REST;
    }

    private void processFloor(MapRoomNode node, RoomType result, ArrayList<MapRoomNode> path) {
        switch (result) {
            case EVENT:
                seedResult.addToTrueMapPath("?");
                Random eventRngDuplicate = new Random(Settings.seed, AbstractDungeon.eventRng.counter);
                AbstractEvent event = AbstractDungeon.generateEvent(eventRngDuplicate);
                String eventKey = EventHelper.getMostRecentEventID();
                Reward eventReward = getEventReward(event, eventKey, AbstractDungeon.floorNum);
                seedResult.registerEvent(eventKey);
                if (!eventReward.isEmpty()) seedResult.addMiscReward(eventReward);
                addEventFloorInfo(path, node, eventKey, eventReward);
                break;
            case MONSTER:
                seedResult.addToTrueMapPath("M");
                String monster = AbstractDungeon.monsterList.remove(0);
                seedResult.registerCombat(monster);
                ArrayList<AbstractCard> monsterCards = AbstractDungeon.getRewardCards();
                seedResult.addCardReward(AbstractDungeon.floorNum, monsterCards);
                if (player.hasRelic(PrayerWheel.ID)) {
                    ArrayList<AbstractCard> prayerCards = AbstractDungeon.getRewardCards();
                    seedResult.addCardReward(AbstractDungeon.floorNum, prayerCards);
                    monsterCards.addAll(prayerCards);
                }
                addGoldReward(AbstractDungeon.treasureRng.random(10, 20));
                AbstractPotion monsterPotion = getPotionReward();
                if (monsterPotion != null) {
                    Reward r = new Reward(AbstractDungeon.floorNum);
                    r.addPotion(monsterPotion);
                    seedResult.addMiscReward(r);
                }
                addMonsterFloorInfo(path, node, monster, monsterCards, monsterPotion);
                break;
            case ELITE:
                seedResult.addToTrueMapPath("E");
                String elite = AbstractDungeon.eliteMonsterList.remove(0);
                seedResult.registerEliteCombat(elite);
                ArrayList<AbstractCard> eliteCards = AbstractDungeon.getRewardCards();
                seedResult.addCardReward(AbstractDungeon.floorNum, eliteCards);
                AbstractRelic.RelicTier tier = AbstractDungeon.returnRandomRelicTier();
                String relic = AbstractDungeon.returnRandomRelicKey(tier);
                Reward relicReward = new Reward(AbstractDungeon.floorNum);
                awardRelic(relic, relicReward);
                if (player.hasRelic(BlackStar.ID)) {
                    awardRelic(AbstractDungeon.returnRandomNonCampfireRelic(tier), relicReward);
                }
                seedResult.addMiscReward(relicReward);
                addGoldReward(AbstractDungeon.treasureRng.random(25, 35));
                AbstractPotion elitePotion = getPotionReward();
                if (elitePotion != null) {
                    Reward r = new Reward(AbstractDungeon.floorNum);
                    r.addPotion(elitePotion);
                    seedResult.addMiscReward(r);
                }
                addEliteFloorInfo(path, node, elite, eliteCards, relicReward, elitePotion);
                break;
            case SHOP:
                seedResult.addToTrueMapPath("S");
                new Merchant();
                Reward shopReward = getShopReward(AbstractDungeon.floorNum);
                seedResult.addShopReward(shopReward);
                addShopFloorInfo(path, node, shopReward);
                break;
            case TREASURE:
                seedResult.addToTrueMapPath("T");
                AbstractChest chest = AbstractDungeon.getRandomChest();
                ArrayList<AbstractCard> treasureBottleDummies = addBottleDummyCardsForChest();
                StandaloneHooks.resetObtainedCards();
                chest.open(false);
                for (AbstractCard dummy : treasureBottleDummies) player.masterDeck.removeCard(dummy);
                addGoldReward(CombatRewards.combatGold);
                Reward treasureRelicReward = new Reward(AbstractDungeon.floorNum);
                for (RewardItem rewardItem : AbstractDungeon.getCurrRoom().rewards) {
                    if (rewardItem.type == RewardItem.RewardType.RELIC && rewardItem.relic != null) {
                        awardRelic(rewardItem.relic, treasureRelicReward);
                    }
                }
                for (AbstractCard card : StandaloneHooks.obtainedCards) {
                    addInvoluntaryCardReward(card, treasureRelicReward);
                }
                seedResult.addAllCardRewards(CombatRewards.combatCardRewards);
                seedResult.addMiscReward(treasureRelicReward);
                addTreasureFloorInfo(path, node, treasureRelicReward);
                break;
            case REST:
                seedResult.addToTrueMapPath("R");
                seedResult.countRestSite();
                FloorInfo restFloorInfo = new FloorInfo(AbstractDungeon.floorNum, getPathIndex(path, actFloor, node), "Rest");
                if (settings.useShovel && player.hasRelic(Shovel.ID)) {
                    Reward digReward = new Reward(AbstractDungeon.floorNum);
                    ArrayList<AbstractCard> shovelBottleDummies = addBottleDummyCardsForChest();
                    AbstractRelic.RelicTier digTier = AbstractDungeon.returnRandomRelicTier();
                    AbstractRelic digRelic = AbstractDungeon.returnRandomRelic(digTier);
                    for (AbstractCard dummy : shovelBottleDummies) player.masterDeck.removeCard(dummy);
                    awardRelic(digRelic, digReward);
                    restFloorInfo.relics.add(digRelic.relicId);
                }
                seedResult.addFloorInfo(restFloorInfo);
                break;
        }
    }

    private int getPathIndex(ArrayList<MapRoomNode> path, int actFloor, MapRoomNode currentNode) {
        if (actFloor == 0) {
            ArrayList<Integer> startXPositions = new ArrayList<>();
            int rowIndex = currentNode.y;
            ArrayList<MapRoomNode> row = AbstractDungeon.map.get(rowIndex);
            for (int x = 0; x < row.size(); x++) {
                MapRoomNode nodeAtX = row.get(x);
                if (nodeAtX != null && nodeAtX.room != null && nodeAtX.hasEdges()) startXPositions.add(x);
            }
            Collections.sort(startXPositions);
            for (int index = 0; index < startXPositions.size(); index++) {
                if (startXPositions.get(index) == currentNode.x) {
                    return startXPositions.size() <= 1 ? 0 : index;
                }
            }
            return 0;
        }
        MapRoomNode parent = path.get(actFloor - 1);
        ArrayList<MapEdge> edgesToNextRow = new ArrayList<>();
        for (MapEdge edge : parent.getEdges()) {
            if (edge.dstY == currentNode.y) {
                MapRoomNode dstNode = AbstractDungeon.map.get(edge.dstY).get(edge.dstX);
                if (dstNode != null && dstNode.hasEdges()) edgesToNextRow.add(edge);
            }
        }
        if (edgesToNextRow.size() <= 1) return 0;
        edgesToNextRow.sort(Comparator.comparingInt(edge -> edge.dstX));
        for (int index = 0; index < edgesToNextRow.size(); index++) {
            if (edgesToNextRow.get(index).dstX == currentNode.x) return index;
        }
        return 0;
    }

    private void addEventFloorInfo(ArrayList<MapRoomNode> path, MapRoomNode node, String eventKey, Reward eventReward) {
        FloorInfo fi = new FloorInfo(AbstractDungeon.floorNum, getPathIndex(path, actFloor, node), "Event");
        fi.name = eventKey != null ? eventKey : "";
        for (AbstractCard card : eventReward.cards) fi.cardRewards.add(card.name);
        for (String relicId : eventReward.relics) fi.relics.add(relicId);
        for (AbstractPotion potion : eventReward.potions) fi.potions.add(potion.name);
        seedResult.addFloorInfo(fi);
    }

    private void addMonsterFloorInfo(ArrayList<MapRoomNode> path, MapRoomNode node, String monster,
                                    ArrayList<AbstractCard> monsterCards, AbstractPotion monsterPotion) {
        FloorInfo fi = new FloorInfo(AbstractDungeon.floorNum, getPathIndex(path, actFloor, node), "Monster");
        fi.name = monster;
        for (AbstractCard card : monsterCards) fi.cardRewards.add(card.name);
        if (monsterPotion != null) fi.potions.add(monsterPotion.name);
        seedResult.addFloorInfo(fi);
    }

    private void addEliteFloorInfo(ArrayList<MapRoomNode> path, MapRoomNode node, String elite,
                                   ArrayList<AbstractCard> eliteCards, Reward relicReward, AbstractPotion elitePotion) {
        FloorInfo fi = new FloorInfo(AbstractDungeon.floorNum, getPathIndex(path, actFloor, node), "Elite");
        fi.name = elite;
        for (AbstractCard card : eliteCards) fi.cardRewards.add(card.name);
        for (String relicId : relicReward.relics) fi.relics.add(relicId);
        if (elitePotion != null) fi.potions.add(elitePotion.name);
        seedResult.addFloorInfo(fi);
    }

    private void addShopFloorInfo(ArrayList<MapRoomNode> path, MapRoomNode node, Reward shopReward) {
        FloorInfo fi = new FloorInfo(AbstractDungeon.floorNum, getPathIndex(path, actFloor, node), "Shop");
        for (AbstractCard card : shopReward.cards) fi.cardRewards.add(card.name);
        for (String relicId : shopReward.relics) fi.relics.add(relicId);
        for (AbstractPotion potion : shopReward.potions) fi.potions.add(potion.name);
        seedResult.addFloorInfo(fi);
    }

    private void addTreasureFloorInfo(ArrayList<MapRoomNode> path, MapRoomNode node, Reward treasureRelicReward) {
        FloorInfo fi = new FloorInfo(AbstractDungeon.floorNum, getPathIndex(path, actFloor, node), "Treasure");
        for (String relicId : treasureRelicReward.relics) fi.relics.add(relicId);
        for (AbstractCard card : treasureRelicReward.cards) fi.cardRewards.add(card.name);
        seedResult.addFloorInfo(fi);
    }

    private void getBossRewards(RunPolicy policy) {
        AbstractDungeon.floorNum += 1;
        if (AbstractDungeon.floorNum > settings.highestFloor) {
            phase = Phase.DONE;
            return;
        }
        int bossFloorNum = AbstractDungeon.floorNum;
        seedResult.registerBossCombat(AbstractDungeon.bossKey);
        AbstractDungeon.currMapNode = new MapRoomNode(-1, 15);
        AbstractDungeon.currMapNode.room = new MonsterRoomBoss();
        AbstractDungeon.currMapNode.room.phase = AbstractRoom.RoomPhase.COMPLETE;
        if (AbstractDungeon.ascensionLevel == 20 && currentAct == 2) {
            seedResult.registerBossCombat(AbstractDungeon.bossList.get(1));
            AbstractDungeon.floorNum += 1;
            if (AbstractDungeon.floorNum > settings.highestFloor) {
                phase = Phase.DONE;
                return;
            }
        }
        if (currentAct < 2) {
            AbstractDungeon.miscRng = new Random(currentSeed + (long) AbstractDungeon.floorNum);
            Reward cardReward = new Reward(AbstractDungeon.floorNum);
            ArrayList<AbstractCard> bossCards = AbstractDungeon.getRewardCards();
            cardReward.addCards(bossCards);
            int gold = 100 + AbstractDungeon.miscRng.random(-5, 5);
            if (AbstractDungeon.ascensionLevel >= 13) gold = (int) (gold * 0.75);
            addGoldReward(gold);
            AbstractPotion potion = getPotionReward();
            if (potion != null) {
                Reward r = new Reward(AbstractDungeon.floorNum);
                r.addPotion(potion);
                seedResult.addMiscReward(r);
            }
            AbstractDungeon.currMapNode.room = new TreasureRoomBoss();
            AbstractDungeon.floorNum += 1;
            if (AbstractDungeon.floorNum > settings.highestFloor) {
                phase = Phase.DONE;
                return;
            }
            AbstractDungeon.miscRng = new Random(currentSeed + (long) AbstractDungeon.floorNum);
            BossChest bossChest = new BossChest();
            bossRelicStrings = new ArrayList<>();
            for (AbstractRelic relic : bossChest.relics) bossRelicStrings.add(relic.relicId);
            seedResult.addBossReward(bossRelicStrings);
            seedResult.addCardReward(cardReward);
            FloorInfo bossFloorInfo = new FloorInfo(bossFloorNum, null, "Boss");
            bossFloorInfo.name = AbstractDungeon.bossKey;
            for (AbstractCard card : bossCards) bossFloorInfo.cardRewards.add(card.name);
            if (potion != null) bossFloorInfo.potions.add(potion.name);
            seedResult.addFloorInfo(bossFloorInfo);
            FloorInfo bossRelicFloorInfo = new FloorInfo(AbstractDungeon.floorNum, null, "BossRelic");
            bossRelicFloorInfo.name = AbstractDungeon.bossKey;
            bossRelicFloorInfo.bossRelics.addAll(bossRelicStrings);
            seedResult.addFloorInfo(bossRelicFloorInfo);
            phase = Phase.BOSS_RELIC_PENDING;
        } else {
            FloorInfo act3BossFloorInfo = new FloorInfo(bossFloorNum, null, "Boss");
            act3BossFloorInfo.name = AbstractDungeon.bossKey;
            seedResult.addFloorInfo(act3BossFloorInfo);
            if (AbstractDungeon.ascensionLevel == 20 && AbstractDungeon.bossList.size() >= 2) {
                FloorInfo act3SecondBossFloorInfo = new FloorInfo(AbstractDungeon.floorNum, null, "Boss");
                act3SecondBossFloorInfo.name = AbstractDungeon.bossList.get(1);
                seedResult.addFloorInfo(act3SecondBossFloorInfo);
            }
            phase = Phase.ACT_TRANSITION;
        }
    }

    private void awardRelic(String relic, Reward reward) {
        reward.addRelic(relic);
        doRelicPickupLogic(RelicLibrary.getRelic(relic), reward);
    }

    private void awardRelic(AbstractRelic relic, Reward reward) {
        reward.addRelic(relic.relicId);
        doRelicPickupLogic(relic, reward);
    }

    private void doRelicPickupLogic(AbstractRelic relic, Reward reward) {
        player.relics.add(relic);
        relic.onEquip();
        String relicKey = relic.relicId;
        switch (relicKey) {
            case TinyHouse.ID:
                seedResult.addCardReward(reward.floor, AbstractDungeon.getRewardCards());
                Reward tinyHouseReward = new Reward(AbstractDungeon.floorNum);
                AbstractDungeon.miscRng.random();
                AbstractPotion tinyHousePotion = PotionHelper.getRandomPotion(AbstractDungeon.miscRng);
                tinyHouseReward.addPotion(tinyHousePotion);
                seedResult.addMiscReward(tinyHouseReward);
                addGoldReward(50);
                break;
            case WingBoots.ID:
                bootsCharges = 3;
                break;
            case CallingBell.ID:
                addInvoluntaryCardReward(new CurseOfTheBell(), reward);
                awardRelic(AbstractDungeon.returnRandomRelicKey(AbstractRelic.RelicTier.COMMON), reward);
                awardRelic(AbstractDungeon.returnRandomRelicKey(AbstractRelic.RelicTier.UNCOMMON), reward);
                awardRelic(AbstractDungeon.returnRandomRelicKey(AbstractRelic.RelicTier.RARE), reward);
                break;
            case PandorasBox.ID:
                int count = 0;
                ArrayList<AbstractCard> strikesAndDefends = new ArrayList<>();
                for (AbstractCard card : player.masterDeck.group) {
                    if (isStrikeOrDefend(card)) {
                        count++;
                        strikesAndDefends.add(card);
                    }
                }
                for (AbstractCard card : strikesAndDefends) player.masterDeck.group.remove(card);
                for (int i = 0; i < count; i++) {
                    AbstractCard newCard = AbstractDungeon.returnTrulyRandomCard().makeCopy();
                    if (!settings.ignorePandoraCards) addInvoluntaryCardReward(newCard, reward);
                }
                break;
            case Astrolabe.ID:
                for (int i = 0; i < 3; i++) {
                    AbstractCard removedCard = player.masterDeck.group.get(1);
                    AbstractDungeon.transformCard(removedCard, true, AbstractDungeon.miscRng);
                    player.masterDeck.removeCard(removedCard);
                    addInvoluntaryCardReward(AbstractDungeon.getTransformedCard(), reward);
                }
                break;
            case Necronomicon.ID:
                addInvoluntaryCardReward(new Necronomicurse(), reward);
                break;
            default:
                break;
        }
    }

    private boolean isStrikeOrDefend(AbstractCard card) {
        String id = card.cardID;
        return id.equals("Strike_R") || id.equals("Strike_G") || id.equals("Strike_B") || id.equals("Strike_P")
                || id.equals("Defend_R") || id.equals("Defend_G") || id.equals("Defend_B") || id.equals("Defend_P");
    }

    @SuppressWarnings("unchecked")
    private Reward getShopReward(int floor) {
        Reward shopReward = new Reward(floor);
        ShopScreen screen = AbstractDungeon.shopScreen;
        try {
            Field coloredCardsField = ShopScreen.class.getDeclaredField("coloredCards");
            Field colorlessCardsField = ShopScreen.class.getDeclaredField("colorlessCards");
            Field relicsField = ShopScreen.class.getDeclaredField("relics");
            Field potionsField = ShopScreen.class.getDeclaredField("potions");
            coloredCardsField.setAccessible(true);
            colorlessCardsField.setAccessible(true);
            relicsField.setAccessible(true);
            potionsField.setAccessible(true);
            ArrayList<AbstractCard> coloredCards = (ArrayList<AbstractCard>) coloredCardsField.get(screen);
            ArrayList<AbstractCard> colorlessCards = (ArrayList<AbstractCard>) colorlessCardsField.get(screen);
            ArrayList<StoreRelic> relics = (ArrayList<StoreRelic>) relicsField.get(screen);
            ArrayList<StorePotion> potions = (ArrayList<StorePotion>) potionsField.get(screen);
            for (AbstractCard card : coloredCards) shopReward.addCard(card);
            for (AbstractCard card : colorlessCards) shopReward.addCard(card);
            for (StoreRelic relic : relics) {
                if (settings.relicsToBuy.contains(relic.relic.relicId) && relic.price <= player.gold) {
                    awardRelic(relic.relic, shopReward);
                    addGoldReward(-relic.price);
                } else {
                    shopReward.addRelic(relic.relic.relicId);
                }
            }
            for (AbstractCard card : shopReward.cards) {
                if (settings.cardsToBuy.contains(card.cardID) && card.price <= player.gold) {
                    player.masterDeck.addToBottom(card);
                    Reward r = new Reward(AbstractDungeon.floorNum);
                    r.addCard(card);
                    seedResult.addMiscReward(r);
                    addGoldReward(-card.price);
                }
            }
            for (StorePotion potion : potions) {
                if (settings.potionsToBuy.contains(potion.potion.ID) && potion.price <= player.gold) {
                    Reward r = new Reward(AbstractDungeon.floorNum);
                    r.addPotion(potion.potion);
                    seedResult.addMiscReward(r);
                    addGoldReward(-potion.price);
                }
                shopReward.addPotion(potion.potion);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return shopReward;
    }

    private Reward getEventReward(AbstractEvent event, String eventKey, int floor) {
        CombatRewards.clear();
        StandaloneHooks.resetObtainedCards();
        Random miscRng = AbstractDungeon.miscRng;
        Reward reward = new Reward(floor);
        switch (eventKey) {
            case GoopPuddle.ID: addGoldReward(75); break;
            case Sssserpent.ID:
                if (settings.takeSerpentGold) {
                    int goldgain = AbstractDungeon.ascensionLevel >= 15 ? 150 : 175;
                    addGoldReward(goldgain);
                    addInvoluntaryCardReward(new Doubt(), reward);
                }
                break;
            case AccursedBlacksmith.ID:
                if (settings.takeWarpedTongs) {
                    reward.addRelic(WarpedTongs.ID);
                    addInvoluntaryCardReward(new Pain(), reward);
                }
                break;
            case BigFish.ID:
                if (settings.takeBigFishRelic) {
                    AbstractRelic.RelicTier fishTier = AbstractDungeon.returnRandomRelicTier();
                    awardRelic(AbstractDungeon.returnRandomScreenlessRelic(fishTier), reward);
                    addInvoluntaryCardReward(new Regret(), reward);
                }
                break;
            case DeadAdventurer.ID:
                if (settings.takeDeadAdventurerFight) {
                    try {
                        Method getMonster = DeadAdventurer.class.getDeclaredMethod("getMonster");
                        getMonster.setAccessible(true);
                        String monster = (String) getMonster.invoke(event);
                        int encounterChance = AbstractDungeon.ascensionLevel >= 15 ? 35 : 25;
                        for (int i = 1; i <= 3; i++) {
                            if (miscRng.random(0, 99) < encounterChance) {
                                addGoldReward(miscRng.random(25, 35));
                                seedResult.addCardReward(AbstractDungeon.floorNum, AbstractDungeon.getRewardCards());
                                seedResult.registerCombat(monster);
                                break;
                            } else encounterChance += 25;
                        }
                        addGoldReward(30);
                        awardRelic(AbstractDungeon.returnRandomScreenlessRelic(AbstractDungeon.returnRandomRelicTier()), reward);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case TheMausoleum.ID:
                if (settings.takeMausoleumRelic) {
                    awardRelic(AbstractDungeon.returnRandomScreenlessRelic(AbstractDungeon.returnRandomRelicTier()), reward);
                    if (miscRng.randomBoolean() || AbstractDungeon.ascensionLevel >= 15) {
                        addInvoluntaryCardReward(new Writhe(), reward);
                    }
                }
                break;
            case ScrapOoze.ID:
                if (settings.takeScrapOozeRelic) {
                    awardRelic(AbstractDungeon.returnRandomScreenlessRelic(AbstractDungeon.returnRandomRelicTier()), reward);
                }
                break;
            case Addict.ID:
                if (settings.takeAddictRelic) {
                    awardRelic(AbstractDungeon.returnRandomScreenlessRelic(AbstractDungeon.returnRandomRelicTier()), reward);
                }
                break;
            case MysteriousSphere.ID:
                if (settings.takeMysteriousSphereFight) {
                    addGoldReward(miscRng.random(45, 55));
                    seedResult.registerCombat("2 Orb Walkers");
                    awardRelic(AbstractDungeon.returnRandomScreenlessRelic(AbstractRelic.RelicTier.RARE), reward);
                    seedResult.addCardReward(AbstractDungeon.floorNum, AbstractDungeon.getRewardCards());
                }
                break;
            case TombRedMask.ID:
                if (!player.hasRelic(RedMask.ID) && settings.takeRedMaskAct3) {
                    awardRelic(RedMask.ID, reward);
                    addGoldReward(-player.gold);
                } else addGoldReward(222);
                break;
            case Mushrooms.ID:
                if (settings.takeMushroomFight) {
                    seedResult.registerCombat(MUSHROOMS_EVENT_ENC);
                    addGoldReward(miscRng.random(25, 35));
                    awardRelic(OddMushroom.ID, reward);
                    seedResult.addCardReward(AbstractDungeon.floorNum, AbstractDungeon.getRewardCards());
                } else addInvoluntaryCardReward(new Parasite(), reward);
                break;
            case MaskedBandits.ID:
                if (settings.takeMaskedBanditFight) {
                    seedResult.registerCombat(MASKED_BANDITS_ENC);
                    addGoldReward(miscRng.random(25, 35));
                    awardRelic(RedMask.ID, reward);
                    seedResult.addCardReward(AbstractDungeon.floorNum, AbstractDungeon.getRewardCards());
                } else addGoldReward(-player.gold);
                break;
            case GoldenIdol.ID:
                if (settings.takeGoldenIdolWithCurse) {
                    awardRelic(GoldenIdol.ID, reward);
                    addInvoluntaryCardReward(new Injury(), reward);
                } else if (settings.takeGoldenIdolWithoutCurse) {
                    awardRelic(GoldenIdol.ID, reward);
                }
                break;
            case ForgottenAltar.ID:
                if (player.hasRelic(GoldenIdol.ID) && settings.tradeGoldenIdolForBloody) {
                    awardRelic(BloodyIdol.ID, reward);
                    loseRelic(GoldenIdol.ID);
                }
                break;
            case Bonfire.ID:
                if (player.isCursed()) awardRelic(SpiritPoop.ID, reward);
                break;
            case CursedTome.ID:
                if (settings.takeCursedTome) {
                    int roll = miscRng.random(2);
                    switch (roll) {
                        case 0: awardRelic(Necronomicon.ID, reward); break;
                        case 1: awardRelic(Enchiridion.ID, reward); break;
                        case 2: awardRelic(NilrysCodex.ID, reward); break;
                    }
                }
                break;
            case FaceTrader.ID:
                if (settings.tradeFaces) {
                    try {
                        Method hotkeyCheck = FaceTrader.class.getDeclaredMethod("getRandomFace");
                        hotkeyCheck.setAccessible(true);
                        awardRelic((AbstractRelic) hotkeyCheck.invoke(event), reward);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case MindBloom.ID:
                if (settings.takeMindBloomGold && AbstractDungeon.floorNum <= 40) {
                    addGoldReward(999);
                    addInvoluntaryCardReward(new Normality(), reward);
                    addInvoluntaryCardReward(new Normality(), reward);
                } else if (settings.takeMindBloomFight) {
                    addGoldReward(100);
                    ArrayList<String> encounters = new ArrayList<>();
                    encounters.add(GUARDIAN_ENC);
                    encounters.add(HEXAGHOST_ENC);
                    encounters.add(SLIME_BOSS_ENC);
                    Collections.shuffle(encounters, new java.util.Random(miscRng.randomLong()));
                    seedResult.registerCombat(encounters.get(0));
                    awardRelic(AbstractDungeon.returnRandomRelic(AbstractRelic.RelicTier.RARE), reward);
                    seedResult.addCardReward(AbstractDungeon.floorNum, AbstractDungeon.getRewardCards());
                } else if (settings.takeMindBloomUpgrade) {
                    awardRelic(MarkOfTheBloom.ID, reward);
                }
                break;
            case MoaiHead.ID:
                if (settings.tradeGoldenIdolForMoney && player.hasRelic(GoldenIdol.ID)) {
                    loseRelic(GoldenIdol.ID);
                    addGoldReward(333);
                }
                break;
            case Colosseum.ID:
                seedResult.registerCombat(COLOSSEUM_SLAVER_ENC);
                AbstractDungeon.treasureRng.random(10, 20);
                if (settings.takeColosseumFight) {
                    seedResult.registerCombat(COLOSSEUM_NOB_ENC);
                    awardRelic(AbstractDungeon.returnRandomRelic(AbstractRelic.RelicTier.RARE), reward);
                    awardRelic(AbstractDungeon.returnRandomRelic(AbstractRelic.RelicTier.UNCOMMON), reward);
                    addGoldReward(100);
                    seedResult.addCardReward(AbstractDungeon.floorNum, AbstractDungeon.getRewardCards());
                }
                break;
            case TheLibrary.ID:
                if (settings.takeLibraryCard) {
                    CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
                    for (int i = 0; i < 20; i++) {
                        AbstractCard card = AbstractDungeon.getCard(AbstractDungeon.rollRarity()).makeCopy();
                        if (!group.contains(card)) group.addToBottom(card);
                        else i--;
                    }
                    reward.addCards(group.group);
                }
                break;
            case DrugDealer.ID:
                if (settings.takeDrugDealerRelic) {
                    awardRelic(MutagenicStrength.ID, reward);
                } else if (settings.takeDrugDealerTransform) {
                    for (int i = 0; i < 2; i++) {
                        AbstractDungeon.transformCard(new Strike_Red(), false, miscRng);
                        reward.addCard(AbstractDungeon.getTransformedCard());
                    }
                } else reward.addCard(new JAX());
                break;
            case SensoryStone.ID:
                for (int i = 0; i < settings.numSensoryStoneCards; i++) {
                    seedResult.addCardReward(AbstractDungeon.floorNum, AbstractDungeon.getColorlessRewardCards());
                }
                break;
            case WeMeetAgain.ID:
                if (settings.takeWeMeetAgainRelic) {
                    awardRelic(AbstractDungeon.returnRandomScreenlessRelic(AbstractDungeon.returnRandomRelicTier()), reward);
                }
                break;
            case WindingHalls.ID:
                if (settings.takeWindingHallsCurse) addInvoluntaryCardReward(new Writhe(), reward);
                else if (settings.takeWindingHallsMadness) {
                    addInvoluntaryCardReward(new Madness(), reward);
                    addInvoluntaryCardReward(new Madness(), reward);
                }
                break;
            case GremlinMatchGame.ID:
                try {
                    Field eventCards = GremlinMatchGame.class.getDeclaredField("cards");
                    eventCards.setAccessible(true);
                    CardGroup gremlinCards = (CardGroup) eventCards.get(event);
                    ArrayList<AbstractCard> matchCards = new ArrayList<>();
                    ArrayList<String> pairs = new ArrayList<>();
                    for (AbstractCard card : gremlinCards.group) {
                        if (pairs.contains(card.cardID)) pairs.remove(card.cardID);
                        else { pairs.add(card.cardID); matchCards.add(card); }
                    }
                    reward.addCards(matchCards);
                } catch (NoSuchFieldException | IllegalAccessException e) { e.printStackTrace(); }
                break;
            case GremlinWheelGame.ID:
                try {
                    Method buttonMethod = GremlinWheelGame.class.getDeclaredMethod("buttonEffect", int.class);
                    buttonMethod.setAccessible(true);
                    buttonMethod.invoke(event, 0);
                    Method preResultMethod = GremlinWheelGame.class.getDeclaredMethod("preApplyResult");
                    preResultMethod.setAccessible(true);
                    preResultMethod.invoke(event);
                    Method resultMethod = GremlinWheelGame.class.getDeclaredMethod("applyResult");
                    resultMethod.setAccessible(true);
                    resultMethod.invoke(event);
                    for (AbstractRelic r : CombatRewards.combatRelics) awardRelic(r, reward);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                break;
            case Lab.ID:
                ArrayList<AbstractPotion> potions = new ArrayList<>();
                potions.add(PotionHelper.getRandomPotion());
                potions.add(PotionHelper.getRandomPotion());
                if (AbstractDungeon.ascensionLevel < 15) potions.add(PotionHelper.getRandomPotion());
                reward.addPotions(potions);
                break;
            case WomanInBlue.ID:
                ArrayList<AbstractPotion> womanPotions = new ArrayList<>();
                womanPotions.add(PotionHelper.getRandomPotion());
                womanPotions.add(PotionHelper.getRandomPotion());
                womanPotions.add(PotionHelper.getRandomPotion());
                reward.addPotions(womanPotions);
                break;
            default:
                break;
        }
        if (StandaloneHooks.obtainedCards.size() > 0) {
            for (AbstractCard card : StandaloneHooks.obtainedCards) {
                addInvoluntaryCardReward(card, reward);
            }
        }
        return reward;
    }

    private void addGoldReward(int amount) {
        if (amount > 0) player.gainGold(amount);
        else player.loseGold(-amount);
    }

    private AbstractPotion getPotionReward() {
        int chance = 40 + AbstractRoom.blizzardPotionMod;
        if (player.hasRelic(WhiteBeast.ID)) chance = 100;
        if (AbstractDungeon.potionRng.random(0, 99) >= chance && !Settings.isDebug) {
            AbstractRoom.blizzardPotionMod += 10;
            return null;
        }
        AbstractRoom.blizzardPotionMod -= 10;
        return AbstractDungeon.returnRandomPotion();
    }

    private ArrayList<AbstractCard> addBottleDummyCardsForChest() {
        ArrayList<AbstractCard> dummies = new ArrayList<>();
        if (settings.alwaysSpawnBottledLightning) {
            boolean hasNonBasicSkill = false;
            for (AbstractCard card : player.masterDeck.group) {
                if (card.type == AbstractCard.CardType.SKILL && card.rarity != AbstractCard.CardRarity.BASIC) {
                    hasNonBasicSkill = true;
                    break;
                }
            }
            if (!hasNonBasicSkill) {
                AbstractCard dummy = AbstractDungeon.getCardFromPool(AbstractCard.CardRarity.COMMON, AbstractCard.CardType.SKILL, false);
                if (dummy != null) {
                    dummy = dummy.makeCopy();
                    player.masterDeck.addToTop(dummy);
                    dummies.add(dummy);
                }
            }
        }
        if (settings.alwaysSpawnBottledTornado && !CardHelper.hasCardType(AbstractCard.CardType.POWER)) {
            AbstractCard dummy = AbstractDungeon.getCardFromPool(AbstractCard.CardRarity.COMMON, AbstractCard.CardType.POWER, false);
            if (dummy != null) {
                dummy = dummy.makeCopy();
                player.masterDeck.addToTop(dummy);
                dummies.add(dummy);
            }
        }
        if (settings.alwaysSpawnBottledFlame) {
            boolean hasNonBasicAttack = false;
            for (AbstractCard card : player.masterDeck.group) {
                if (card.type == AbstractCard.CardType.ATTACK && card.rarity != AbstractCard.CardRarity.BASIC) {
                    hasNonBasicAttack = true;
                    break;
                }
            }
            if (!hasNonBasicAttack) {
                AbstractCard dummy = AbstractDungeon.getCardFromPool(AbstractCard.CardRarity.COMMON, AbstractCard.CardType.ATTACK, false);
                if (dummy != null) {
                    dummy = dummy.makeCopy();
                    player.masterDeck.addToTop(dummy);
                    dummies.add(dummy);
                }
            }
        }
        return dummies;
    }

    private void addInvoluntaryCardReward(AbstractCard card, Reward reward) {
        reward.cards.add(card);
        player.masterDeck.addToTop(card);
    }

    private void loseRelic(String relicID) {
        player.loseRelic(relicID);
    }
}
