package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.SuicideAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.ExplosionSmallEffect;

public class ExplosivePower extends AbstractPower {
   public static final String POWER_ID = "Explosive";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Explosive");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private static final int DAMAGE_AMOUNT = 30;

   public ExplosivePower(AbstractCreature owner, int damage) {
      this.name = NAME;
      this.ID = "Explosive";
      this.owner = owner;
      this.amount = damage;
      this.updateDescription();
      this.loadRegion("explosive");
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[3] + 30 + DESCRIPTIONS[2];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1] + 30 + DESCRIPTIONS[2];
      }
   }

   @Override
   public void duringTurn() {
      if (this.amount == 1 && !this.owner.isDying) {
         this.addToBot(new VFXAction(new ExplosionSmallEffect(this.owner.hb.cX, this.owner.hb.cY), 0.1F));
         this.addToBot(new SuicideAction((AbstractMonster)this.owner));
         DamageInfo damageInfo = new DamageInfo(this.owner, 30, DamageInfo.DamageType.THORNS);
         this.addToBot(new DamageAction(AbstractDungeon.player, damageInfo, AbstractGameAction.AttackEffect.FIRE, true));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Explosive", 1));
         this.updateDescription();
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
