package com.megacrit.cardcrawl.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;
import com.megacrit.cardcrawl.vfx.FadeWipeParticle;
import com.megacrit.cardcrawl.vfx.FlameAnimationEffect;
import com.megacrit.cardcrawl.vfx.MapCircleEffect;
import java.util.ArrayList;
import java.util.Iterator;

public class MapRoomNode {
   private static final int IMG_WIDTH = (int)(Settings.xScale * 64.0F);
   public static final float OFFSET_X = Settings.isMobile ? 496.0F * Settings.xScale : 560.0F * Settings.xScale;
   private static final float OFFSET_Y = 180.0F * Settings.scale;
   private static final float SPACING_X = Settings.isMobile ? IMG_WIDTH * 2.2F : IMG_WIDTH * 2.0F;
   private static final float JITTER_X = Settings.isMobile ? 13.0F * Settings.xScale : 27.0F * Settings.xScale;
   private static final float JITTER_Y = Settings.isMobile ? 18.0F * Settings.xScale : 37.0F * Settings.xScale;
   public float offsetX = (int)MathUtils.random(-JITTER_X, JITTER_X);
   public float offsetY = (int)MathUtils.random(-JITTER_Y, JITTER_Y);
   public static final Color AVAILABLE_COLOR = new Color(0.09F, 0.13F, 0.17F, 1.0F);
   private static final Color NOT_TAKEN_COLOR = new Color(0.34F, 0.34F, 0.34F, 1.0F);
   private static final Color OUTLINE_COLOR = Color.valueOf("8c8c80ff");
   public Color color = NOT_TAKEN_COLOR.cpy();
   private float oscillateTimer = MathUtils.random(0.0F, 6.28F);
   public Hitbox hb = null;
   private static final int W = 128;
   private static final int O_W = 192;
   private float scale = 0.5F;
   private float angle = MathUtils.random(360.0F);
   private ArrayList<MapRoomNode> parents = new ArrayList<>();
   private ArrayList<FlameAnimationEffect> fEffects = new ArrayList<>();
   private float flameVfxTimer = 0.0F;
   public int x;
   public int y;
   public AbstractRoom room = null;
   private ArrayList<MapEdge> edges = new ArrayList<>();
   public boolean taken = false;
   public boolean highlighted = false;
   private float animWaitTimer = 0.0F;
   public boolean hasEmeraldKey = false;
   private static final float ANIM_WAIT_TIME = 0.25F;

   public MapRoomNode(int x, int y) {
      this.x = x;
      this.y = y;
      float hitbox_w = Settings.isMobile ? 114.0F * Settings.scale : 64.0F * Settings.scale;
      this.hb = new Hitbox(hitbox_w, hitbox_w);
   }

   public boolean hasEdges() {
      return !this.edges.isEmpty();
   }

   public void addEdge(MapEdge e) {
      Boolean unique = true;

      for (MapEdge otherEdge : this.edges) {
         if (e.compareTo(otherEdge) == 0) {
            unique = false;
         }
      }

      if (unique) {
         this.edges.add(e);
      }
   }

   public void delEdge(MapEdge e) {
      this.edges.remove(e);
   }

   public ArrayList<MapEdge> getEdges() {
      return this.edges;
   }

   public boolean isConnectedTo(MapRoomNode node) {
      for (MapEdge edge : this.edges) {
         if (node.x == edge.dstX && node.y == edge.dstY) {
            return true;
         }
      }

      return false;
   }

   public boolean wingedIsConnectedTo(MapRoomNode node) {
      for (MapEdge edge : this.edges) {
         if (ModHelper.isModEnabled("Flight") && node.y == edge.dstY) {
            return true;
         }

         if (node.y == edge.dstY && AbstractDungeon.player.hasRelic("WingedGreaves") && AbstractDungeon.player.getRelic("WingedGreaves").counter > 0) {
            return true;
         }
      }

      return false;
   }

   public MapEdge getEdgeConnectedTo(MapRoomNode node) {
      for (MapEdge edge : this.edges) {
         if (node.x == edge.dstX && node.y == edge.dstY) {
            return edge;
         }
      }

      return null;
   }

   public void setRoom(AbstractRoom room) {
      this.room = room;
   }

   public boolean leftNodeAvailable() {
      for (MapEdge edge : this.edges) {
         if (edge.dstX < this.x) {
            return true;
         }
      }

      return false;
   }

