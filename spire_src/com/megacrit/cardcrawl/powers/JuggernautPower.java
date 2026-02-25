package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageRandomEnemyAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class JuggernautPower extends AbstractPower {
   public static final String POWER_ID = "Juggernaut";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Juggernaut");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public JuggernautPower(AbstractCreature owner, int newAmount) {
      this.name = NAME;
      this.ID = "Juggernaut";
      this.owner = owner;
      this.amount = newAmount;
      this.updateDescription();
      this.loadRegion("juggernaut");
   }

   @Override
   public void onGainedBlock(float blockAmount) {
      if (blockAmount > 0.0F) {
         this.flash();
         this.addToBot(
            new DamageRandomEnemyAction(new DamageInfo(this.owner, this.amount, DamageInfo.DamageType.THORNS), AbstractGameAction.AttackEffect.SLASH_HORIZONTAL)
         );
      }
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
