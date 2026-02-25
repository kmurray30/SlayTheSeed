package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.BlightStrings;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class Accursed extends AbstractBlight {
   public static final String ID = "Accursed";
   private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("Accursed");
   public static final String NAME;
   public static final String[] DESC = blightStrings.DESCRIPTION;

   public Accursed() {
      super("Accursed", NAME, DESC[0] + 2 + DESC[1], "accursed.png", false);
      this.counter = 2;
   }

   @Override
   public void stack() {
      this.counter += 2;
      this.updateDescription();
      this.flash();
   }

   @Override
   public void updateDescription() {
      this.description = DESC[0] + this.counter + DESC[1];
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.initializeTips();
   }

   @Override
   public void onBossDefeat() {
      this.flash();
      Random posRandom = new Random();

      for (int i = 0; i < this.counter; i++) {
         AbstractDungeon.topLevelEffects
            .add(
               new ShowCardAndObtainEffect(
                  AbstractDungeon.getCardWithoutRng(AbstractCard.CardRarity.CURSE),
                  Settings.WIDTH / 2.0F + posRandom.random(-(Settings.WIDTH / 3.0F), Settings.WIDTH / 3.0F),
                  Settings.HEIGHT / 2.0F + posRandom.random(-(Settings.HEIGHT / 3.0F), Settings.HEIGHT / 3.0F)
               )
            );
      }
   }

   static {
      NAME = blightStrings.NAME;
   }
}
