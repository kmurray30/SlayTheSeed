package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PrayerWheel extends AbstractRelic {
   public static final String ID = "Prayer Wheel";

   public PrayerWheel() {
      super("Prayer Wheel", "prayerWheel.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new PrayerWheel();
   }

   @Override
   public boolean canSpawn() {
      return Settings.isEndless || AbstractDungeon.floorNum <= 48;
   }
}
