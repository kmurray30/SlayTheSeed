package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.map.DungeonMap;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.MapCircleEffect;
import com.megacrit.cardcrawl.vfx.scene.LevelTransitionTextOverlayEffect;
import java.util.ArrayList;

public class DungeonMapScreen {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("DungeonMapScreen");
   public static final String[] TEXT;
   public DungeonMap map = new DungeonMap();
   private ArrayList<MapRoomNode> visibleMapNodes = new ArrayList<>();
   public boolean dismissable = false;
   private float mapScrollUpperLimit;
   private static final float MAP_UPPER_SCROLL_DEFAULT = -2300.0F * Settings.scale;
   private static final float MAP_UPPER_SCROLL_FINAL_ACT = -300.0F * Settings.scale;
   private static final float MAP_SCROLL_LOWER = 190.0F * Settings.scale;
   public static final float ICON_SPACING_Y = 120.0F * Settings.scale;
   public static float offsetY = -100.0F * Settings.scale;
   private float targetOffsetY = offsetY;
   private float grabStartY = 0.0F;
   private boolean grabbedScreen = false;
   public boolean clicked = false;
   public float clickTimer = 0.0F;
   private float clickStartX;
   private float clickStartY;
   private float scrollWaitTimer = 0.0F;
   private static final float SCROLL_WAIT_TIME = 1.0F;
   private static final float SPECIAL_ANIMATE_TIME = 3.0F;
   private float oscillatingTimer;
   private float oscillatingFader;
   private Color oscillatingColor = Settings.GOLD_COLOR.cpy();
   private float scrollBackTimer = 0.0F;
   public Hitbox mapNodeHb = null;
   private static final float RETICLE_DIST = 20.0F * Settings.scale;
   private static final int RETICLE_W = 36;

   public DungeonMapScreen() {
      this.oscillatingFader = 0.0F;
      this.oscillatingTimer = 0.0F;
      this.oscillatingColor.a = 0.0F;
   }

   public void update() {
      if (this.scrollWaitTimer < 0.0F
         && Settings.isControllerMode
         && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP
         && !this.map.legend.isLegendHighlighted
         && this.scrollBackTimer > 0.0F) {
         this.scrollBackTimer = this.scrollBackTimer - Gdx.graphics.getDeltaTime();
         if (Gdx.input.getY() > Settings.HEIGHT * 0.85F) {
            this.targetOffsetY = this.targetOffsetY + Settings.SCROLL_SPEED * 2.0F;
         } else if (Gdx.input.getY() < Settings.HEIGHT * 0.15F) {
            this.targetOffsetY = this.targetOffsetY - Settings.SCROLL_SPEED * 2.0F;
         }

         if (this.targetOffsetY > MAP_SCROLL_LOWER) {
            this.targetOffsetY = MAP_SCROLL_LOWER;
         } else if (this.targetOffsetY < this.mapScrollUpperLimit) {
            this.targetOffsetY = this.mapScrollUpperLimit;
         }

         offsetY = MathUtils.lerp(offsetY, this.targetOffsetY, Gdx.graphics.getDeltaTime() * 12.0F);
      }

      this.map.update();
      if (AbstractDungeon.isScreenUp) {
         for (MapRoomNode n : this.visibleMapNodes) {
            n.update();
         }
      }

      if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && !this.dismissable && this.scrollWaitTimer < 0.0F) {
         this.oscillateColor();
      }

      for (AbstractGameEffect e : AbstractDungeon.topLevelEffects) {
         if (e instanceof MapCircleEffect) {
            return;
         }
      }

