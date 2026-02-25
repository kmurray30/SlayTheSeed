package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.unique.DiscoveryAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class PowerPotion extends AbstractPotion {
   public static final String POTION_ID = "PowerPotion";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("PowerPotion");

   public PowerPotion() {
      super(potionStrings.NAME, "PowerPotion", AbstractPotion.PotionRarity.COMMON, AbstractPotion.PotionSize.CARD, AbstractPotion.PotionColor.BLUE);
      this.isThrown = false;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      if (AbstractDungeon.player != null && AbstractDungeon.player.hasRelic("SacredBark")) {
         this.description = potionStrings.DESCRIPTIONS[1];
      } else {
         this.description = potionStrings.DESCRIPTIONS[0];
      }

      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
   }

   @Override
   public void use(AbstractCreature target) {
      this.addToBot(new DiscoveryAction(AbstractCard.CardType.POWER, this.potency));
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 1;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new PowerPotion();
   }
}
