package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;

public class WristBlade extends AbstractRelic {
   public static final String ID = "WristBlade";

   public WristBlade() {
      super("WristBlade", "wBlade.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new WristBlade();
   }

   @Override
   public float atDamageModify(float damage, AbstractCard c) {
      return c.costForTurn != 0 && (!c.freeToPlayOnce || c.cost == -1) ? damage : damage + 4.0F;
   }
}
