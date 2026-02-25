package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.megacrit.cardcrawl.ui.campfire.RestOption;

public class CoffeeDripper extends AbstractRelic {
   public static final String ID = "Coffee Dripper";

   public CoffeeDripper() {
      super("Coffee Dripper", "coffeeDripper.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.CLINK);
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
   public boolean canUseCampfireOption(AbstractCampfireOption option) {
      if (option instanceof RestOption && option.getClass().getName().equals(RestOption.class.getName())) {
         ((RestOption)option).updateUsability(false);
         return false;
      } else {
         return true;
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new CoffeeDripper();
   }
}
