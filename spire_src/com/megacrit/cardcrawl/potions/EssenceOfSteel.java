package com.megacrit.cardcrawl.potions;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.powers.PlatedArmorPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class EssenceOfSteel extends AbstractPotion {
   public static final String POTION_ID = "EssenceOfSteel";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("EssenceOfSteel");

   public EssenceOfSteel() {
      super(
         potionStrings.NAME,
         "EssenceOfSteel",
         AbstractPotion.PotionRarity.UNCOMMON,
         AbstractPotion.PotionSize.ANVIL,
         AbstractPotion.PotionEffect.NONE,
         Color.TEAL,
         null,
         null
      );
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
      AbstractCreature var2 = AbstractDungeon.player;
      if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
         this.addToBot(new ApplyPowerAction(var2, AbstractDungeon.player, new PlatedArmorPower(var2, this.potency), this.potency));
      }
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 4;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new EssenceOfSteel();
   }
}
