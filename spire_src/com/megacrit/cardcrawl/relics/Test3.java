package com.megacrit.cardcrawl.relics;

public class Test3 extends AbstractRelic {
   public static final String ID = "Test 3";

   public Test3() {
      super("Test 3", "test3.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEquip() {
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Test3();
   }
}
