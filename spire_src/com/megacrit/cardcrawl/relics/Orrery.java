package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Orrery extends AbstractRelic {
   public static final String ID = "Orrery";

   public Orrery() {
      super("Orrery", "orrery.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEquip() {
      for (int i = 0; i < 4; i++) {
         AbstractDungeon.getCurrRoom().addCardToRewards();
      }

      AbstractDungeon.combatRewardScreen.open(this.DESCRIPTIONS[1]);
      AbstractDungeon.getCurrRoom().rewardPopOutTimer = 0.0F;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Orrery();
   }
}
