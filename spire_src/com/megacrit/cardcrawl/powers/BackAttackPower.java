package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BackAttackPower extends AbstractPower {
   public static final String POWER_ID = "BackAttack";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("BackAttack");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public BackAttackPower(AbstractCreature owner) {
      this.name = NAME;
      this.type = AbstractPower.PowerType.BUFF;
      this.ID = "BackAttack";
      this.owner = owner;
      this.amount = -1;
      this.updateDescription();
      if (owner.hb.cX < Settings.WIDTH / 2.0F) {
         this.loadRegion("backAttack");
      } else {
         this.loadRegion("backAttack2");
      }
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0];
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
