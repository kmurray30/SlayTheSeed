package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class DuVuDoll extends AbstractRelic {
   public static final String ID = "Du-Vu Doll";
   private static final int AMT = 1;

   public DuVuDoll() {
      super("Du-Vu Doll", "duvuDoll.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1];
   }

   @Override
   public void setCounter(int c) {
      this.counter = c;
      if (this.counter == 0) {
         this.description = this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[2];
      } else {
         this.description = this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[3] + this.counter + this.DESCRIPTIONS[4];
      }

      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.initializeTips();
   }

   @Override
   public void onMasterDeckChange() {
      this.counter = 0;

      for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
         if (c.type == AbstractCard.CardType.CURSE) {
            this.counter++;
         }
      }

      if (this.counter == 0) {
         this.description = this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[2];
      } else {
         this.description = this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[3] + this.counter + this.DESCRIPTIONS[4];
      }

      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.initializeTips();
   }

   @Override
   public void onEquip() {
      this.counter = 0;

      for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
         if (c.type == AbstractCard.CardType.CURSE) {
            this.counter++;
         }
      }

      if (this.counter == 0) {
         this.description = this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[2];
      } else {
         this.description = this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1] + this.DESCRIPTIONS[3] + this.counter + this.DESCRIPTIONS[4];
      }

      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.initializeTips();
   }

   @Override
   public void atBattleStart() {
      if (this.counter > 0) {
         this.flash();
         this.addToTop(
            new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new StrengthPower(AbstractDungeon.player, this.counter), this.counter)
         );
         this.addToTop(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new DuVuDoll();
   }
}
