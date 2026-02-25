package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RepairPower extends AbstractPower {
   public static final String POWER_ID = "Repair";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Repair");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public RepairPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Repair";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("repair");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void onVictory() {
      AbstractPlayer p = AbstractDungeon.player;
      if (p.currentHealth > 0) {
         p.heal(this.amount);
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
