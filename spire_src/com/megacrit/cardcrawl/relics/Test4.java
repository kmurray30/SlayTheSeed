package com.megacrit.cardcrawl.relics;

public class Test4 extends AbstractRelic {
   public static final String ID = "Test 4";

   public Test4() {
      super("Test 4", "test4.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atBattleStart() {
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Test4();
   }
}
