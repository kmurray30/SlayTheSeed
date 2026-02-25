package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class FaceOfCleric extends AbstractRelic {
   public static final String ID = "FaceOfCleric";

   public FaceOfCleric() {
      super("FaceOfCleric", "clericFace.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onVictory() {
      this.flash();
      AbstractDungeon.player.increaseMaxHp(1, true);
   }

   @Override
   public AbstractRelic makeCopy() {
      return new FaceOfCleric();
   }
}
