package seedsearch.core;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.util.ArrayList;

/**
 * Shared combat reward state used by both SeedRunner (batch search) and RunEngine (step-by-step).
 * Extracted to core so RunEngine does not depend on SeedRunner.
 */
public class CombatRewards {

    public static final ArrayList<AbstractRelic> combatRelics = new ArrayList<>();
    public static final ArrayList<Reward> combatCardRewards = new ArrayList<>();
    public static final ArrayList<AbstractPotion> combatPotions = new ArrayList<>();
    public static int combatGold = 0;

    public static void clear() {
        combatGold = 0;
        combatRelics.clear();
        combatCardRewards.clear();
        combatPotions.clear();
    }
}
