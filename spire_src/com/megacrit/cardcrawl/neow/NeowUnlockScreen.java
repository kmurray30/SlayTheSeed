package com.megacrit.cardcrawl.neow;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.ui.buttons.UnlockConfirmButton;
import com.megacrit.cardcrawl.unlock.AbstractUnlock;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.ConeEffect;
import com.megacrit.cardcrawl.vfx.RoomShineEffect;
import com.megacrit.cardcrawl.vfx.RoomShineEffect2;
import java.util.ArrayList;
import java.util.Iterator;

public class NeowUnlockScreen {
   public ArrayList<AbstractUnlock> unlockBundle;
   private ArrayList<ConeEffect> cones = new ArrayList<>();
   private static final int CONE_AMT = 30;
   private float shinyTimer = 0.0F;
   private static final float SHINY_INTERVAL = 0.2F;
   public UnlockConfirmButton button = new UnlockConfirmButton();
   public long id;
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("UnlockScreen");
   public static final String[] TEXT;

   public void open(ArrayList<AbstractUnlock> unlock, boolean isVictory) {
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NEOW_UNLOCK;
      this.unlockBundle = unlock;
      this.button.show();
      this.id = CardCrawlGame.sound.play("UNLOCK_SCREEN");
      this.cones.clear();

      for (int i = 0; i < 30; i++) {
         this.cones.add(new ConeEffect());
      }

      switch (this.unlockBundle.get(0).type) {
         case CARD:
            for (int i = 0; i < this.unlockBundle.size(); i++) {
               UnlockTracker.unlockCard(this.unlockBundle.get(i).card.cardID);
               AbstractDungeon.dynamicBanner.appearInstantly(TEXT[0]);
               this.unlockBundle.get(i).card.targetDrawScale = 1.0F;
               this.unlockBundle.get(i).card.drawScale = 0.01F;
               float var7 = Settings.WIDTH;
               this.unlockBundle.get(i).card.current_x = var7 * (0.25F * (i + 1));
               var7 = Settings.HEIGHT;
               this.unlockBundle.get(i).card.current_y = var7 / 2.0F;
               var7 = Settings.WIDTH;
               this.unlockBundle.get(i).card.target_x = var7 * (0.25F * (i + 1));
               this.unlockBundle.get(i).card.target_y = Settings.HEIGHT / 2.0F - 30.0F * Settings.scale;
            }
            break;
         case RELIC:
            for (int i = 0; i < this.unlockBundle.size(); i++) {
               UnlockTracker.hardUnlockOverride(this.unlockBundle.get(i).relic.relicId);
               UnlockTracker.markRelicAsSeen(this.unlockBundle.get(i).relic.relicId);
               this.unlockBundle.get(i).relic.loadLargeImg();
               AbstractDungeon.dynamicBanner.appearInstantly(TEXT[1]);
               float var10001 = Settings.WIDTH;
               this.unlockBundle.get(i).relic.currentX = var10001 * (0.25F * (i + 1));
               var10001 = Settings.HEIGHT;
               this.unlockBundle.get(i).relic.currentY = var10001 / 2.0F;
               this.unlockBundle.get(i).relic.hb.move(this.unlockBundle.get(i).relic.currentX, this.unlockBundle.get(i).relic.currentY);
            }
            break;
         case CHARACTER:
            this.unlockBundle.get(0).onUnlockScreenOpen();
            AbstractDungeon.dynamicBanner.appearInstantly(TEXT[2]);
         case MISC:
      }
   }

   public void reOpen() {
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NEOW_UNLOCK;
      this.button.show();
      this.id = CardCrawlGame.sound.play("UNLOCK_SCREEN");
      this.cones.clear();

      for (int i = 0; i < 30; i++) {
         this.cones.add(new ConeEffect());
      }

      switch (this.unlockBundle.get(0).type) {
         case CARD:
            for (int i = 0; i < this.unlockBundle.size(); i++) {
               UnlockTracker.unlockCard(this.unlockBundle.get(i).card.cardID);
               AbstractDungeon.dynamicBanner.appearInstantly(TEXT[0]);
               this.unlockBundle.get(i).card.targetDrawScale = 1.0F;
               this.unlockBundle.get(i).card.drawScale = 0.01F;
               float var5 = Settings.WIDTH;
               this.unlockBundle.get(i).card.current_x = var5 * (0.25F * (i + 1));
               var5 = Settings.HEIGHT;
               this.unlockBundle.get(i).card.current_y = var5 / 2.0F;
               var5 = Settings.WIDTH;
               this.unlockBundle.get(i).card.target_x = var5 * (0.25F * (i + 1));
               this.unlockBundle.get(i).card.target_y = Settings.HEIGHT / 2.0F - 30.0F * Settings.scale;
            }
            break;
         case RELIC:
            for (int i = 0; i < this.unlockBundle.size(); i++) {
               AbstractDungeon.dynamicBanner.appearInstantly(TEXT[1]);
               float var10001 = Settings.WIDTH;
               this.unlockBundle.get(i).relic.currentX = var10001 * (0.25F * (i + 1));
               var10001 = Settings.HEIGHT;
               this.unlockBundle.get(i).relic.currentY = var10001 / 2.0F;
               this.unlockBundle.get(i).relic.hb.move(this.unlockBundle.get(i).relic.currentX, this.unlockBundle.get(i).relic.currentY);
            }
            break;
         case CHARACTER:
            this.unlockBundle.get(0).onUnlockScreenOpen();
            AbstractDungeon.dynamicBanner.appearInstantly(TEXT[2]);
         case MISC:
      }
   }

