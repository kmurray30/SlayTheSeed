package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;

public class HappyFlower extends AbstractRelic {
   public static final String ID = "Happy Flower";
   private static final int NUM_TURNS = 3;
   private static final int ENERGY_AMT = 1;

   public HappyFlower() {
      super("Happy Flower", "sunflower.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.SOLID);
   }

   @Override
   public String getUpdatedDescription() {
      return AbstractDungeon.player != null ? this.setDescription(AbstractDungeon.player.chosenClass) : this.setDescription(null);
   }

   private String setDescription(AbstractPlayer.PlayerClass c) {
      return this.DESCRIPTIONS[0] + 3 + this.DESCRIPTIONS[1];
   }

   @Override
   public void updateDescription(AbstractPlayer.PlayerClass c) {
      this.description = this.setDescription(c);
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.initializeTips();
   }

   @Override
   public void onEquip() {
      this.counter = 0;
   }

   @Override
   public void atTurnStart() {
      if (this.counter == -1) {
         this.counter += 2;
      } else {
         this.counter++;
      }

      if (this.counter == 3) {
         this.counter = 0;
         this.flash();
         this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
         this.addToBot(new GainEnergyAction(1));
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new HappyFlower();
   }
}
