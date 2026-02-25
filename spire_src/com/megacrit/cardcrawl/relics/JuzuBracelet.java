package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class JuzuBracelet extends AbstractRelic {
   public static final String ID = "Juzu Bracelet";

   public JuzuBracelet() {
      super("Juzu Bracelet", "juzuBracelet.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public boolean canSpawn() {
      return Settings.isEndless || AbstractDungeon.floorNum <= 48;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new JuzuBracelet();
   }
}
