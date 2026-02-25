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
import com.megacrit.cardcrawl.powers.IntangiblePlayerPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class GhostInAJar extends AbstractPotion {
   public static final String POTION_ID = "GhostInAJar";
   private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("GhostInAJar");

   public GhostInAJar() {
      super(potionStrings.NAME, "GhostInAJar", AbstractPotion.PotionRarity.RARE, AbstractPotion.PotionSize.GHOST, AbstractPotion.PotionColor.WHITE);
      this.labOutlineColor = Settings.GREEN_RELIC_COLOR;
      this.isThrown = false;
   }

   @Override
   public void initializeData() {
      this.potency = this.getPotency();
      this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.tips.add(new PowerTip(TipHelper.capitalize(GameDictionary.INTANGIBLE.NAMES[0]), GameDictionary.keywords.get(GameDictionary.INTANGIBLE.NAMES[0])));
   }

   @Override
   public void use(AbstractCreature target) {
      AbstractCreature var2 = AbstractDungeon.player;
      if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
         this.addToBot(new ApplyPowerAction(var2, AbstractDungeon.player, new IntangiblePlayerPower(var2, this.potency), this.potency));
      }
   }

   @Override
   public int getPotency(int ascensionLevel) {
      return 1;
   }

   @Override
   public AbstractPotion makeCopy() {
      return new GhostInAJar();
   }
}
