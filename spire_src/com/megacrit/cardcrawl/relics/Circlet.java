package com.megacrit.cardcrawl.relics;

public class Circlet extends AbstractRelic {
   public static final String ID = "Circlet";

   public Circlet() {
      super("Circlet", "circlet.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.CLINK);
      this.counter = 1;
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEquip() {
      this.flash();
   }

   @Override
   public void onUnequip() {
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Circlet();
   }
}
