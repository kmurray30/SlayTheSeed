package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;

public class Ectoplasm extends AbstractRelic {
   public static final String ID = "Ectoplasm";

   public Ectoplasm() {
      super("Ectoplasm", "ectoplasm.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.FLAT);
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
   public boolean canSpawn() {
      return AbstractDungeon.actNum <= 1;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Ectoplasm();
   }
}
