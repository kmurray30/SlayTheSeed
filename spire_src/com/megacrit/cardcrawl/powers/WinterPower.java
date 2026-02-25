package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.EmptyOrbSlot;
import com.megacrit.cardcrawl.orbs.Frost;

public class WinterPower extends AbstractPower {
   public static final String POWER_ID = "Winter";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Winter");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public WinterPower(AbstractCreature owner, int orbAmt) {
      this.name = NAME;
      this.ID = "Winter";
      this.owner = owner;
      this.amount = orbAmt;
      this.updateDescription();
      this.loadRegion("winter");
   }

   @Override
   public void atStartOfTurn() {
      if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         for (AbstractOrb o : AbstractDungeon.player.orbs) {
            if (o instanceof EmptyOrbSlot) {
               this.flash();
               break;
            }
         }

         for (int i = 0; i < this.amount; i++) {
            this.addToBot(new ChannelAction(new Frost(), false));
         }
      }
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
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
