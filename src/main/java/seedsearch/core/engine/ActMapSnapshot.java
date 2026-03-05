package seedsearch.core.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of the act map for frontend display.
 * Contains nodes and edges from AbstractDungeon.map.
 */
public class ActMapSnapshot {

    public static class MapNode {
        public final int y;
        public final int x;
        public final String roomType;
        public final String symbol;

        public MapNode(int y, int x, String roomType, String symbol) {
            this.y = y;
            this.x = x;
            this.roomType = roomType;
            this.symbol = symbol;
        }
    }

    public static class MapEdge {
        public final int fromY;
        public final int fromX;
        public final int toY;
        public final int toX;

        public MapEdge(int fromY, int fromX, int toY, int toX) {
            this.fromY = fromY;
            this.fromX = fromX;
            this.toY = toY;
            this.toX = toX;
        }
    }

    public final List<MapNode> nodes = new ArrayList<>();
    public final List<MapEdge> edges = new ArrayList<>();
}
