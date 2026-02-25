package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class MetallicizePower extends AbstractPower {
   public static final String POWER_ID = "Metallicize";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Metallicize");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public MetallicizePower(AbstractCreature owner, int armorAmt) {
      this.name = NAME;
      this.ID = "Metallicize";
      this.owner = owner;
      this.amount = armorAmt;
      this.updateDescription();
      this.loadRegion("armor");
   }

   @Override
   public void playApplyPowerSfx() {
      CardCrawlGame.sound.play("POWER_METALLICIZE", 0.05F);
   }

   @Override
   public void updateDescription() {
      if (this.owner.isPlayer) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[2] + this.amount + DESCRIPTIONS[3];
      }
   }

   @Override
   public void atEndOfTurnPreEndTurnCards(boolean isPlayer) {
      this.flash();
      this.addToBot(new GainBlockAction(this.owner, this.owner, this.amount));
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
