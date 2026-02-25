package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Nunchaku extends AbstractRelic {
   public static final String ID = "Nunchaku";
   private static final int NUM_CARDS = 10;

   public Nunchaku() {
      super("Nunchaku", "nunchaku.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.FLAT);
      this.counter = 0;
   }

   @Override
   public String getUpdatedDescription() {
      return AbstractDungeon.player != null ? this.setDescription(AbstractDungeon.player.chosenClass) : this.setDescription(null);
   }

   private String setDescription(AbstractPlayer.PlayerClass c) {
      return this.DESCRIPTIONS[0] + 10 + this.DESCRIPTIONS[1];
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.ATTACK) {
         this.counter++;
         if (this.counter % 10 == 0) {
            this.counter = 0;
            this.flash();
            this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
            this.addToBot(new GainEnergyAction(1));
         }
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Nunchaku();
   }
}
