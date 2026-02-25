package com.megacrit.cardcrawl.relics;

public class RunicPyramid extends AbstractRelic {
   public static final String ID = "Runic Pyramid";

   public RunicPyramid() {
      super("Runic Pyramid", "runicPyramid.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new RunicPyramid();
   }
}