   public boolean centerNodeAvailable() {
      for (MapEdge edge : this.edges) {
         if (edge.dstX == this.x) {
            return true;
         }
      }

      return false;
   }

   public boolean rightNodeAvailable() {
      for (MapEdge edge : this.edges) {
         if (edge.dstX > this.x) {
            return true;
         }
      }

      return false;
   }

   public void addParent(MapRoomNode parent) {
      this.parents.add(parent);
   }

   public ArrayList<MapRoomNode> getParents() {
      return this.parents;
   }

   public String getRoomSymbol(Boolean showSpecificRoomSymbol) {
      return this.room != null && showSpecificRoomSymbol ? this.room.getMapSymbol() : "*";
   }

   @Override
   public String toString() {
      return "(" + this.x + "," + this.y + "):" + this.edges.toString();
   }

   public AbstractRoom getRoom() {
      return this.room;
   }

   public void update() {
      if (this.animWaitTimer != 0.0F) {
         this.animWaitTimer = this.animWaitTimer - Gdx.graphics.getDeltaTime();
         if (this.animWaitTimer < 0.0F) {
            if (!AbstractDungeon.firstRoomChosen) {
               AbstractDungeon.setCurrMapNode(this);
            } else {
               AbstractDungeon.getCurrMapNode().taken = true;
            }

            MapEdge connectedEdge = AbstractDungeon.getCurrMapNode().getEdgeConnectedTo(this);
            if (connectedEdge != null) {
               connectedEdge.markAsTaken();
            }

            this.animWaitTimer = 0.0F;
            AbstractDungeon.nextRoom = this;
            AbstractDungeon.pathX.add(this.x);
            AbstractDungeon.pathY.add(this.y);
            CardCrawlGame.metricData.path_taken.add(AbstractDungeon.nextRoom.getRoom().getMapSymbol());
            if (!AbstractDungeon.isDungeonBeaten) {
               AbstractDungeon.nextRoomTransitionStart();
               CardCrawlGame.music.fadeOutTempBGM();
            }
         }
      }

      this.updateEmerald();
      this.highlighted = false;
      this.scale = MathHelper.scaleLerpSnap(this.scale, 0.5F);
      this.hb.move(this.x * SPACING_X + OFFSET_X + this.offsetX, this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY + this.offsetY);
      this.hb.update();

      for (MapEdge edge : this.edges) {
         if (!edge.taken) {
            edge.color = NOT_TAKEN_COLOR;
         }
      }

      if (AbstractDungeon.getCurrRoom().phase.equals(AbstractRoom.RoomPhase.COMPLETE)) {
         if (this.equals(AbstractDungeon.getCurrMapNode())) {
            for (MapEdge edgex : this.edges) {
               edgex.color = AVAILABLE_COLOR;
            }
         }

         boolean normalConnection = AbstractDungeon.getCurrMapNode().isConnectedTo(this);
         boolean wingedConnection = AbstractDungeon.getCurrMapNode().wingedIsConnectedTo(this);
         if (!normalConnection && !Settings.isDebug && !wingedConnection) {
            if (this.hb.hovered && !this.taken) {
               this.scale = 1.0F;
               this.color = AVAILABLE_COLOR.cpy();
            } else {
               this.color = NOT_TAKEN_COLOR.cpy();
            }
         } else {
            if (this.hb.hovered) {
               if (this.hb.justHovered) {
                  this.playNodeHoveredSound();
               }

               if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && AbstractDungeon.dungeonMapScreen.clicked && this.animWaitTimer <= 0.0F) {
                  this.playNodeSelectedSound();
                  AbstractDungeon.dungeonMapScreen.clicked = false;
                  AbstractDungeon.dungeonMapScreen.clickTimer = 0.0F;
                  if (!normalConnection && wingedConnection && AbstractDungeon.player.hasRelic("WingedGreaves")) {
                     AbstractDungeon.player.getRelic("WingedGreaves").counter--;
                     if (AbstractDungeon.player.getRelic("WingedGreaves").counter <= 0) {
                        AbstractDungeon.player.getRelic("WingedGreaves").setCounter(-2);
                     }
                  }

                  AbstractDungeon.topLevelEffects
                     .add(
                        new MapCircleEffect(
                           this.x * SPACING_X + OFFSET_X + this.offsetX,
                           this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY + this.offsetY,
                           this.angle
                        )
                     );
                  if (!Settings.FAST_MODE) {
                     AbstractDungeon.topLevelEffects.add(new FadeWipeParticle());
                  }

                  this.animWaitTimer = 0.25F;
                  if (this.room instanceof EventRoom) {
                     CardCrawlGame.mysteryMachine++;
                  }
               }

               this.highlighted = true;
            } else {
               this.color = AVAILABLE_COLOR.cpy();
            }

            this.oscillateColor();
         }
      } else if (this.hb.hovered) {
         this.scale = 1.0F;
         this.color = AVAILABLE_COLOR.cpy();
      } else {
         this.color = NOT_TAKEN_COLOR.cpy();
      }

