package com.megacrit.cardcrawl.screens.compendium;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;
import java.util.ArrayList;

public class PotionViewScreen implements ScrollBarListener {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("PotionViewScreen");
   public static final String[] TEXT;
   private static final float SPACE = 80.0F * Settings.scale;
   private static final float START_X = 600.0F * Settings.scale;
   private float scrollY = Settings.HEIGHT;
   private float targetY = this.scrollY;
   private float scrollUpperBound = Settings.HEIGHT - 100.0F * Settings.scale;
   private float scrollLowerBound = Settings.HEIGHT / 2.0F;
   private int row = 0;
   private int col = 0;
   public MenuCancelButton button = new MenuCancelButton();
   private ArrayList<AbstractPotion> commonPotions = null;
   private ArrayList<AbstractPotion> uncommonPotions = null;
   private ArrayList<AbstractPotion> rarePotions = null;
   private boolean grabbedScreen = false;
   private float grabStartY = 0.0F;
   private ScrollBar scrollBar;
   private Hitbox controllerPotionHb = null;

   public PotionViewScreen() {
      this.scrollBar = new ScrollBar(this);
   }

   public void open() {
      this.controllerPotionHb = null;
      this.sortOnOpen();
      this.button.show(TEXT[0]);
      this.targetY = this.scrollUpperBound - 50.0F * Settings.scale;
      this.scrollY = Settings.HEIGHT;
      CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.POTION_VIEW;
   }

   private void sortOnOpen() {
      this.commonPotions = PotionHelper.getPotionsByRarity(AbstractPotion.PotionRarity.COMMON);
      this.uncommonPotions = PotionHelper.getPotionsByRarity(AbstractPotion.PotionRarity.UNCOMMON);
      this.rarePotions = PotionHelper.getPotionsByRarity(AbstractPotion.PotionRarity.RARE);
   }

   public void update() {
      this.updateControllerInput();
      if (Settings.isModded && Settings.isControllerMode && this.controllerPotionHb != null) {
         if (Gdx.input.getY() > Settings.HEIGHT * 0.7F) {
            this.targetY = this.targetY + Settings.SCROLL_SPEED;
            if (this.targetY > this.scrollUpperBound) {
               this.targetY = this.scrollUpperBound;
            }
         } else if (Gdx.input.getY() < Settings.HEIGHT * 0.3F) {
            this.targetY = this.targetY - Settings.SCROLL_SPEED;
            if (this.targetY < this.scrollLowerBound) {
               this.targetY = this.scrollLowerBound;
            }
         }
      }

      this.button.update();
      if (this.button.hb.clicked || InputHelper.pressedEscape) {
         InputHelper.pressedEscape = false;
         this.button.hide();
         CardCrawlGame.mainMenuScreen.panelScreen.refresh();
      }

      boolean isScrollingScrollBar = this.scrollBar.update();
      if (!isScrollingScrollBar) {
         this.updateScrolling();
      }

      InputHelper.justClickedLeft = false;
      this.updateList(this.commonPotions);
      this.updateList(this.uncommonPotions);
      this.updateList(this.rarePotions);
   }

