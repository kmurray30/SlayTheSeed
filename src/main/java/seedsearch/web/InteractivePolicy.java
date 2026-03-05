package seedsearch.web;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import seedsearch.engine.DecisionPoint;
import seedsearch.engine.RunEngine;
import seedsearch.engine.RunPolicy;

import java.util.List;

/**
 * Policy that pauses at Neow, Path, and BossRelic choices for interactive web play.
 * Returns null for getPathForCurrentAct so the user chooses path; returns null for
 * choose() so the user picks Neow and BossRelic via the API.
 */
public class InteractivePolicy implements RunPolicy {

    @Override
    public List<MapRoomNode> getPathForCurrentAct(RunEngine engine) {
        return null;
    }

    @Override
    public Object choose(DecisionPoint decisionPoint) {
        return null;
    }
}
