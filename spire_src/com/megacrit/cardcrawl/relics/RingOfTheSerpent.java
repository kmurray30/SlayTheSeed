package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class RingOfTheSerpent extends AbstractRelic {
   public static final String ID = "Ring of the Serpent";
   private static final int NUM_CARDS = 1;

   public RingOfTheSerpent() {
      super("Ring of the Serpent", "serpent_ring.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + this.DESCRIPTIONS[1];
   }

   @Override
   public void onEquip() {
      AbstractDungeon.player.masterHandSize++;
   }

   @Override
   public void onUnequip() {
      AbstractDungeon.player.masterHandSize--;
   }

   @Override
   public void atTurnStart() {
      this.flash();
   }

   @Override
   public boolean canSpawn() {
      return AbstractDungeon.player.hasRelic("Ring of the Snake");
   }

   @Override
   public AbstractRelic makeCopy() {
      return new RingOfTheSerpent();
   }
}
