package seedsearch.core.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Base type for all decision points in a run.
 * Subclasses represent the different choice types (Neow, path, card reward, etc.).
 */
public abstract class DecisionPoint {

    public abstract String getType();

    /** Neow choice: pick one of 4 options by index (0-3). */
    public static class NeowChoice extends DecisionPoint {
        public final List<String> options = new ArrayList<>();
        public final int floor = 0;

        @Override
        public String getType() {
            return "neow";
        }
    }

    /** Path choice: pick which node to go to next. Options are node IDs "y,x". */
    public static class PathChoice extends DecisionPoint {
        public final List<String> nextNodeIds = new ArrayList<>();
        public final int floor;

        public PathChoice(int floor) {
            this.floor = floor;
        }

        @Override
        public String getType() {
            return "path";
        }
    }

    /** Card reward: pick one of 3 cards or skip. Options are card IDs. */
    public static class CardRewardChoice extends DecisionPoint {
        public final List<CardOption> cards = new ArrayList<>();
        public final boolean canSkip = true;
        public final int floor;

        public CardRewardChoice(int floor) {
            this.floor = floor;
        }

        @Override
        public String getType() {
            return "cardReward";
        }

        public static class CardOption {
            public final String cardId;
            public final String name;

            public CardOption(String cardId, String name) {
                this.cardId = cardId;
                this.name = name;
            }
        }
    }

    /** Boss relic: pick one of 3 relics or skip. Options are relic IDs. */
    public static class BossRelicChoice extends DecisionPoint {
        public final List<String> relicIds = new ArrayList<>();
        public final boolean canSkip = true;
        public final int floor;

        public BossRelicChoice(int floor) {
            this.floor = floor;
        }

        @Override
        public String getType() {
            return "bossRelic";
        }
    }

    /** Shop: buy cards, relics, potions. Options are item IDs with prices. */
    public static class ShopChoice extends DecisionPoint {
        public final List<ShopCardOption> cards = new ArrayList<>();
        public final List<ShopRelicOption> relics = new ArrayList<>();
        public final List<ShopPotionOption> potions = new ArrayList<>();
        public final int floor;
        public final int gold;

        public ShopChoice(int floor, int gold) {
            this.floor = floor;
            this.gold = gold;
        }

        @Override
        public String getType() {
            return "shop";
        }

        public static class ShopCardOption {
            public final String cardId;
            public final String name;
            public final int price;

            public ShopCardOption(String cardId, String name, int price) {
                this.cardId = cardId;
                this.name = name;
                this.price = price;
            }
        }

        public static class ShopRelicOption {
            public final String relicId;
            public final String name;
            public final int price;

            public ShopRelicOption(String relicId, String name, int price) {
                this.relicId = relicId;
                this.name = name;
                this.price = price;
            }
        }

        public static class ShopPotionOption {
            public final String potionId;
            public final String name;
            public final int price;

            public ShopPotionOption(String potionId, String name, int price) {
                this.potionId = potionId;
                this.name = name;
                this.price = price;
            }
        }
    }

    /** Event: pick one option. Options are {id, label} pairs. */
    public static class EventChoice extends DecisionPoint {
        public final String eventId;
        public final List<EventOption> options = new ArrayList<>();
        public final int floor;

        public EventChoice(String eventId, int floor) {
            this.eventId = eventId;
            this.floor = floor;
        }

        @Override
        public String getType() {
            return "event";
        }

        public static class EventOption {
            public final String id;
            public final String label;

            public EventOption(String id, String label) {
                this.id = id;
                this.label = label;
            }
        }
    }
}
