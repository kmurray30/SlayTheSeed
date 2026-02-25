package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BufferPower extends AbstractPower {
   public static final String POWER_ID = "Buffer";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Buffer");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public BufferPower(AbstractCreature owner, int bufferAmt) {
      this.name = NAME;
      this.ID = "Buffer";
      this.owner = owner;
      this.amount = bufferAmt;
      this.updateDescription();
      this.loadRegion("buffer");
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
   }

   @Override
   public void updateDescription() {
      if (this.amount <= 1) {
         this.description = DESCRIPTIONS[0];
      } else {
         this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
      }
   }

   @Override
   public int onAttackedToChangeDamage(DamageInfo info, int damageAmount) {
      if (damageAmount > 0) {
         this.addToTop(new ReducePowerAction(this.owner, this.owner, this.ID, 1));
      }

      return 0;
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
