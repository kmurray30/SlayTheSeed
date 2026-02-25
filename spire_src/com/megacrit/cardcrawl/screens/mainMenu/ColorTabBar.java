package com.megacrit.cardcrawl.screens.mainMenu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.SingleCardViewPopup;
import com.megacrit.cardcrawl.screens.compendium.CardLibraryScreen;

public class ColorTabBar {
   private static final float TAB_SPACING = 198.0F * Settings.xScale;
   private static final int BAR_W = 1334;
   private static final int BAR_H = 102;
   private static final int TAB_W = 274;
   private static final int TAB_H = 68;
   private static final int TICKBOX_W = 48;
   public Hitbox redHb;
   public Hitbox greenHb;
   public Hitbox blueHb;
   public Hitbox purpleHb;
   public Hitbox colorlessHb;
   public Hitbox curseHb;
   public Hitbox viewUpgradeHb;
   public ColorTabBar.CurrentTab curTab = ColorTabBar.CurrentTab.RED;
   private TabBarListener delegate;

   public ColorTabBar(TabBarListener delegate) {
      float w = 200.0F * Settings.scale;
      float h = 50.0F * Settings.scale;
      this.redHb = new Hitbox(w, h);
      this.greenHb = new Hitbox(w, h);
      this.blueHb = new Hitbox(w, h);
      this.purpleHb = new Hitbox(w, h);
      this.colorlessHb = new Hitbox(w, h);
      this.curseHb = new Hitbox(w, h);
      this.delegate = delegate;
      this.viewUpgradeHb = new Hitbox(360.0F * Settings.scale, 48.0F * Settings.scale);
   }

   public void update(float y) {
      float x = 470.0F * Settings.xScale;
      this.redHb.move(x, y + 50.0F * Settings.scale);
      float var4;
      this.greenHb.move(var4 = x + TAB_SPACING, y + 50.0F * Settings.scale);
      this.blueHb.move(x = var4 + TAB_SPACING, y + 50.0F * Settings.scale);
      float var6;
      this.purpleHb.move(var6 = x + TAB_SPACING, y + 50.0F * Settings.scale);
      this.colorlessHb.move(x = var6 + TAB_SPACING, y + 50.0F * Settings.scale);
      float var8;
      this.curseHb.move(var8 = x + TAB_SPACING, y + 50.0F * Settings.scale);
      this.viewUpgradeHb.move(1410.0F * Settings.xScale, y);
      this.redHb.update();
      this.greenHb.update();
      this.blueHb.update();
      this.purpleHb.update();
      this.colorlessHb.update();
      this.curseHb.update();
      this.viewUpgradeHb.update();
      if (this.redHb.justHovered
         || this.greenHb.justHovered
         || this.blueHb.justHovered
         || this.colorlessHb.justHovered
         || this.curseHb.justHovered
         || this.purpleHb.justHovered) {
         CardCrawlGame.sound.playA("UI_HOVER", -0.4F);
      }

      if (Settings.isControllerMode) {
         if (CInputActionSet.pageRightViewExhaust.isJustPressed()) {
            if (this.curTab == ColorTabBar.CurrentTab.RED) {
               this.curTab = ColorTabBar.CurrentTab.GREEN;
            } else if (this.curTab == ColorTabBar.CurrentTab.GREEN) {
               this.curTab = ColorTabBar.CurrentTab.BLUE;
            } else if (this.curTab == ColorTabBar.CurrentTab.BLUE) {
               this.curTab = ColorTabBar.CurrentTab.PURPLE;
            } else if (this.curTab == ColorTabBar.CurrentTab.PURPLE) {
               this.curTab = ColorTabBar.CurrentTab.COLORLESS;
            } else if (this.curTab == ColorTabBar.CurrentTab.COLORLESS) {
               this.curTab = ColorTabBar.CurrentTab.CURSE;
            } else if (this.curTab == ColorTabBar.CurrentTab.CURSE) {
               this.curTab = ColorTabBar.CurrentTab.RED;
            }

            this.delegate.didChangeTab(this, this.curTab);
         } else if (CInputActionSet.pageLeftViewDeck.isJustPressed()) {
            if (this.curTab == ColorTabBar.CurrentTab.RED) {
               this.curTab = ColorTabBar.CurrentTab.CURSE;
            } else if (this.curTab == ColorTabBar.CurrentTab.GREEN) {
               this.curTab = ColorTabBar.CurrentTab.RED;
            } else if (this.curTab == ColorTabBar.CurrentTab.BLUE) {
               this.curTab = ColorTabBar.CurrentTab.GREEN;
            } else if (this.curTab == ColorTabBar.CurrentTab.PURPLE) {
               this.curTab = ColorTabBar.CurrentTab.BLUE;
            } else if (this.curTab == ColorTabBar.CurrentTab.COLORLESS) {
               this.curTab = ColorTabBar.CurrentTab.PURPLE;
            } else if (this.curTab == ColorTabBar.CurrentTab.CURSE) {
               this.curTab = ColorTabBar.CurrentTab.COLORLESS;
            }

            this.delegate.didChangeTab(this, this.curTab);
         }
      }

      if (InputHelper.justClickedLeft) {
         ColorTabBar.CurrentTab oldTab = this.curTab;
         if (this.redHb.hovered) {
            this.curTab = ColorTabBar.CurrentTab.RED;
         } else if (this.greenHb.hovered) {
            this.curTab = ColorTabBar.CurrentTab.GREEN;
         } else if (this.blueHb.hovered) {
            this.curTab = ColorTabBar.CurrentTab.BLUE;
         } else if (this.purpleHb.hovered) {
            this.curTab = ColorTabBar.CurrentTab.PURPLE;
         } else if (this.colorlessHb.hovered) {
            this.curTab = ColorTabBar.CurrentTab.COLORLESS;
         } else if (this.curseHb.hovered) {
            this.curTab = ColorTabBar.CurrentTab.CURSE;
         }

         if (oldTab != this.curTab) {
            this.delegate.didChangeTab(this, this.curTab);
         }
      }

      if (this.viewUpgradeHb.justHovered) {
         CardCrawlGame.sound.playA("UI_HOVER", -0.3F);
      }

      if (this.viewUpgradeHb.hovered && InputHelper.justClickedLeft) {
         this.viewUpgradeHb.clickStarted = true;
      }

      if (this.viewUpgradeHb.clicked || this.viewUpgradeHb.hovered && CInputActionSet.select.isJustPressed()) {
         this.viewUpgradeHb.clicked = false;
         CardCrawlGame.sound.playA("UI_CLICK_1", -0.2F);
         SingleCardViewPopup.isViewingUpgrade = !SingleCardViewPopup.isViewingUpgrade;
      }
   }

