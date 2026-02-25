package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class RegalPillow extends AbstractRelic {
   public static final String ID = "Regal Pillow";
   public static final int HEAL_AMT = 15;

   public RegalPillow() {
      super("Regal Pillow", "regal_pillow.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 15 + this.DESCRIPTIONS[1];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new RegalPillow();
   }

   @Override
   public boolean canSpawn() {
      return Settings.isEndless || AbstractDungeon.floorNum <= 48;
   }
}
