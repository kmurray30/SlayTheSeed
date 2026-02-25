package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.shrines.WeMeetAgain;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class BloodPotion extends AbstractPotion {
   public static final String POTION_ID = "BloodPotion";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("BloodPotion");

   public BloodPotion() {
      super(potionStrings.NAME, "BloodPotion", AbstractPotion.PotionRarity.COMMON, AbstractPotion.PotionSize.H, AbstractPotion.PotionColor.WHITE);
      this.labOutlineColor = Settings.RED_RELIC_COLOR;
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
         this.addToBot(new HealAction(AbstractDungeon.player, AbstractDungeon.player, (int)(AbstractDungeon.player.maxHealth * (this.potency / 100.0F))));
      } else {
         AbstractDungeon.player.heal((int)(AbstractDungeon.player.maxHealth * (this.potency / 100.0F)));
      }
   }

   @Override
   public boolean canUse() {
      return AbstractDungeon.actionManager.turnHasEnded && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT
         ? false
         : AbstractDungeon.getCurrRoom().event == null || !(AbstractDungeon.getCurrRoom().event instanceof WeMeetAgain);
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 20;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new BloodPotion();
   }
}
