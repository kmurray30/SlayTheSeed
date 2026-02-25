package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.LocalizedStrings;

public class Strawberry extends AbstractRelic {
   public static final String ID = "Strawberry";
   private static final int HP_AMT = 7;

   public Strawberry() {
      super("Strawberry", "strawberry.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 7 + LocalizedStrings.PERIOD;
   }

   @Override
   public void onEquip() {
      AbstractDungeon.player.increaseMaxHp(7, true);
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Strawberry();
   }
}
