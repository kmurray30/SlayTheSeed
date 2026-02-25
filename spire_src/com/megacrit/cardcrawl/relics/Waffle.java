package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Waffle extends AbstractRelic {
   public static final String ID = "Lee's Waffle";
   private static final int HP_AMT = 7;

   public Waffle() {
      super("Lee's Waffle", "waffle.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 7 + this.DESCRIPTIONS[1];
   }

   @Override
   public void onEquip() {
      AbstractDungeon.player.increaseMaxHp(7, false);
      AbstractDungeon.player.heal(AbstractDungeon.player.maxHealth);
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Waffle();
   }
}
