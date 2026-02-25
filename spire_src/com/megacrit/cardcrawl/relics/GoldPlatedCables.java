package com.megacrit.cardcrawl.relics;

public class GoldPlatedCables extends AbstractRelic {
   public static final String ID = "Cables";

   public GoldPlatedCables() {
      super("Cables", "cables.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new GoldPlatedCables();
   }
}
