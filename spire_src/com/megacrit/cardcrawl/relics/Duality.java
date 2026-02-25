package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.DexterityPower;
import com.megacrit.cardcrawl.powers.LoseDexterityPower;

public class Duality extends AbstractRelic {
   public static final String ID = "Yang";

   public Duality() {
      super("Yang", "duality.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.ATTACK) {
         this.flash();
         AbstractPlayer p = AbstractDungeon.player;
         this.addToBot(new RelicAboveCreatureAction(p, this));
         this.addToBot(new ApplyPowerAction(p, p, new DexterityPower(p, 1), 1));
         this.addToBot(new ApplyPowerAction(p, p, new LoseDexterityPower(p, 1), 1));
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Duality();
   }
}
