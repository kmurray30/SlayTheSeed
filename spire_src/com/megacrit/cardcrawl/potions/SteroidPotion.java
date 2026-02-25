package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.powers.LoseStrengthPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class SteroidPotion extends AbstractPotion {
   public static final String POTION_ID = "SteroidPotion";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("SteroidPotion");

   public SteroidPotion() {
      super(potionStrings.NAME, "SteroidPotion", AbstractPotion.PotionRarity.COMMON, AbstractPotion.PotionSize.FAIRY, AbstractPotion.PotionColor.STEROID);
      this.isThrown = false;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1] + this.potency + potionStrings.DESCRIPTIONS[2];
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.tips.add(new PowerTip(TipHelper.capitalize(GameDictionary.STRENGTH.NAMES[0]), GameDictionary.keywords.get(GameDictionary.STRENGTH.NAMES[0])));
   }

   @Override
   public void use(AbstractCreature target) {
      AbstractCreature var2 = AbstractDungeon.player;
      if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
         this.addToBot(new ApplyPowerAction(var2, AbstractDungeon.player, new StrengthPower(var2, this.potency), this.potency));
         this.addToBot(new ApplyPowerAction(var2, AbstractDungeon.player, new LoseStrengthPower(var2, this.potency), this.potency));
      }
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 5;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new SteroidPotion();
   }
}
