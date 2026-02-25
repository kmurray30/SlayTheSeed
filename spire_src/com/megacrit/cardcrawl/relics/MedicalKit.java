package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MedicalKit extends AbstractRelic {
   public static final String ID = "Medical Kit";

   public MedicalKit() {
      super("Medical Kit", "medicalKit.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new MedicalKit();
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.STATUS) {
         AbstractDungeon.player.getRelic("Medical Kit").flash();
         card.exhaust = true;
         action.exhaustCard = true;
      }
   }
}
