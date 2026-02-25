package com.megacrit.cardcrawl.screens.leaderboards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;

public class FilterButton {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("LeaderboardFilters");
   public static final String[] TEXT;
   public FilterButton.LeaderboardType lType = null;
   public FilterButton.RegionSetting rType = null;
   public boolean active = false;
   public AbstractPlayer.PlayerClass pClass = null;
   private Texture img;
   private static final int W = 128;
   public Hitbox hb = new Hitbox(100.0F * Settings.scale, 100.0F * Settings.scale);
   public String label;

   public FilterButton(String imgUrl, boolean active, AbstractPlayer.PlayerClass pClass, FilterButton.LeaderboardType lType, FilterButton.RegionSetting rType) {
      if (pClass != null) {
         switch (pClass) {
            case IRONCLAD:
               this.img = ImageMaster.FILTER_IRONCLAD;
               break;
            case THE_SILENT:
               this.img = ImageMaster.FILTER_SILENT;
               break;
            case DEFECT:
               this.img = ImageMaster.FILTER_DEFECT;
               break;
            case WATCHER:
               this.img = ImageMaster.FILTER_WATCHER;
               break;
            default:
               this.img = ImageMaster.FILTER_IRONCLAD;
         }
      } else if (lType != null) {
         switch (lType) {
            case CONSECUTIVE_WINS:
               this.img = ImageMaster.FILTER_CHAIN;
               break;
            case FASTEST_WIN:
               this.img = ImageMaster.FILTER_TIME;
               break;
            case HIGH_SCORE:
               this.img = ImageMaster.FILTER_SCORE;
               break;
            case SPIRE_LEVEL:
            case AVG_FLOOR:
            case AVG_SCORE:
            default:
               this.img = ImageMaster.FILTER_CHAIN;
         }
      } else if (rType != null) {
         switch (rType) {
            case FRIEND:
               this.img = ImageMaster.FILTER_FRIENDS;
               break;
            case GLOBAL:
            default:
               this.img = ImageMaster.FILTER_GLOBAL;
         }
      }

      this.lType = lType;
      this.rType = rType;
      this.active = active;
      this.pClass = pClass;
   }

   public FilterButton(String imgUrl, boolean active, AbstractPlayer.PlayerClass pClass) {
      this(imgUrl, active, pClass, null, null);
      switch (pClass) {
         case IRONCLAD:
            this.label = TEXT[0];
            break;
         case THE_SILENT:
            this.label = TEXT[1];
            break;
         case DEFECT:
            this.label = TEXT[2];
            break;
         case WATCHER:
            this.label = TEXT[11];
            break;
         default:
            this.label = TEXT[0];
      }

      if (active) {
         CardCrawlGame.mainMenuScreen.leaderboardsScreen.charLabel = LeaderboardScreen.TEXT[2] + ":  " + this.label;
      }
   }

   public FilterButton(String imgUrl, boolean active, FilterButton.LeaderboardType lType) {
      this(imgUrl, active, null, lType, null);
      switch (lType) {
         case CONSECUTIVE_WINS:
            this.label = TEXT[5];
            break;
         case FASTEST_WIN:
            this.label = TEXT[6];
            break;
         case HIGH_SCORE:
            this.label = TEXT[7];
            break;
         case SPIRE_LEVEL:
            this.label = TEXT[8];
            break;
         case AVG_FLOOR:
            this.label = TEXT[3];
            break;
         case AVG_SCORE:
            this.label = TEXT[4];
            break;
         default:
            this.label = TEXT[7];
      }

      if (active) {
         CardCrawlGame.mainMenuScreen.leaderboardsScreen.typeLabel = LeaderboardScreen.TEXT[4] + ":  " + this.label;
      }
   }

   public FilterButton(String imgUrl, boolean active, FilterButton.RegionSetting rType) {
      this(imgUrl, active, null, null, rType);
      switch (rType) {
         case FRIEND:
            this.label = TEXT[9];
            break;
         case GLOBAL:
            this.label = TEXT[10];
            break;
         default:
            this.label = TEXT[9];
      }

      if (active) {
         CardCrawlGame.mainMenuScreen.leaderboardsScreen.regionLabel = LeaderboardScreen.TEXT[3] + ":  " + this.label;
      }
   }

   public void update() {
      this.hb.update();
      if (this.hb.justHovered && !this.active) {
         CardCrawlGame.sound.play("UI_HOVER");
      }

      if (Settings.isControllerMode) {
         if (!this.active && this.hb.hovered && CInputActionSet.select.isJustPressed()) {
            CInputActionSet.select.unpress();
            this.hb.clicked = true;
         }
      } else if (!this.active && this.hb.hovered && InputHelper.justClickedLeft && !CardCrawlGame.mainMenuScreen.leaderboardsScreen.waiting) {
         CardCrawlGame.sound.playA("UI_CLICK_1", -0.4F);
         this.hb.clickStarted = true;
      }

      if (this.hb.clicked) {
         this.hb.clicked = false;
         if (!this.active) {
            this.toggle(true);
         }
      }
   }

   private void toggle(boolean refresh) {
      this.active = true;
      CardCrawlGame.mainMenuScreen.leaderboardsScreen.refresh = true;
      if (this.pClass != null) {
         for (FilterButton b : CardCrawlGame.mainMenuScreen.leaderboardsScreen.charButtons) {
            if (b != this) {
               b.active = false;
            }
         }

         CardCrawlGame.mainMenuScreen.leaderboardsScreen.charLabel = LeaderboardScreen.TEXT[2] + ":  " + this.label;
      } else if (this.rType != null) {
         for (FilterButton bx : CardCrawlGame.mainMenuScreen.leaderboardsScreen.regionButtons) {
            if (bx != this) {
               bx.active = false;
            }
         }

         CardCrawlGame.mainMenuScreen.leaderboardsScreen.regionLabel = LeaderboardScreen.TEXT[3] + ":  " + this.label;
      } else if (this.lType != null) {
         for (FilterButton bxx : CardCrawlGame.mainMenuScreen.leaderboardsScreen.typeButtons) {
            if (bxx != this) {
               bxx.active = false;
            }
         }

         CardCrawlGame.mainMenuScreen.leaderboardsScreen.typeLabel = LeaderboardScreen.TEXT[4] + ":  " + this.label;
      }
   }

   public void render(SpriteBatch sb, float x, float y) {
      if (this.active) {
         sb.setColor(new Color(1.0F, 0.8F, 0.2F, 0.5F + (MathUtils.cosDeg((float)(System.currentTimeMillis() / 4L % 360L)) + 1.25F) / 5.0F));
         sb.draw(
            ImageMaster.FILTER_GLOW_BG, x - 64.0F, y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false
         );
      }

      if (!this.hb.hovered && !this.active) {
         sb.setColor(Color.GRAY);
         sb.draw(this.img, x - 64.0F, y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
      } else {
         sb.setColor(Color.WHITE);
         sb.draw(this.img, x - 64.0F, y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
      }

      if (this.hb.hovered) {
         sb.setBlendFunction(770, 1);
         sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.25F));
         sb.draw(this.img, x - 64.0F, y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
         sb.setBlendFunction(770, 771);
      }

      this.hb.move(x, y);
      this.hb.render(sb);
   }

   static {
      TEXT = uiStrings.TEXT;
   }

   public static enum LeaderboardType {
      HIGH_SCORE,
      FASTEST_WIN,
      CONSECUTIVE_WINS,
      AVG_FLOOR,
      AVG_SCORE,
      SPIRE_LEVEL;
   }

   public static enum RegionSetting {
      GLOBAL,
      FRIEND;
   }
}
