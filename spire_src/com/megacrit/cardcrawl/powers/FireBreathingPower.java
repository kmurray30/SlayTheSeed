package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class FireBreathingPower extends AbstractPower {
   public static final String POWER_ID = "Fire Breathing";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Fire Breathing");

   public FireBreathingPower(AbstractCreature owner, int newAmount) {
      this.name = powerStrings.NAME;
      this.ID = "Fire Breathing";
      this.owner = owner;
      this.amount = newAmount;
      this.updateDescription();
      this.loadRegion("firebreathing");
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }

   @Override
   public void onCardDraw(AbstractCard card) {
      if (card.type == AbstractCard.CardType.STATUS || card.type == AbstractCard.CardType.CURSE) {
         this.flash();
         this.addToBot(
            new DamageAllEnemiesAction(
               null, DamageInfo.createDamageMatrix(this.amount, true), DamageInfo.DamageType.THORNS, AbstractGameAction.AttackEffect.FIRE, true
            )
         );
      }
   }
}
