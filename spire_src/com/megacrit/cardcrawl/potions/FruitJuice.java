package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.shrines.WeMeetAgain;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class FruitJuice extends AbstractPotion {
   public static final String POTION_ID = "Fruit Juice";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Fruit Juice");

   public FruitJuice() {
      super(potionStrings.NAME, "Fruit Juice", AbstractPotion.PotionRarity.RARE, AbstractPotion.PotionSize.HEART, AbstractPotion.PotionColor.FRUIT);
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
      AbstractDungeon.player.increaseMaxHp(this.potency, true);
   }

   @Override
   public boolean canUse() {
      return AbstractDungeon.actionManager.turnHasEnded && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT
         ? false
         : AbstractDungeon.getCurrRoom().event == null || !(AbstractDungeon.getCurrRoom().event instanceof WeMeetAgain);
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 5;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new FruitJuice();
   }
}
