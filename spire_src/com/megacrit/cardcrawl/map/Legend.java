package com.megacrit.cardcrawl.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.localization.UIStrings;
import java.util.ArrayList;

public class Legend {
   public static final float X = 1670.0F * Settings.xScale;
   public static final float Y = 600.0F * Settings.yScale;
   private static final int LW = 512;
   private static final int LH = 800;
   public Color c = new Color(1.0F, 1.0F, 1.0F, 0.0F);
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("Legend");
   public static final String[] TEXT;
   public ArrayList<LegendItem> items = new ArrayList<>();
   public boolean isLegendHighlighted = false;
   private static Texture img = null;

   public Legend() {
      this.items.add(new LegendItem(TEXT[0], ImageMaster.MAP_NODE_EVENT, TEXT[1], TEXT[2], 0));
      this.items.add(new LegendItem(TEXT[3], ImageMaster.MAP_NODE_MERCHANT, TEXT[4], TEXT[5], 1));
      this.items.add(new LegendItem(TEXT[6], ImageMaster.MAP_NODE_TREASURE, TEXT[7], TEXT[8], 2));
      this.items.add(new LegendItem(TEXT[9], ImageMaster.MAP_NODE_REST, TEXT[10], TEXT[11], 3));
      this.items.add(new LegendItem(TEXT[12], ImageMaster.MAP_NODE_ENEMY, TEXT[13], TEXT[14], 4));
      this.items.add(new LegendItem(TEXT[15], ImageMaster.MAP_NODE_ELITE, TEXT[16], TEXT[17], 5));
      if (img == null) {
         img = ImageMaster.loadImage("images/ui/map/selectBox.png");
      }
   }

   public boolean isIconHovered(String nodeHovered) {
      switch (nodeHovered) {
         case "?":
            if (this.items.get(0).hb.hovered) {
               return true;
            }
            break;
         case "$":
            if (this.items.get(1).hb.hovered) {
               return true;
            }
            break;
         case "T":
            if (this.items.get(2).hb.hovered) {
               return true;
            }
            break;
         case "R":
            if (this.items.get(3).hb.hovered) {
               return true;
            }
            break;
         case "M":
            if (this.items.get(4).hb.hovered) {
               return true;
            }
            break;
         case "E":
            if (this.items.get(5).hb.hovered) {
               return true;
            }
            break;
         default:
            return false;
      }

      return false;
   }

   public void update(float mapAlpha, boolean isMapScreen) {
      if (mapAlpha >= 0.8F && isMapScreen) {
         this.updateControllerInput();
         this.c.a = MathHelper.fadeLerpSnap(this.c.a, 1.0F);

         for (LegendItem i : this.items) {
            i.update();
         }
      } else {
         this.c.a = MathHelper.fadeLerpSnap(this.c.a, 0.0F);
      }
   }

   private void updateControllerInput() {
      if (Settings.isControllerMode) {
         if (this.isLegendHighlighted) {
            if (CInputActionSet.proceed.isJustPressed()
               || CInputActionSet.cancel.isJustPressed()
               || CInputActionSet.left.isJustPressed()
               || CInputActionSet.altLeft.isJustPressed()) {
               CInputActionSet.cancel.unpress();
               this.isLegendHighlighted = false;
               return;
            }
         } else if (CInputActionSet.proceed.isJustPressed()) {
            this.isLegendHighlighted = true;
            return;
         }

         if (this.isLegendHighlighted) {
            boolean anyHovered = false;
            int index = 0;

            for (LegendItem i : this.items) {
               if (i.hb.hovered) {
                  anyHovered = true;
                  break;
               }

               index++;
            }

            if (!anyHovered) {
               Gdx.input.setCursorPosition((int)this.items.get(0).hb.cX, Settings.HEIGHT - (int)this.items.get(0).hb.cY);
            } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
               if (++index > this.items.size() - 1) {
                  index = 0;
               }

               Gdx.input.setCursorPosition((int)this.items.get(index).hb.cX, Settings.HEIGHT - (int)this.items.get(index).hb.cY);
            } else if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
               if (--index < 0) {
                  index = this.items.size() - 1;
               }

               Gdx.input.setCursorPosition((int)this.items.get(index).hb.cX, Settings.HEIGHT - (int)this.items.get(index).hb.cY);
            }
         }
      }
   }

   public void render(SpriteBatch sb) {
      sb.setColor(this.c);
      if (!Settings.isMobile) {
         sb.draw(
            ImageMaster.MAP_LEGEND, X - 256.0F, Y - 400.0F, 256.0F, 400.0F, 512.0F, 800.0F, Settings.scale, Settings.yScale, 0.0F, 0, 0, 512, 800, false, false
         );
      } else {
         sb.draw(
            ImageMaster.MAP_LEGEND,
            X - 256.0F,
            Y - 400.0F,
            256.0F,
            400.0F,
            512.0F,
            800.0F,
            Settings.scale * 1.1F,
            Settings.yScale * 1.1F,
            0.0F,
            0,
            0,
            512,
            800,
            false,
            false
         );
      }

      Color c2 = new Color(MapRoomNode.AVAILABLE_COLOR.r, MapRoomNode.AVAILABLE_COLOR.g, MapRoomNode.AVAILABLE_COLOR.b, this.c.a);
      if (Settings.isMobile) {
         FontHelper.renderFontCentered(sb, FontHelper.menuBannerFont, TEXT[18], X, Y + 190.0F * Settings.yScale, c2, 1.4F);
      } else {
         FontHelper.renderFontCentered(sb, FontHelper.menuBannerFont, TEXT[18], X, Y + 170.0F * Settings.yScale, c2);
      }

      sb.setColor(c2);

      for (LegendItem i : this.items) {
         i.render(sb, c2);
      }

      if (Settings.isControllerMode) {
         sb.setColor(new Color(1.0F, 1.0F, 1.0F, c2.a));
         sb.draw(
            CInputActionSet.proceed.getKeyImg(),
            1570.0F * Settings.xScale - 32.0F,
            Y + 170.0F * Settings.yScale - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            Settings.scale,
            Settings.scale,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
         if (this.isLegendHighlighted) {
            sb.setColor(new Color(1.0F, 0.9F, 0.5F, 0.6F + MathUtils.cosDeg((float)(System.currentTimeMillis() / 2L % 360L)) / 5.0F));
            float doop = 1.0F + (1.0F + MathUtils.cosDeg((float)(System.currentTimeMillis() / 2L % 360L))) / 50.0F;
            sb.draw(
               img,
               1670.0F * Settings.scale - 160.0F,
               Settings.HEIGHT - Gdx.input.getY() - 52.0F + 4.0F * Settings.scale,
               160.0F,
               52.0F,
               320.0F,
               104.0F,
               Settings.scale * doop,
               Settings.scale * doop,
               0.0F,
               0,
               0,
               320,
               104,
               false,
               false
            );
         }
      }
   }

   static {
      TEXT = uiStrings.TEXT;
   }
}
