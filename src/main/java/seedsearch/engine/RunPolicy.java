package seedsearch.engine;

import com.megacrit.cardcrawl.map.MapRoomNode;

import java.util.List;

/**
 * Policy for providing choices during a run.
 * When getPathForCurrentAct returns non-null, RunEngine auto-advances through the path without pausing for PathChoice.
 */
public interface RunPolicy {

    /**
     * Returns the path for the current act, or null if the engine should pause for path choice.
     * The engine is passed so the policy can call engine.findMapPath(map) if needed.
     * When non-null, RunEngine uses this path and does not return PathChoice.
     */
    default List<MapRoomNode> getPathForCurrentAct(RunEngine engine) {
        return null;
    }

    /**
     * Provide a choice for the given decision point.
     * Return null to pause (caller will block until user provides choice via applyChoice).
     * Return non-null to apply immediately and continue.
     */
    Object choose(DecisionPoint decisionPoint);
}
