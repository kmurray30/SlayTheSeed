package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class VigorPower extends AbstractPower {
   public static final String POWER_ID = "Vigor";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Vigor");

   public VigorPower(AbstractCreature owner, int amount) {
      this.name = powerStrings.NAME;
      this.ID = "Vigor";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("vigor");
      this.type = AbstractPower.PowerType.BUFF;
      this.isTurnBased = false;
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }

   @Override
   public float atDamageGive(float damage, DamageInfo.DamageType type) {
      float var3;
      return type == DamageInfo.DamageType.NORMAL ? (var3 = damage + this.amount) : damage;
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.ATTACK) {
         this.flash();
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Vigor"));
      }
   }
}
