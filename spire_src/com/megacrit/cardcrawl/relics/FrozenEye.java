package com.megacrit.cardcrawl.relics;

public class FrozenEye extends AbstractRelic {
   public static final String ID = "Frozen Eye";

   public FrozenEye() {
      super("Frozen Eye", "frozenEye.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.SOLID);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new FrozenEye();
   }
}
