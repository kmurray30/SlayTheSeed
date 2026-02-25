package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.powers.DexterityPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class DexterityPotion extends AbstractPotion {
   public static final String POTION_ID = "Dexterity Potion";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Dexterity Potion");

   public DexterityPotion() {
      super(potionStrings.NAME, "Dexterity Potion", AbstractPotion.PotionRarity.COMMON, AbstractPotion.PotionSize.S, AbstractPotion.PotionColor.GREEN);
      this.isThrown = false;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.tips.add(new PowerTip(TipHelper.capitalize(GameDictionary.DEXTERITY.NAMES[0]), GameDictionary.keywords.get(GameDictionary.DEXTERITY.NAMES[0])));
   }

   @Override
   public void use(AbstractCreature target) {
      AbstractCreature var2 = AbstractDungeon.player;
      AbstractDungeon.effectList.add(new FlashAtkImgEffect(var2.hb.cX, var2.hb.cY, AbstractGameAction.AttackEffect.SHIELD));
      this.addToBot(new ApplyPowerAction(var2, AbstractDungeon.player, new DexterityPower(var2, this.potency), this.potency));
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 2;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new DexterityPotion();
   }
}
