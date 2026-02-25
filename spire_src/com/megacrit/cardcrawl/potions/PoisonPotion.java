package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.powers.PoisonPower;

public class PoisonPotion extends AbstractPotion {
   public static final String POTION_ID = "Poison Potion";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Poison Potion");

   public PoisonPotion() {
      super(potionStrings.NAME, "Poison Potion", AbstractPotion.PotionRarity.COMMON, AbstractPotion.PotionSize.M, AbstractPotion.PotionColor.POISON);
      this.labOutlineColor = Settings.GREEN_RELIC_COLOR;
      this.isThrown = true;
      this.targetRequired = true;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.tips.add(new PowerTip(TipHelper.capitalize(GameDictionary.POISON.NAMES[0]), GameDictionary.keywords.get(GameDictionary.POISON.NAMES[0])));
   }

   @Override
   public void use(AbstractCreature target) {
      this.addToBot(new ApplyPowerAction(target, AbstractDungeon.player, new PoisonPower(target, AbstractDungeon.player, this.potency), this.potency));
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 6;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new PoisonPotion();
   }
}
