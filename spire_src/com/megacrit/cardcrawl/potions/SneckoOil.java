package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.unique.RandomizeHandCostAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class SneckoOil extends AbstractPotion {
   public static final String POTION_ID = "SneckoOil";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("SneckoOil");

   public SneckoOil() {
      super(potionStrings.NAME, "SneckoOil", AbstractPotion.PotionRarity.RARE, AbstractPotion.PotionSize.SNECKO, AbstractPotion.PotionColor.SNECKO);
      this.isThrown = false;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
   }

   @Override
   public void use(AbstractCreature target) {
      if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
         this.addToBot(new DrawCardAction(AbstractDungeon.player, this.potency));
         this.addToBot(new RandomizeHandCostAction());
      }
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 5;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new SneckoOil();
   }
}
