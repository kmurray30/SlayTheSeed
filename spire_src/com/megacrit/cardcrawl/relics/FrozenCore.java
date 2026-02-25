package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.Frost;

public class FrozenCore extends AbstractRelic {
   public static final String ID = "FrozenCore";

   public FrozenCore() {
      super("FrozenCore", "frozenOrb.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onPlayerEndTurn() {
      if (AbstractDungeon.player.hasEmptyOrb()) {
         this.flash();
         AbstractDungeon.player.channelOrb(new Frost());
      }
   }

   @Override
   public boolean canSpawn() {
      return AbstractDungeon.player.hasRelic("Cracked Core");
   }

   @Override
   public AbstractRelic makeCopy() {
      return new FrozenCore();
   }
}
