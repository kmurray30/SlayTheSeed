package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.WeakPower;

public class WaveOfTheHandPower extends AbstractPower {
   public static final String POWER_ID = "WaveOfTheHandPower";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("WaveOfTheHandPower");

   public WaveOfTheHandPower(AbstractCreature owner, int newAmount) {
      this.name = powerStrings.NAME;
      this.ID = "WaveOfTheHandPower";
      this.owner = owner;
      this.amount = newAmount;
      this.updateDescription();
      this.loadRegion("wave_of_the_hand");
   }

   @Override
   public void onGainedBlock(float blockAmount) {
      if (blockAmount > 0.0F) {
         this.flash();
         AbstractCreature p = AbstractDungeon.player;

         for (AbstractMonster mo : AbstractDungeon.getCurrRoom().monsters.monsters) {
            this.addToBot(new ApplyPowerAction(mo, p, new WeakPower(mo, this.amount, false), this.amount, true, AbstractGameAction.AttackEffect.NONE));
         }
      }
   }

   @Override
   public void atEndOfRound() {
      this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "WaveOfTheHandPower"));
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }
}