      if (!AbstractDungeon.firstRoomChosen && this.y == 0 && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE) {
         if (this.hb.hovered) {
            if (this.hb.justHovered) {
               this.playNodeHoveredSound();
            }

            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP
               && (CInputActionSet.select.isJustPressed() || AbstractDungeon.dungeonMapScreen.clicked)) {
               this.playNodeSelectedSound();
               AbstractDungeon.dungeonMapScreen.clicked = false;
               AbstractDungeon.dungeonMapScreen.clickTimer = 0.0F;
               AbstractDungeon.dungeonMapScreen.dismissable = true;
               if (!AbstractDungeon.firstRoomChosen) {
                  AbstractDungeon.firstRoomChosen = true;
               }

               AbstractDungeon.topLevelEffects
                  .add(
                     new MapCircleEffect(
                        this.x * SPACING_X + OFFSET_X + this.offsetX,
                        this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY + this.offsetY,
                        this.angle
                     )
                  );
               AbstractDungeon.topLevelEffects.add(new FadeWipeParticle());
               this.animWaitTimer = 0.25F;
            }

            this.highlighted = true;
         } else if (this.y != 0) {
            this.highlighted = true;
            this.scale = 1.0F;
         } else {
            this.color = AVAILABLE_COLOR.cpy();
         }

