package com.megacrit.cardcrawl.relics;

public class MarkOfTheBloom extends AbstractRelic {
   public static final String ID = "Mark of the Bloom";

   public MarkOfTheBloom() {
      super("Mark of the Bloom", "bloom.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public int onPlayerHeal(int healAmount) {
      this.flash();
      return 0;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new MarkOfTheBloom();
   }
}