   public void update() {
      this.shinyTimer = this.shinyTimer - Gdx.graphics.getDeltaTime();
      if (this.shinyTimer < 0.0F) {
         this.shinyTimer = 0.2F;
         AbstractDungeon.topLevelEffects.add(new RoomShineEffect());
         AbstractDungeon.topLevelEffects.add(new RoomShineEffect());
         AbstractDungeon.topLevelEffects.add(new RoomShineEffect2());
      }

      switch (this.unlockBundle.get(0).type) {
         case CARD:
            this.updateConeEffect();

            for (int i = 0; i < this.unlockBundle.size(); i++) {
               this.unlockBundle.get(i).card.update();
               this.unlockBundle.get(i).card.updateHoverLogic();
               this.unlockBundle.get(i).card.targetDrawScale = 1.0F;
            }
            break;
         case RELIC:
            this.updateConeEffect();

            for (int i = 0; i < this.unlockBundle.size(); i++) {
               this.unlockBundle.get(i).relic.update();
            }
            break;
         case CHARACTER:
            this.updateConeEffect();
            this.unlockBundle.get(0).player.update();
      }

      this.button.update();
   }

   private void updateConeEffect() {
      Iterator<ConeEffect> e = this.cones.iterator();

      while (e.hasNext()) {
         ConeEffect d = e.next();
         d.update();
         if (d.isDone) {
            e.remove();
         }
      }

      if (this.cones.size() < 30) {
         this.cones.add(new ConeEffect());
      }
   }

   public void render(SpriteBatch sb) {
      sb.setColor(new Color(0.05F, 0.15F, 0.18F, 1.0F));
      sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, (float)Settings.WIDTH, (float)Settings.HEIGHT);
      sb.setBlendFunction(770, 1);

      for (ConeEffect e : this.cones) {
         e.render(sb);
      }

      sb.setBlendFunction(770, 771);
      switch (this.unlockBundle.get(0).type) {
         case CARD:
            for (int i = 0; i < this.unlockBundle.size(); i++) {
               this.unlockBundle.get(i).card.renderHoverShadow(sb);
               this.unlockBundle.get(i).card.render(sb);
               this.unlockBundle.get(i).card.renderCardTip(sb);
            }

            sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.5F));
            sb.draw(
               ImageMaster.UNLOCK_TEXT_BG,
               Settings.WIDTH / 2.0F - 500.0F,
               Settings.HEIGHT / 2.0F - 330.0F * Settings.scale - 130.0F,
               500.0F,
               130.0F,
               1000.0F,
               260.0F,
               Settings.scale * 1.2F,
               Settings.scale * 0.8F,
               0.0F,
               0,
               0,
               1000,
               260,
               false,
               false
            );
            FontHelper.renderFontCentered(
               sb, FontHelper.panelNameFont, TEXT[3], Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F - 330.0F * Settings.scale, Settings.CREAM_COLOR
            );
            break;
         case RELIC:
            for (int i = 0; i < this.unlockBundle.size(); i++) {
               if (RelicLibrary.redList.contains(this.unlockBundle.get(i).relic)) {
                  this.unlockBundle.get(i).relic.render(sb, false, Settings.RED_RELIC_COLOR);
               } else if (RelicLibrary.greenList.contains(this.unlockBundle.get(i).relic)) {
                  this.unlockBundle.get(i).relic.render(sb, false, Settings.GREEN_RELIC_COLOR);
               } else if (RelicLibrary.blueList.contains(this.unlockBundle.get(i).relic)) {
                  this.unlockBundle.get(i).relic.render(sb, false, Settings.BLUE_RELIC_COLOR);
               } else if (RelicLibrary.whiteList.contains(this.unlockBundle.get(i).relic)) {
                  this.unlockBundle.get(i).relic.render(sb, false, Settings.PURPLE_RELIC_COLOR);
               } else {
                  this.unlockBundle.get(i).relic.render(sb, false, Settings.TWO_THIRDS_TRANSPARENT_BLACK_COLOR);
               }

               sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.5F));
               sb.draw(
                  ImageMaster.UNLOCK_TEXT_BG,
                  Settings.WIDTH / 2.0F - 500.0F,
                  Settings.HEIGHT / 2.0F - 330.0F * Settings.scale - 130.0F,
                  500.0F,
                  130.0F,
                  1000.0F,
                  260.0F,
                  Settings.scale * 1.2F,
                  Settings.scale * 0.8F,
                  0.0F,
                  0,
                  0,
                  1000,
                  260,
                  false,
                  false
               );
               float var10003 = Settings.WIDTH * (0.25F * (i + 1));
               float var10004 = Settings.HEIGHT / 2.0F - 150.0F * Settings.scale;
               FontHelper.renderFontCentered(sb, FontHelper.losePowerFont, this.unlockBundle.get(i).relic.name, var10003, var10004, Settings.GOLD_COLOR, 1.2F);
            }

            FontHelper.renderFontCentered(
               sb, FontHelper.panelNameFont, TEXT[3], Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F - 330.0F * Settings.scale, Settings.CREAM_COLOR
            );
            break;
         case CHARACTER:
            this.unlockBundle.get(0).render(sb);
            this.unlockBundle.get(0).player.renderPlayerImage(sb);
      }

      this.button.render(sb);
   }

   static {
      TEXT = uiStrings.TEXT;
   }
}