         this.oscillateColor();
      }

      if (this.equals(AbstractDungeon.getCurrMapNode())) {
         this.color = AVAILABLE_COLOR.cpy();
         this.scale = MathHelper.scaleLerpSnap(this.scale, 0.5F);
      }
   }

   private void updateEmerald() {
      if (Settings.isFinalActAvailable && this.hasEmeraldKey) {
         this.flameVfxTimer = this.flameVfxTimer - Gdx.graphics.getDeltaTime();
         if (this.flameVfxTimer < 0.0F) {
            this.flameVfxTimer = MathUtils.random(0.2F, 0.4F);
            this.fEffects.add(new FlameAnimationEffect(this.hb));
         }

         Iterator<FlameAnimationEffect> i = this.fEffects.iterator();

         while (i.hasNext()) {
            FlameAnimationEffect e = i.next();
            if (e.isDone) {
               e.dispose();
               i.remove();
            }
         }

         for (FlameAnimationEffect e : this.fEffects) {
            e.update();
         }
      }
   }

   private void playNodeHoveredSound() {
      int roll = MathUtils.random(3);
      switch (roll) {
         case 0:
            CardCrawlGame.sound.play("MAP_HOVER_1");
            break;
         case 1:
            CardCrawlGame.sound.play("MAP_HOVER_2");
            break;
         case 2:
            CardCrawlGame.sound.play("MAP_HOVER_3");
            break;
         default:
            CardCrawlGame.sound.play("MAP_HOVER_4");
      }
   }

   private void playNodeSelectedSound() {
      int roll = MathUtils.random(3);
      switch (roll) {
         case 0:
            CardCrawlGame.sound.play("MAP_SELECT_1");
            break;
         case 1:
            CardCrawlGame.sound.play("MAP_SELECT_2");
            break;
         case 2:
            CardCrawlGame.sound.play("MAP_SELECT_3");
            break;
         default:
            CardCrawlGame.sound.play("MAP_SELECT_4");
      }
   }

   private void oscillateColor() {
      if (!this.taken) {
         this.oscillateTimer = this.oscillateTimer + Gdx.graphics.getDeltaTime() * 5.0F;
         this.color.a = 0.66F + (MathUtils.cos(this.oscillateTimer) + 1.0F) / 6.0F;
         this.scale = 0.25F + this.color.a;
      } else {
         this.scale = MathHelper.scaleLerpSnap(this.scale, Settings.scale);
      }
   }

   public void render(SpriteBatch sb) {
      for (MapEdge edge : this.edges) {
         edge.render(sb);
      }

      this.renderEmeraldVfx(sb);
      if (this.highlighted) {
         sb.setColor(new Color(0.9F, 0.9F, 0.9F, 1.0F));
      } else {
         sb.setColor(OUTLINE_COLOR);
      }

      boolean legendHovered = AbstractDungeon.dungeonMapScreen.map.legend.isIconHovered(this.getRoomSymbol(true));
      if (legendHovered) {
         this.scale = 0.68F;
         sb.setColor(Color.LIGHT_GRAY);
      }

      if (!Settings.isMobile) {
         sb.draw(
            this.room.getMapImgOutline(),
            this.x * SPACING_X + OFFSET_X - 64.0F + this.offsetX,
            this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY - 64.0F + this.offsetY,
            64.0F,
            64.0F,
            128.0F,
            128.0F,
            this.scale * Settings.scale,
            this.scale * Settings.scale,
            0.0F,
            0,
            0,
            128,
            128,
            false,
            false
         );
      } else {
         sb.draw(
            this.room.getMapImgOutline(),
            this.x * SPACING_X + OFFSET_X - 64.0F + this.offsetX,
            this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY - 64.0F + this.offsetY,
            64.0F,
            64.0F,
            128.0F,
            128.0F,
            this.scale * Settings.scale * 2.0F,
            this.scale * Settings.scale * 2.0F,
            0.0F,
            0,
            0,
            128,
            128,
            false,
            false
         );
      }

      if (this.taken) {
         sb.setColor(AVAILABLE_COLOR);
      } else {
         sb.setColor(this.color);
      }

      if (legendHovered) {
         sb.setColor(AVAILABLE_COLOR);
      }

      if (!Settings.isMobile) {
         sb.draw(
            this.room.getMapImg(),
            this.x * SPACING_X + OFFSET_X - 64.0F + this.offsetX,
            this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY - 64.0F + this.offsetY,
            64.0F,
            64.0F,
            128.0F,
            128.0F,
            this.scale * Settings.scale,
            this.scale * Settings.scale,
            0.0F,
            0,
            0,
            128,
            128,
            false,
            false
         );
      } else {
         sb.draw(
            this.room.getMapImg(),
            this.x * SPACING_X + OFFSET_X - 64.0F + this.offsetX,
            this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY - 64.0F + this.offsetY,
            64.0F,
            64.0F,
            128.0F,
            128.0F,
            this.scale * Settings.scale * 2.0F,
            this.scale * Settings.scale * 2.0F,
            0.0F,
            0,
            0,
            128,
            128,
            false,
            false
         );
      }

      if (this.taken || AbstractDungeon.firstRoomChosen && this.equals(AbstractDungeon.getCurrMapNode())) {
         sb.setColor(AVAILABLE_COLOR);
         if (!Settings.isMobile) {
            sb.draw(
               ImageMaster.MAP_CIRCLE_5,
               this.x * SPACING_X + OFFSET_X - 96.0F + this.offsetX,
               this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY - 96.0F + this.offsetY,
               96.0F,
               96.0F,
               192.0F,
               192.0F,
               (this.scale * 0.95F + 0.2F) * Settings.scale,
               (this.scale * 0.95F + 0.2F) * Settings.scale,
               this.angle,
               0,
               0,
               192,
               192,
               false,
               false
            );
         } else {
            sb.draw(
               ImageMaster.MAP_CIRCLE_5,
               this.x * SPACING_X + OFFSET_X - 96.0F + this.offsetX,
               this.y * Settings.MAP_DST_Y + OFFSET_Y + DungeonMapScreen.offsetY - 96.0F + this.offsetY,
               96.0F,
               96.0F,
               192.0F,
               192.0F,
               (this.scale * 0.95F + 0.2F) * Settings.scale * 2.0F,
               (this.scale * 0.95F + 0.2F) * Settings.scale * 2.0F,
               this.angle,
               0,
               0,
               192,
               192,
               false,
               false
            );
         }
      }

      if (this.hb != null) {
         this.hb.render(sb);
      }
   }

   private void renderEmeraldVfx(SpriteBatch sb) {
      if (Settings.isFinalActAvailable && this.hasEmeraldKey) {
         for (FlameAnimationEffect e : this.fEffects) {
            e.render(sb, this.scale);
         }
      }
   }
}