   public Hitbox hoveredHb() {
      if (this.redHb.hovered) {
         return this.redHb;
      } else if (this.greenHb.hovered) {
         return this.greenHb;
      } else if (this.blueHb.hovered) {
         return this.blueHb;
      } else if (this.purpleHb.hovered) {
         return this.purpleHb;
      } else if (this.colorlessHb.hovered) {
         return this.colorlessHb;
      } else if (this.curseHb.hovered) {
         return this.curseHb;
      } else {
         return this.viewUpgradeHb.hovered ? this.viewUpgradeHb : null;
      }
   }

   public Color getBarColor() {
      switch (this.curTab) {
         case RED:
            return new Color(0.5F, 0.1F, 0.1F, 1.0F);
         case GREEN:
            return new Color(0.25F, 0.55F, 0.0F, 1.0F);
         case BLUE:
            return new Color(0.01F, 0.34F, 0.52F, 1.0F);
         case PURPLE:
            return new Color(0.37F, 0.22F, 0.49F, 1.0F);
         case COLORLESS:
            return new Color(0.4F, 0.4F, 0.4F, 1.0F);
         case CURSE:
            return new Color(0.18F, 0.18F, 0.16F, 1.0F);
         default:
            return Color.WHITE;
      }
   }

   public void render(SpriteBatch sb, float y) {
      sb.setColor(Color.GRAY);
      if (this.curTab != ColorTabBar.CurrentTab.CURSE) {
         this.renderTab(sb, ImageMaster.COLOR_TAB_CURSE, this.curseHb.cX, y, CardLibraryScreen.TEXT[5], true);
      }

      if (this.curTab != ColorTabBar.CurrentTab.COLORLESS) {
         this.renderTab(sb, ImageMaster.COLOR_TAB_COLORLESS, this.colorlessHb.cX, y, CardLibraryScreen.TEXT[4], true);
      }

      if (this.curTab != ColorTabBar.CurrentTab.BLUE) {
         this.renderTab(sb, ImageMaster.COLOR_TAB_BLUE, this.blueHb.cX, y, CardLibraryScreen.TEXT[3], true);
      }

      if (this.curTab != ColorTabBar.CurrentTab.PURPLE) {
         this.renderTab(sb, ImageMaster.COLOR_TAB_PURPLE, this.purpleHb.cX, y, CardLibraryScreen.TEXT[8], true);
      }

      if (this.curTab != ColorTabBar.CurrentTab.GREEN) {
         this.renderTab(sb, ImageMaster.COLOR_TAB_GREEN, this.greenHb.cX, y, CardLibraryScreen.TEXT[2], true);
      }

      if (this.curTab != ColorTabBar.CurrentTab.RED) {
         this.renderTab(sb, ImageMaster.COLOR_TAB_RED, this.redHb.cX, y, CardLibraryScreen.TEXT[1], true);
      }

      sb.setColor(this.getBarColor());
      sb.draw(
         ImageMaster.COLOR_TAB_BAR,
         Settings.WIDTH / 2.0F - 667.0F,
         y - 51.0F,
         667.0F,
         51.0F,
         1334.0F,
         102.0F,
         Settings.xScale,
         Settings.scale,
         0.0F,
         0,
         0,
         1334,
         102,
         false,
         false
      );
      sb.setColor(Color.WHITE);
      switch (this.curTab) {
         case RED:
            this.renderTab(sb, ImageMaster.COLOR_TAB_RED, this.redHb.cX, y, CardLibraryScreen.TEXT[1], false);
            break;
         case GREEN:
            this.renderTab(sb, ImageMaster.COLOR_TAB_GREEN, this.greenHb.cX, y, CardLibraryScreen.TEXT[2], false);
            break;
         case BLUE:
            this.renderTab(sb, ImageMaster.COLOR_TAB_BLUE, this.blueHb.cX, y, CardLibraryScreen.TEXT[3], false);
            break;
         case PURPLE:
            this.renderTab(sb, ImageMaster.COLOR_TAB_PURPLE, this.purpleHb.cX, y, CardLibraryScreen.TEXT[8], false);
            break;
         case COLORLESS:
            this.renderTab(sb, ImageMaster.COLOR_TAB_COLORLESS, this.colorlessHb.cX, y, CardLibraryScreen.TEXT[4], false);
            break;
         case CURSE:
            this.renderTab(sb, ImageMaster.COLOR_TAB_CURSE, this.curseHb.cX, y, CardLibraryScreen.TEXT[5], false);
      }

      this.renderViewUpgrade(sb, y);
      this.redHb.render(sb);
      this.greenHb.render(sb);
      this.blueHb.render(sb);
      this.purpleHb.render(sb);
      this.colorlessHb.render(sb);
      this.curseHb.render(sb);
      this.viewUpgradeHb.render(sb);
   }

