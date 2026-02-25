package com.megacrit.cardcrawl.relics;

public class PaperCrane extends AbstractRelic {
   public static final String ID = "Paper Crane";
   public static final float WEAK_EFFECTIVENESS = 0.6F;
   public static final int EFFECTIVENESS_STRING = 40;

   public PaperCrane() {
      super("Paper Crane", "paperCrane.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new PaperCrane();
   }
}
