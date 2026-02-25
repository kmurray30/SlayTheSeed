package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.WeakPower;

public class ChampionsBelt extends AbstractRelic {
   public static final String ID = "Champion Belt";
   public static final int EFFECT = 1;

   public ChampionsBelt() {
      super("Champion Belt", "championBelt.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1];
   }

   @Override
   public void onTrigger(AbstractCreature target) {
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new ApplyPowerAction(target, AbstractDungeon.player, new WeakPower(target, 1, false), 1));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new ChampionsBelt();
   }
}