   private void renderTab(SpriteBatch sb, Texture img, float x, float y, String label, boolean selected) {
      sb.draw(
         img, x - 137.0F, y - 34.0F + 53.0F * Settings.scale, 137.0F, 34.0F, 274.0F, 68.0F, Settings.xScale, Settings.scale, 0.0F, 0, 0, 274, 68, false, false
      );
      Color c = Settings.GOLD_COLOR;
      if (selected) {
         c = Color.GRAY;
      }

      FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, label, x, y + 50.0F * Settings.scale, c, 0.9F);
   }

   private void renderViewUpgrade(SpriteBatch sb, float y) {
      Color c = Settings.CREAM_COLOR;
      if (this.viewUpgradeHb.hovered) {
         c = Settings.GOLD_COLOR;
      }

      FontHelper.renderFontRightAligned(sb, FontHelper.topPanelInfoFont, CardLibraryScreen.TEXT[7], 1546.0F * Settings.xScale, y, c);
      Texture img = SingleCardViewPopup.isViewingUpgrade ? ImageMaster.COLOR_TAB_BOX_TICKED : ImageMaster.COLOR_TAB_BOX_UNTICKED;
      sb.setColor(c);
      sb.draw(
         img,
         1532.0F * Settings.xScale - FontHelper.getSmartWidth(FontHelper.topPanelInfoFont, CardLibraryScreen.TEXT[7], 9999.0F, 0.0F) - 24.0F,
         y - 24.0F,
         24.0F,
         24.0F,
         48.0F,
         48.0F,
         Settings.scale,
         Settings.scale,
         0.0F,
         0,
         0,
         48,
         48,
         false,
         false
      );
   }

   public static enum CurrentTab {
      RED,
      GREEN,
      BLUE,
      PURPLE,
      COLORLESS,
      CURSE;
   }
}
