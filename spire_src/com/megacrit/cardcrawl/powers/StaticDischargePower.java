package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.orbs.Lightning;

public class StaticDischargePower extends AbstractPower {
   public static final String POWER_ID = "StaticDischarge";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("StaticDischarge");

   public StaticDischargePower(AbstractCreature owner, int lightningAmount) {
      this.name = powerStrings.NAME;
      this.ID = "StaticDischarge";
      this.owner = owner;
      this.amount = lightningAmount;
      this.updateDescription();
      this.loadRegion("static_discharge");
   }

   @Override
   public int onAttacked(DamageInfo info, int damageAmount) {
      if (info.type != DamageInfo.DamageType.THORNS
         && info.type != DamageInfo.DamageType.HP_LOSS
         && info.owner != null
         && info.owner != this.owner
         && damageAmount > 0) {
         this.flash();

         for (int i = 0; i < this.amount; i++) {
            this.addToTop(new ChannelAction(new Lightning()));
         }
      }

      return damageAmount;
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }
}
