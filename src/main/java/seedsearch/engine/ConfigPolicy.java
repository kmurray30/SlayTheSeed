package seedsearch.engine;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import seedsearch.SearchSettings;

import java.util.List;

/**
 * Policy that provides choices from SearchSettings.
 * Used by SeedRunner/sim to run without pausing - all choices come from config.
 */
public class ConfigPolicy implements RunPolicy {

    private final SearchSettings settings;

    public ConfigPolicy(SearchSettings settings) {
        this.settings = settings;
    }

    @Override
    public List<MapRoomNode> getPathForCurrentAct(RunEngine engine) {
        return engine.findMapPath(AbstractDungeon.map);
    }

    @Override
    public Object choose(DecisionPoint decisionPoint) {
        if (decisionPoint instanceof DecisionPoint.NeowChoice) {
            if (settings.forceNeowLament) {
                return 0;
            }
            return settings.neowChoice;
        }
        if (decisionPoint instanceof DecisionPoint.BossRelicChoice) {
            DecisionPoint.BossRelicChoice br = (DecisionPoint.BossRelicChoice) decisionPoint;
            for (String preferred : settings.bossRelicsToTake) {
                if (br.relicIds.contains(preferred)) {
                    return preferred;
                }
            }
            return br.canSkip ? "" : br.relicIds.get(0);
        }
        if (decisionPoint instanceof DecisionPoint.CardRewardChoice) {
            DecisionPoint.CardRewardChoice cr = (DecisionPoint.CardRewardChoice) decisionPoint;
            for (DecisionPoint.CardRewardChoice.CardOption opt : cr.cards) {
                if (settings.cardsToBuy.contains(opt.cardId)) {
                    return opt.cardId;
                }
            }
            return cr.canSkip ? null : cr.cards.get(0).cardId;
        }
        if (decisionPoint instanceof DecisionPoint.ShopChoice) {
            return 0; // Shop uses settings directly in getShopReward
        }
        if (decisionPoint instanceof DecisionPoint.EventChoice) {
            return 0; // Events use settings in getEventReward
        }
        return null;
    }
}
