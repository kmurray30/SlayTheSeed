package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;

public class BlockPotion extends AbstractPotion {
   public static final String POTION_ID = "Block Potion";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Block Potion");

   public BlockPotion() {
      super(potionStrings.NAME, "Block Potion", AbstractPotion.PotionRarity.COMMON, AbstractPotion.PotionSize.S, AbstractPotion.PotionColor.BLUE);
      this.isThrown = false;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.tips.add(new PowerTip(TipHelper.capitalize(GameDictionary.BLOCK.NAMES[0]), GameDictionary.keywords.get(GameDictionary.BLOCK.NAMES[0])));
   }

   @Override
   public void use(AbstractCreature target) {
      this.addToBot(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, this.potency));
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 12;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new BlockPotion();
   }
}
