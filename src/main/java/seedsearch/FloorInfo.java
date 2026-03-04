package seedsearch;

import java.util.ArrayList;

/**
 * Holds per-floor information for YAML output.
 * Path is the choice when entering this floor (0=leftmost, 1=second from left, etc.).
 * Null for Floor 0 (Neow) and Boss floors.
 */
public class FloorInfo {

    public int floor;
    public Integer path;           // null for Floor 0 (Neow) and Boss (no fork before)
    public String type;             // "Neow", "Monster", "Elite", "Event", "Rest", "Shop", "Treasure", "Boss"
    public String name;             // monster name, event instance, or boss name
    public ArrayList<String> cardRewards;
    public ArrayList<String> relics;
    public ArrayList<String> potions;
    public ArrayList<String> bossRelics;   // only for Boss type
    public ArrayList<String> neowOptions;  // only for Neow type
    public ArrayList<String> neowChoice;   // chosen reward (card names, relic name, etc.)
    public int neowChosenIndex = -1;       // index of chosen option for quoting

    public FloorInfo(int floor) {
        this.floor = floor;
        this.path = null;
        this.type = null;
        this.name = null;
        this.cardRewards = new ArrayList<>();
        this.relics = new ArrayList<>();
        this.potions = new ArrayList<>();
        this.bossRelics = new ArrayList<>();
        this.neowOptions = new ArrayList<>();
        this.neowChoice = new ArrayList<>();
    }

    public FloorInfo(int floor, Integer path, String type) {
        this(floor);
        this.path = path;
        this.type = type;
    }
}
