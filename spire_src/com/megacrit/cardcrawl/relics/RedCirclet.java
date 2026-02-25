package com.megacrit.cardcrawl.relics;

public class RedCirclet extends AbstractRelic {
   public static final String ID = "Red Circlet";

   public RedCirclet() {
      super("Red Circlet", "redCirclet.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new RedCirclet();
   }
}
