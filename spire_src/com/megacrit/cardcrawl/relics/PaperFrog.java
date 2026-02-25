package com.megacrit.cardcrawl.relics;

public class PaperFrog extends AbstractRelic {
   public static final String ID = "Paper Frog";
   public static final float VULN_EFFECTIVENESS = 1.75F;
   public static final int EFFECTIVENESS_STRING = 75;

   public PaperFrog() {
      super("Paper Frog", "paperFrog.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new PaperFrog();
   }
}
