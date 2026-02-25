package com.megacrit.cardcrawl.screens.mainMenu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

public class PatchNotesScreen implements ScrollBarListener {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("PatchNotesScreen");
   public static final String[] TEXT;
   private static String text = null;
   private FileHandle log;
   private static final float START_Y = Settings.HEIGHT - 300.0F * Settings.scale;
   private float scrollY = START_Y;
   private float targetY = this.scrollY;
   private float scrollLowerBound = Settings.HEIGHT - 300.0F * Settings.scale;
   private float scrollUpperBound = 2400.0F * Settings.scale;
   public MenuCancelButton button = new MenuCancelButton();
   private static final float LINE_WIDTH = 1200.0F * Settings.scale;
   private static final float LINE_SPACING = 30.0F * Settings.scale;
   private boolean grabbedScreen = false;
   private float grabStartY = 0.0F;
   private ScrollBar scrollBar = new ScrollBar(this);

   public void open() {
      this.button.show(TEXT[0]);
      this.targetY = this.scrollLowerBound;
      this.scrollY = Settings.HEIGHT - 400.0F * Settings.scale;
      CardCrawlGame.mainMenuScreen.darken();
      CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.PATCH_NOTES;
      if (text == null) {
         if (Settings.isBeta) {
            this.log = Gdx.files.internal("changelog" + File.separator + "notes.txt");
         } else {
            this.log = Gdx.files.internal("changelog" + File.separator + "notes_main.txt");
         }

         this.openLog();
         this.scrollUpperBound = this.calculateHeight() + 300.0F * Settings.scale;
         if (this.scrollUpperBound < this.scrollLowerBound) {
            this.scrollUpperBound = this.scrollLowerBound + 100.0F * Settings.scale;
         }
      } else {
         this.scrollY = START_Y;
         this.targetY = this.scrollY;
      }
   }

   private float calculateHeight() {
      return FontHelper.getHeight(FontHelper.tipBodyFont, text, 1.0F);
   }

   private void openLog() {
      try (BufferedReader br = new BufferedReader(this.log.reader())) {
         StringBuilder sb = new StringBuilder();
         String line = "";

         try {
            line = br.readLine();
         } catch (IOException var15) {
            var15.printStackTrace();
         }

         while (line != null) {
            sb.append(line);
            sb.append(System.lineSeparator());
            line = br.readLine();
         }

         text = sb.toString();
         br.close();
      } catch (IOException var18) {
         var18.printStackTrace();
      }
   }

   public void update() {
      if (Settings.isControllerMode) {
         if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
            this.targetY = this.targetY + Settings.SCROLL_SPEED * 2.0F;
         } else if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
            this.targetY = this.targetY - Settings.SCROLL_SPEED * 2.0F;
         } else if (CInputActionSet.drawPile.isJustPressed()) {
            this.targetY = this.targetY - Settings.SCROLL_SPEED * 10.0F;
         } else if (CInputActionSet.discardPile.isJustPressed()) {
            this.targetY = this.targetY + Settings.SCROLL_SPEED * 10.0F;
         }
      }

      this.button.update();
      if (this.button.hb.clicked || InputHelper.pressedEscape) {
         InputHelper.pressedEscape = false;
         CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.MAIN_MENU;
         this.button.hide();
         CardCrawlGame.mainMenuScreen.lighten();
      }

      boolean isDraggingScrollBar = this.scrollBar.update();
      if (!isDraggingScrollBar) {
         this.updateScrolling();
      }

      InputHelper.justClickedLeft = false;
   }

   private void updateScrolling() {
      int y = InputHelper.mY;
      if (!this.grabbedScreen) {
         if (InputHelper.scrolledDown) {
            this.targetY = this.targetY + Settings.SCROLL_SPEED;
         } else if (InputHelper.scrolledUp) {
            this.targetY = this.targetY - Settings.SCROLL_SPEED;
         }

         if (InputHelper.justClickedLeft) {
            this.grabbedScreen = true;
            this.grabStartY = y - this.targetY;
         }
      } else if (InputHelper.isMouseDown) {
         this.targetY = y - this.grabStartY;
      } else {
         this.grabbedScreen = false;
      }

      this.scrollY = MathHelper.scrollSnapLerpSpeed(this.scrollY, this.targetY);
      this.resetScrolling();
      this.updateBarPosition();
   }

   private void resetScrolling() {
      if (this.targetY < this.scrollLowerBound) {
         this.targetY = MathHelper.scrollSnapLerpSpeed(this.targetY, this.scrollLowerBound);
      } else if (this.targetY > this.scrollUpperBound) {
         this.targetY = MathHelper.scrollSnapLerpSpeed(this.targetY, this.scrollUpperBound);
      }
   }

   public void render(SpriteBatch sb) {
      FontHelper.renderSmartText(
         sb, FontHelper.panelNameFont, TEXT[1], 250.0F * Settings.scale, this.scrollY + 70.0F * Settings.scale, LINE_WIDTH, LINE_SPACING, Settings.CREAM_COLOR
      );
      FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, text, 300.0F * Settings.scale, this.scrollY, Settings.CREAM_COLOR);
      this.button.render(sb);
      this.scrollBar.render(sb);
   }

   @Override
   public void scrolledUsingBar(float newPercent) {
      this.scrollY = MathHelper.valueFromPercentBetween(this.scrollLowerBound, this.scrollUpperBound, newPercent);
      this.targetY = this.scrollY;
      this.updateBarPosition();
   }

   private void updateBarPosition() {
      float percent = MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound, this.scrollY);
      this.scrollBar.parentScrolledToPercent(percent);
   }

   static {
      TEXT = uiStrings.TEXT;
   }
}
