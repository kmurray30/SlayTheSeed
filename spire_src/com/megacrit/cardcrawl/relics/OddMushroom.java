package com.megacrit.cardcrawl.relics;

public class OddMushroom extends AbstractRelic {
   public static final String ID = "Odd Mushroom";
   public static final float VULN_EFFECTIVENESS = 1.25F;
   public static final int EFFECTIVENESS_STRING = 25;

   public OddMushroom() {
      super("Odd Mushroom", "mushroom.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new OddMushroom();
   }
}
