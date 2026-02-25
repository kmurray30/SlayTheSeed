package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class RegenerateMonsterPower extends AbstractPower {
   public static final String POWER_ID = "Regenerate";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Regenerate");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public RegenerateMonsterPower(AbstractMonster owner, int regenAmt) {
      this.name = NAME;
      this.ID = "Regenerate";
      this.owner = owner;
      this.amount = regenAmt;
      this.updateDescription();
      this.loadRegion("regen");
      this.type = AbstractPower.PowerType.BUFF;
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      this.flash();
      if (!this.owner.halfDead && !this.owner.isDying && !this.owner.isDead) {
         this.addToBot(new HealAction(this.owner, this.owner, this.amount));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