      if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
         this.updateYOffset();
      }

      this.updateMouse();
      this.updateControllerInput();
      if (Settings.isControllerMode && this.mapNodeHb != null && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
         int tmpY = (int)(Settings.HEIGHT - this.mapNodeHb.cY);
         if (tmpY < 1) {
            tmpY = 1;
         } else if (tmpY > Settings.HEIGHT - 1) {
            tmpY = Settings.HEIGHT - 1;
         }

         Gdx.input.setCursorPosition((int)this.mapNodeHb.cX, tmpY);
      }
   }

   private void updateMouse() {
      if (this.clicked) {
         this.clicked = false;
      }

      if (InputHelper.justReleasedClickLeft
         && this.clickTimer < 0.4F
         && Vector2.dst(this.clickStartX, this.clickStartY, InputHelper.mX, InputHelper.mY) < Settings.CLICK_DIST_THRESHOLD) {
         this.clicked = true;
      }

      if (!InputHelper.justClickedLeft
         && (!CInputActionSet.select.isJustPressed() || !AbstractDungeon.topPanel.potionUi.isHidden || AbstractDungeon.topPanel.selectPotionMode)) {
         if (InputHelper.isMouseDown) {
            this.clickTimer = this.clickTimer + Gdx.graphics.getDeltaTime();
         }
      } else {
         this.clickTimer = 0.0F;
         this.clickStartX = InputHelper.mX;
         this.clickStartY = InputHelper.mY;
      }

      if (CInputActionSet.select.isJustPressed() && this.clickTimer < 0.4F && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
         this.clicked = true;
      }
   }

   private void updateControllerInput() {
      if (this.scrollWaitTimer > 0.0F
         || !Settings.isControllerMode
         || AbstractDungeon.topPanel.selectPotionMode
         || !AbstractDungeon.topPanel.potionUi.isHidden
         || this.map.legend.isLegendHighlighted
         || AbstractDungeon.player.viewingRelics) {
         this.mapNodeHb = null;
      } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
         this.targetOffsetY = this.targetOffsetY + Settings.SCROLL_SPEED * 4.0F;
      } else if (!CInputActionSet.up.isJustPressed() && !CInputActionSet.altUp.isJustPressed()) {
         if (CInputActionSet.left.isJustPressed()
            || CInputActionSet.right.isJustPressed()
            || CInputActionSet.altRight.isJustPressed()
            || CInputActionSet.altLeft.isJustPressed()) {
            this.scrollBackTimer = 0.1F;
         }

         if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            ArrayList<MapRoomNode> nodes = new ArrayList<>();
            if (!AbstractDungeon.firstRoomChosen) {
               for (MapRoomNode n : this.visibleMapNodes) {
                  if (n.y == 0) {
                     nodes.add(n);
                  }
               }
            } else {
               for (MapRoomNode nx : this.visibleMapNodes) {
                  boolean flightMatters = AbstractDungeon.player.hasRelic("WingedGreaves") || ModHelper.isModEnabled("Flight");
                  if (AbstractDungeon.currMapNode.isConnectedTo(nx) || flightMatters && AbstractDungeon.currMapNode.wingedIsConnectedTo(nx)) {
                     nodes.add(nx);
                  }
               }
            }

            boolean anyHovered = false;
            int index = 0;

            for (MapRoomNode nxx : nodes) {
               if (nxx.hb.hovered) {
                  anyHovered = true;
                  break;
               }

               index++;
            }

            if (!anyHovered && this.mapNodeHb == null && !nodes.isEmpty()) {
               Gdx.input.setCursorPosition((int)nodes.get(nodes.size() / 2).hb.cX, Settings.HEIGHT - (int)nodes.get(nodes.size() / 2).hb.cY);
               this.mapNodeHb = nodes.get(nodes.size() / 2).hb;
            } else if (!anyHovered && nodes.isEmpty()) {
               Gdx.input
                  .setCursorPosition((int)AbstractDungeon.dungeonMapScreen.map.bossHb.cX, Settings.HEIGHT - (int)AbstractDungeon.dungeonMapScreen.map.bossHb.cY);
               this.mapNodeHb = null;
            } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
               if (--index < 0) {
                  index = nodes.size() - 1;
               }

               Gdx.input.setCursorPosition((int)nodes.get(index).hb.cX, Settings.HEIGHT - (int)nodes.get(index).hb.cY);
               this.mapNodeHb = nodes.get(index).hb;
            } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
               if (++index > nodes.size() - 1) {
                  index = 0;
               }

               Gdx.input.setCursorPosition((int)nodes.get(index).hb.cX, Settings.HEIGHT - (int)nodes.get(index).hb.cY);
               this.mapNodeHb = nodes.get(index).hb;
            }
         }
      } else {
         this.targetOffsetY = this.targetOffsetY - Settings.SCROLL_SPEED * 4.0F;
      }
   }

   private void updateYOffset() {
      if (this.grabbedScreen) {
         if (InputHelper.isMouseDown) {
            this.targetOffsetY = InputHelper.mY - this.grabStartY;
         } else {
            this.grabbedScreen = false;
         }
      } else if (this.scrollWaitTimer < 0.0F) {
         if (InputHelper.scrolledDown) {
            this.targetOffsetY = this.targetOffsetY + Settings.MAP_SCROLL_SPEED;
         } else if (InputHelper.scrolledUp) {
            this.targetOffsetY = this.targetOffsetY - Settings.MAP_SCROLL_SPEED;
         }

         if (InputHelper.justClickedLeft && this.scrollWaitTimer < 0.0F) {
            this.grabbedScreen = true;
            this.grabStartY = InputHelper.mY - this.targetOffsetY;
         }
      }

      this.resetScrolling();
      this.updateAnimation();
   }

   private void resetScrolling() {
      if (this.targetOffsetY < this.mapScrollUpperLimit) {
         this.targetOffsetY = MathHelper.scrollSnapLerpSpeed(this.targetOffsetY, this.mapScrollUpperLimit);
      } else if (this.targetOffsetY > MAP_SCROLL_LOWER) {
         this.targetOffsetY = MathHelper.scrollSnapLerpSpeed(this.targetOffsetY, MAP_SCROLL_LOWER);
      }
   }

   private void updateAnimation() {
      this.scrollWaitTimer = this.scrollWaitTimer - Gdx.graphics.getDeltaTime();
      if (this.scrollWaitTimer < 0.0F) {
         offsetY = MathUtils.lerp(offsetY, this.targetOffsetY, Gdx.graphics.getDeltaTime() * 12.0F);
      } else if (this.scrollWaitTimer < 3.0F) {
         offsetY = Interpolation.exp10.apply(MAP_SCROLL_LOWER, this.mapScrollUpperLimit, this.scrollWaitTimer / 3.0F);
      }
   }

   public void updateImage() {
      this.visibleMapNodes.clear();

      for (ArrayList<MapRoomNode> rows : CardCrawlGame.dungeon.getMap()) {
         for (MapRoomNode node : rows) {
            if (node.hasEdges()) {
               this.visibleMapNodes.add(node);
            }
         }
      }
   }

   public void render(SpriteBatch sb) {
      this.map.render(sb);
      if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
         for (MapRoomNode n : this.visibleMapNodes) {
            n.render(sb);
         }
      }

      this.map.renderBossIcon(sb);
      if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && !this.dismissable && this.scrollWaitTimer < 0.0F) {
         FontHelper.renderDeckViewTip(sb, TEXT[0], 80.0F * Settings.scale, this.oscillatingColor);
      }

      this.renderControllerUi(sb);
   }

   private void renderControllerUi(SpriteBatch sb) {
      if (Settings.isControllerMode) {
         if (this.mapNodeHb != null) {
            this.renderReticle(sb, this.mapNodeHb);
         }
      }
   }

   private void oscillateColor() {
      this.oscillatingFader = this.oscillatingFader + Gdx.graphics.getDeltaTime();
      if (this.oscillatingFader > 1.0F) {
         this.oscillatingFader = 1.0F;
         this.oscillatingTimer = this.oscillatingTimer + Gdx.graphics.getDeltaTime() * 5.0F;
      }

      this.oscillatingColor.a = (0.33F + (MathUtils.cos(this.oscillatingTimer) + 1.0F) / 3.0F) * this.oscillatingFader;
   }

   public void open(boolean doScrollingAnimation) {
      this.mapNodeHb = null;
      if (!AbstractDungeon.id.equals("TheEnding")) {
         this.mapScrollUpperLimit = MAP_UPPER_SCROLL_DEFAULT;
      } else {
         this.mapScrollUpperLimit = MAP_UPPER_SCROLL_FINAL_ACT;
      }

      AbstractDungeon.player.releaseCard();
      this.map.legend.isLegendHighlighted = false;
      if (Settings.isDebug) {
         doScrollingAnimation = false;
      }

      InputHelper.justClickedLeft = false;
      this.clicked = false;
      this.clickTimer = 999.0F;
      this.grabbedScreen = false;
      AbstractDungeon.topPanel.unhoverHitboxes();
      this.map.show();
      this.dismissable = !doScrollingAnimation;
      if (MathUtils.randomBoolean()) {
         CardCrawlGame.sound.play("MAP_OPEN", 0.1F);
      } else {
         CardCrawlGame.sound.play("MAP_OPEN_2", 0.1F);
      }

      if (doScrollingAnimation) {
         this.mapNodeHb = null;
         AbstractDungeon.topLevelEffects.add(new LevelTransitionTextOverlayEffect(AbstractDungeon.name, AbstractDungeon.levelNum));
         this.scrollWaitTimer = 4.0F;
         offsetY = this.mapScrollUpperLimit;
         this.targetOffsetY = MAP_SCROLL_LOWER;
      } else {
         this.scrollWaitTimer = 0.0F;
         AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
         if (AbstractDungeon.getCurrMapNode() == null) {
            offsetY = this.mapScrollUpperLimit;
         } else {
            offsetY = -50.0F * Settings.scale + AbstractDungeon.getCurrMapNode().y * -ICON_SPACING_Y;
         }

         this.targetOffsetY = offsetY;
      }

      AbstractDungeon.dynamicBanner.hide();
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.MAP;
      AbstractDungeon.isScreenUp = true;
      this.grabStartY = 0.0F;
      AbstractDungeon.overlayMenu.proceedButton.hide();
      AbstractDungeon.overlayMenu.hideCombatPanels();
      AbstractDungeon.overlayMenu.endTurnButton.hide();
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.updateImage();
   }

   public void close() {
      this.map.hide();
      AbstractDungeon.overlayMenu.cancelButton.hide();
      this.clicked = false;
   }

   public void closeInstantly() {
      this.map.hideInstantly();
      if (AbstractDungeon.overlayMenu != null) {
         AbstractDungeon.overlayMenu.cancelButton.hideInstantly();
      }

      this.clicked = false;
   }

   public void renderReticle(SpriteBatch sb, Hitbox hb) {
      this.renderReticleCorner(sb, -hb.width / 2.0F - RETICLE_DIST, hb.height / 2.0F + RETICLE_DIST, hb, false, false);
      this.renderReticleCorner(sb, hb.width / 2.0F + RETICLE_DIST, hb.height / 2.0F + RETICLE_DIST, hb, true, false);
      this.renderReticleCorner(sb, -hb.width / 2.0F - RETICLE_DIST, -hb.height / 2.0F - RETICLE_DIST, hb, false, true);
      this.renderReticleCorner(sb, hb.width / 2.0F + RETICLE_DIST, -hb.height / 2.0F - RETICLE_DIST, hb, true, true);
   }

   private void renderReticleCorner(SpriteBatch sb, float x, float y, Hitbox hb, boolean flipX, boolean flipY) {
      sb.setColor(new Color(0.0F, 0.0F, 0.0F, this.map.targetAlpha / 4.0F));
      sb.draw(
         ImageMaster.RETICLE_CORNER,
         hb.cX + x - 18.0F + 4.0F * Settings.scale,
         hb.cY + y - 18.0F - 4.0F * Settings.scale,
         18.0F,
         18.0F,
         36.0F,
         36.0F,
         Settings.scale,
         Settings.scale,
         0.0F,
         0,
         0,
         36,
         36,
         flipX,
         flipY
      );
      sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.map.targetAlpha));
      sb.draw(
         ImageMaster.RETICLE_CORNER,
         hb.cX + x - 18.0F,
         hb.cY + y - 18.0F,
         18.0F,
         18.0F,
         36.0F,
         36.0F,
         Settings.scale,
         Settings.scale,
         0.0F,
         0,
         0,
         36,
         36,
         flipX,
         flipY
      );
   }

   static {
      TEXT = uiStrings.TEXT;
   }
}
