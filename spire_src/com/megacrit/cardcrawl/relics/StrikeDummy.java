package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;

public class StrikeDummy extends AbstractRelic {
   public static final String ID = "StrikeDummy";

   public StrikeDummy() {
      super("StrikeDummy", "dummy.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 3 + this.DESCRIPTIONS[1];
   }

   @Override
   public float atDamageModify(float damage, AbstractCard c) {
      return c.hasTag(AbstractCard.CardTags.STRIKE) ? damage + 3.0F : damage;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new StrikeDummy();
   }
}
