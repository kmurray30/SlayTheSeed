package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.ui.DialogWord;

public class GameSavedEffect extends AbstractGameEffect {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("GameSavedEffect");
   public static final String[] TEXT;

   @Override
   public void update() {
      if (ModHelper.enabledMods.size() > 0) {
         if (ModHelper.enabledMods.size() > 3) {
            AbstractDungeon.topLevelEffects
               .add(new SpeechTextEffect(1600.0F * Settings.scale, Settings.HEIGHT - 74.0F * Settings.scale, 2.0F, TEXT[0], DialogWord.AppearEffect.FADE_IN));
         } else {
            AbstractDungeon.topLevelEffects
               .add(new SpeechTextEffect(1600.0F * Settings.scale, Settings.HEIGHT - 26.0F * Settings.scale, 2.0F, TEXT[0], DialogWord.AppearEffect.FADE_IN));
         }
      } else {
         AbstractDungeon.topLevelEffects
            .add(new SpeechTextEffect(1450.0F * Settings.scale, Settings.HEIGHT - 26.0F * Settings.scale, 2.0F, TEXT[0], DialogWord.AppearEffect.FADE_IN));
      }

      this.isDone = true;
   }

   @Override
   public void render(SpriteBatch sb) {
   }

   @Override
   public void dispose() {
   }

   static {
      TEXT = uiStrings.TEXT;
   }
}
