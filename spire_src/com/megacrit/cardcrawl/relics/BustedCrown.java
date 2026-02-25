package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;

public class BustedCrown extends AbstractRelic {
   private static final int CARDS_SUBTRACTED = 2;
   public static final String ID = "Busted Crown";

   public BustedCrown() {
      super("Busted Crown", "crown.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return AbstractDungeon.player != null ? this.setDescription(AbstractDungeon.player.chosenClass) : this.setDescription(null);
   }

   private String setDescription(AbstractPlayer.PlayerClass c) {
      return this.DESCRIPTIONS[1] + this.DESCRIPTIONS[0];
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
      AbstractDungeon.player.energy.energyMaster++;
   }

   @Override
   public void onUnequip() {
      AbstractDungeon.player.energy.energyMaster--;
   }

   @Override
   public int changeNumberOfCardsInReward(int numberOfCards) {
      return numberOfCards - 2;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new BustedCrown();
   }
}
