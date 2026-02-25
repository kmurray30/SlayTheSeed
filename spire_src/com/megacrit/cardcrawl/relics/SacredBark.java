package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.potions.AbstractPotion;

public class SacredBark extends AbstractRelic {
   public static final String ID = "SacredBark";

   public SacredBark() {
      super("SacredBark", "bark.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEquip() {
      for (AbstractPotion p : AbstractDungeon.player.potions) {
         p.initializeData();
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new SacredBark();
   }
}
