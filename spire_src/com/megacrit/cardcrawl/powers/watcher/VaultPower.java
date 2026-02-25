package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class VaultPower extends AbstractPower {
   public static final String POWER_ID = "Vault";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Vault");
   private AbstractCreature source;

   public VaultPower(AbstractCreature target, AbstractCreature source, int amount) {
      this.name = powerStrings.NAME;
      this.ID = "Vault";
      this.owner = target;
      this.source = source;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("carddraw");
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }

   @Override
   public void atEndOfRound() {
      this.flash();
      this.addToBot(
         new DamageAction(this.owner, new DamageInfo(this.source, this.amount, DamageInfo.DamageType.NORMAL), AbstractGameAction.AttackEffect.BLUNT_HEAVY)
      );
      this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Vault"));
   }
}