   private void updateControllerInput() {
      if (Settings.isControllerMode) {
         PotionViewScreen.PotionCategory category = PotionViewScreen.PotionCategory.NONE;
         int index = 0;
         boolean anyHovered = false;

         for (AbstractPotion r : this.commonPotions) {
            if (r.hb.hovered) {
               anyHovered = true;
               category = PotionViewScreen.PotionCategory.COMMON;
               break;
            }

            index++;
         }

         if (!anyHovered) {
            index = 0;

            for (AbstractPotion r : this.uncommonPotions) {
               if (r.hb.hovered) {
                  anyHovered = true;
                  category = PotionViewScreen.PotionCategory.UNCOMMON;
                  break;
               }

               index++;
            }
         }

         if (!anyHovered) {
            index = 0;

            for (AbstractPotion r : this.rarePotions) {
               if (r.hb.hovered) {
                  anyHovered = true;
                  category = PotionViewScreen.PotionCategory.RARE;
                  break;
               }

               index++;
            }
         }

         if (!anyHovered) {
            System.out.println("NONE HOVERED");
            CInputHelper.setCursor(this.commonPotions.get(0).hb);
         } else {
            switch (category) {
               case COMMON:
                  if (!CInputActionSet.up.isJustPressed() && !CInputActionSet.altUp.isJustPressed()) {
                     if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        index += 10;
                        if (index > this.commonPotions.size() - 1) {
                           index %= 10;
                           if (index > this.uncommonPotions.size() - 1) {
                              index = this.uncommonPotions.size() - 1;
                           }

                           CInputHelper.setCursor(this.uncommonPotions.get(index).hb);
                           this.controllerPotionHb = this.uncommonPotions.get(index).hb;
                        } else {
                           CInputHelper.setCursor(this.commonPotions.get(index).hb);
                           this.controllerPotionHb = this.commonPotions.get(index).hb;
                        }
                     } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        if (--index < 0) {
                           index = this.commonPotions.size() - 1;
                        }

                        CInputHelper.setCursor(this.commonPotions.get(index).hb);
                        this.controllerPotionHb = this.commonPotions.get(index).hb;
                     } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        if (++index > this.commonPotions.size() - 1) {
                           index = 0;
                        }

                        CInputHelper.setCursor(this.commonPotions.get(index).hb);
                        this.controllerPotionHb = this.commonPotions.get(index).hb;
                     }
                  } else {
                     if (index >= 10) {
                        index -= 10;
                     }

                     CInputHelper.setCursor(this.commonPotions.get(index).hb);
                     this.controllerPotionHb = this.commonPotions.get(index).hb;
                  }
                  break;
               case UNCOMMON:
                  if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                     index -= 10;
                     if (index < 0) {
                        index = this.commonPotions.size() - 1;
                        CInputHelper.setCursor(this.commonPotions.get(index).hb);
                        this.controllerPotionHb = this.commonPotions.get(index).hb;
                     } else {
                        CInputHelper.setCursor(this.uncommonPotions.get(index).hb);
                        this.controllerPotionHb = this.uncommonPotions.get(index).hb;
                     }
                  } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                     index += 10;
                     if (index > this.uncommonPotions.size() - 1) {
                        index %= 10;
                        if (index > this.rarePotions.size() - 1) {
                           index = this.rarePotions.size() - 1;
                        }

                        CInputHelper.setCursor(this.rarePotions.get(index).hb);
                        this.controllerPotionHb = this.rarePotions.get(index).hb;
                     } else {
                        CInputHelper.setCursor(this.uncommonPotions.get(index).hb);
                        this.controllerPotionHb = this.uncommonPotions.get(index).hb;
                     }
                  } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                     if (--index < 0) {
                        index = this.uncommonPotions.size() - 1;
                     }

                     CInputHelper.setCursor(this.uncommonPotions.get(index).hb);
                     this.controllerPotionHb = this.uncommonPotions.get(index).hb;
                  } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                     if (++index > this.uncommonPotions.size() - 1) {
                        index = 0;
                     }

                     CInputHelper.setCursor(this.uncommonPotions.get(index).hb);
                     this.controllerPotionHb = this.uncommonPotions.get(index).hb;
                  }
                  break;
               case RARE:
                  if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                     CInputHelper.setCursor(this.uncommonPotions.get(index).hb);
                     this.controllerPotionHb = this.uncommonPotions.get(index).hb;
                  } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                     if (--index < 0) {
                        index = this.rarePotions.size() - 1;
                     }

                     CInputHelper.setCursor(this.rarePotions.get(index).hb);
                     this.controllerPotionHb = this.rarePotions.get(index).hb;
                  } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                     if (++index > this.rarePotions.size() - 1) {
                        index = 0;
                     }

                     CInputHelper.setCursor(this.rarePotions.get(index).hb);
                     this.controllerPotionHb = this.rarePotions.get(index).hb;
                  }
            }
         }
      }
   }

   private void updateScrolling() {
      int y = InputHelper.mY;
      if (!this.grabbedScreen && Settings.isModded) {
         if (InputHelper.scrolledDown) {
            this.targetY = this.targetY + Settings.SCROLL_SPEED;
         } else if (InputHelper.scrolledUp) {
            this.targetY = this.targetY - Settings.SCROLL_SPEED;
         }

         if (InputHelper.justClickedLeft) {
            this.grabbedScreen = true;
            this.grabStartY = y - this.targetY;
         }
      } else if (InputHelper.isMouseDown && Settings.isModded) {
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

   private void updateList(ArrayList<AbstractPotion> list) {
      for (AbstractPotion r : list) {
         r.hb.update();
         r.hb.move(r.posX, r.posY);
         r.update();
      }
   }

   public void render(SpriteBatch sb) {
      this.row = -1;
      this.col = 0;
      this.renderList(sb, TEXT[1], TEXT[2], this.commonPotions);
      this.renderList(sb, TEXT[3], TEXT[4], this.uncommonPotions);
      this.renderList(sb, TEXT[5], TEXT[6], this.rarePotions);
      this.button.render(sb);
      if (Settings.isModded) {
         this.scrollBar.render(sb);
      }
   }

   private void renderList(SpriteBatch sb, String msg, String desc, ArrayList<AbstractPotion> list) {
      this.row += 2;
      FontHelper.renderSmartText(
         sb,
         FontHelper.buttonLabelFont,
         msg,
         START_X - 50.0F * Settings.scale,
         this.scrollY + 4.0F * Settings.scale - SPACE * this.row,
         99999.0F,
         0.0F,
         Settings.GOLD_COLOR
      );
      FontHelper.renderSmartText(
         sb,
         FontHelper.cardDescFont_N,
         desc,
         START_X - 50.0F * Settings.scale + FontHelper.getSmartWidth(FontHelper.buttonLabelFont, msg, 99999.0F, 0.0F),
         this.scrollY - 0.0F * Settings.scale - SPACE * this.row,
         99999.0F,
         0.0F,
         Settings.CREAM_COLOR
      );
      this.row++;
      this.col = 0;

      for (AbstractPotion r : list) {
         if (this.col == 10) {
            this.col = 0;
            this.row++;
         }

         r.posX = START_X + SPACE * this.col;
         r.posY = this.scrollY - SPACE * this.row;
         r.labRender(sb);
         this.col++;
      }
   }

   @Override
   public void scrolledUsingBar(float newPercent) {
      if (Settings.isModded) {
         float newPosition = MathHelper.valueFromPercentBetween(this.scrollLowerBound, this.scrollUpperBound, newPercent);
         this.scrollY = newPosition;
         this.targetY = newPosition;
         this.updateBarPosition();
      }
   }

   private void updateBarPosition() {
      float percent = MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound, this.scrollY);
      this.scrollBar.parentScrolledToPercent(percent);
   }

   static {
      TEXT = uiStrings.TEXT;
   }

   private static enum PotionCategory {
      NONE,
      COMMON,
      UNCOMMON,
      RARE;
   }
}
