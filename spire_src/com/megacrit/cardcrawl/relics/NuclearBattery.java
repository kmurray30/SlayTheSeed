package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.Plasma;

public class NuclearBattery extends AbstractRelic {
   public static final String ID = "Nuclear Battery";

   public NuclearBattery() {
      super("Nuclear Battery", "battery.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atPreBattle() {
      AbstractDungeon.player.channelOrb(new Plasma());
   }

   @Override
   public AbstractRelic makeCopy() {
      return new NuclearBattery();
   }
}
