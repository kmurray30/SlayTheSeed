package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.SuicideAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.ExplosionSmallEffect;

public class FadingPower extends AbstractPower {
   public static final String POWER_ID = "Fading";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Fading");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public FadingPower(AbstractCreature owner, int turns) {
      this.name = NAME;
      this.ID = "Fading";
      this.owner = owner;
      this.amount = turns;
      this.updateDescription();
      this.loadRegion("fading");
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[2];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      }
   }

   @Override
   public void duringTurn() {
      if (this.amount == 1 && !this.owner.isDying) {
         this.addToBot(new VFXAction(new ExplosionSmallEffect(this.owner.hb.cX, this.owner.hb.cY), 0.1F));
         this.addToBot(new SuicideAction((AbstractMonster)this.owner));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Fading", 1));
         this.updateDescription();
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
