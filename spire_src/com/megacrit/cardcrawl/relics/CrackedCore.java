package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.Lightning;

public class CrackedCore extends AbstractRelic {
   public static final String ID = "Cracked Core";

   public CrackedCore() {
      super("Cracked Core", "crackedOrb.png", AbstractRelic.RelicTier.STARTER, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atPreBattle() {
      AbstractDungeon.player.channelOrb(new Lightning());
   }

   @Override
   public AbstractRelic makeCopy() {
      return new CrackedCore();
   }
}
