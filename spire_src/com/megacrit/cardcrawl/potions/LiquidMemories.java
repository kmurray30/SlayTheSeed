package com.megacrit.cardcrawl.potions;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.common.BetterDiscardPileToHandAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class LiquidMemories extends AbstractPotion {
   public static final String POTION_ID = "LiquidMemories";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("LiquidMemories");

   public LiquidMemories() {
      super(
         potionStrings.NAME,
         "LiquidMemories",
         AbstractPotion.PotionRarity.UNCOMMON,
         AbstractPotion.PotionSize.EYE,
         AbstractPotion.PotionEffect.NONE,
         new Color(225754111),
         new Color(389060095),
         null
      );
      this.isThrown = false;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      if (this.potency == 1) {
         this.description = potionStrings.DESCRIPTIONS[0];
      } else {
         this.description = potionStrings.DESCRIPTIONS[1] + this.potency + potionStrings.DESCRIPTIONS[2];
      }

      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
   }

   @Override
   public void use(AbstractCreature target) {
      this.addToBot(new BetterDiscardPileToHandAction(this.potency, 0));
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 1;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new LiquidMemories();
   }
}
