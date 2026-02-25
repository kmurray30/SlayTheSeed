package com.megacrit.cardcrawl.relics;

public class NlothsGift extends AbstractRelic {
   public static final String ID = "Nloth's Gift";
   public static final float MULTIPLIER = 3.0F;

   public NlothsGift() {
      super("Nloth's Gift", "nlothsGift.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public int changeRareCardRewardChance(int rareCardChance) {
      return rareCardChance * 3;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new NlothsGift();
   }
}
