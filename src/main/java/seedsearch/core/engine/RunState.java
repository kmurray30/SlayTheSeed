package seedsearch.core.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable run state for UI display.
 */
public class RunState {

    public final long seed;
    public final int floor;
    public final int gold;
    public final int maxHealth;
    public final int currentHealth;
    public final List<String> relicIds = new ArrayList<>();
    public final List<String> deckCardIds = new ArrayList<>();
    public final List<String> potionIds = new ArrayList<>();
    public final int act;
    public final String actName;

    public RunState(long seed, int floor, int gold, int maxHealth, int currentHealth,
                   List<String> relicIds, List<String> deckCardIds, List<String> potionIds,
                   int act, String actName) {
        this.seed = seed;
        this.floor = floor;
        this.gold = gold;
        this.maxHealth = maxHealth;
        this.currentHealth = currentHealth;
        if (relicIds != null) {
            this.relicIds.addAll(relicIds);
        }
        if (deckCardIds != null) {
            this.deckCardIds.addAll(deckCardIds);
        }
        if (potionIds != null) {
            this.potionIds.addAll(potionIds);
        }
        this.act = act;
        this.actName = actName;
    }
}
