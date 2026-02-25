package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ForcefieldPower extends AbstractPower {
   public static final String POWER_ID = "Nullify Attack";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Nullify Attack");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public ForcefieldPower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "Nullify Attack";
      this.owner = owner;
      this.amount = -1;
      this.updateDescription();
      this.loadRegion("forcefield");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0];
   }

   @Override
   public float atDamageFinalReceive(float damage, DamageInfo.DamageType type) {
      return damage > 0.0F && type != DamageInfo.DamageType.HP_LOSS && type != DamageInfo.DamageType.THORNS ? 0.0F : damage;
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
