package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.localization.LocalizedStrings;

public class ChemicalX extends AbstractRelic {
   public static final String ID = "Chemical X";
   public static final int BOOST = 2;

   public ChemicalX() {
      super("Chemical X", "chemicalX.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return !this.DESCRIPTIONS[1].equals("") ? this.DESCRIPTIONS[0] + 2 + this.DESCRIPTIONS[1] : this.DESCRIPTIONS[0] + 2 + LocalizedStrings.PERIOD;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new ChemicalX();
   }
}
