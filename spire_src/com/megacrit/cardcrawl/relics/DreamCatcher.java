package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DreamCatcher extends AbstractRelic {
   public static final String ID = "Dream Catcher";

   public DreamCatcher() {
      super("Dream Catcher", "dreamCatcher.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new DreamCatcher();
   }

   @Override
   public boolean canSpawn() {
      return Settings.isEndless || AbstractDungeon.floorNum <= 48;
   }
}
