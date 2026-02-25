package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class PotionSlot extends AbstractPotion {
   public static final String POTION_ID = "Potion Slot";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Potion Slot");

   public PotionSlot(int slot) {
      super(potionStrings.NAME, "Potion Slot", AbstractPotion.PotionRarity.PLACEHOLDER, AbstractPotion.PotionSize.T, AbstractPotion.PotionColor.NONE);
      this.isObtained = true;
      this.description = potionStrings.DESCRIPTIONS[0];
      this.name = potionStrings.DESCRIPTIONS[1];
      this.tips.add(new PowerTip(this.name, this.description));
      this.adjustPosition(slot);
   }

   @Override
   public void use(AbstractCreature target) {
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 0;
   }

   @Override
   public AbstractPotion makeCopy() {
      return null;
   }
}
