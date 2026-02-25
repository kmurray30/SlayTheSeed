package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.WeakPower;

public class GremlinMask extends AbstractRelic {
   public static final String ID = "GremlinMask";

   public GremlinMask() {
      super("GremlinMask", "gremlinMask.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atBattleStart() {
      this.flash();
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new WeakPower(AbstractDungeon.player, 1, false), 1));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new GremlinMask();
   }
}
