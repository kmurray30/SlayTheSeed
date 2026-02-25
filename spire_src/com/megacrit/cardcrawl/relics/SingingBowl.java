package com.megacrit.cardcrawl.relics;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class SingingBowl extends AbstractRelic {
   public static final String ID = "Singing Bowl";
   public static final int HP_AMT = 2;

   public SingingBowl() {
      super("Singing Bowl", "singingBowl.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void update() {
      super.update();
      if (this.hb.hovered && InputHelper.justClickedLeft) {
         CardCrawlGame.sound.playA("SINGING_BOWL", MathUtils.random(-0.2F, 0.1F));
         this.flash();
      }
   }

   @Override
   public boolean canSpawn() {
      return Settings.isEndless || AbstractDungeon.floorNum <= 48;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new SingingBowl();
   }
}
