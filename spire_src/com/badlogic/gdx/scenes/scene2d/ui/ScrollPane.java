package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.Cullable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

public class ScrollPane extends WidgetGroup {
   private ScrollPane.ScrollPaneStyle style;
   private Actor widget;
   final Rectangle hScrollBounds = new Rectangle();
   final Rectangle vScrollBounds = new Rectangle();
   final Rectangle hKnobBounds = new Rectangle();
   final Rectangle vKnobBounds = new Rectangle();
   private final Rectangle widgetAreaBounds = new Rectangle();
   private final Rectangle widgetCullingArea = new Rectangle();
   private final Rectangle scissorBounds = new Rectangle();
   private ActorGestureListener flickScrollListener;
   boolean scrollX;
   boolean scrollY;
   boolean vScrollOnRight = true;
   boolean hScrollOnBottom = true;
   float amountX;
   float amountY;
   float visualAmountX;
   float visualAmountY;
   float maxX;
   float maxY;
   boolean touchScrollH;
   boolean touchScrollV;
   final Vector2 lastPoint = new Vector2();
   float areaWidth;
   float areaHeight;
   private boolean fadeScrollBars = true;
   private boolean smoothScrolling = true;
   float fadeAlpha;
   float fadeAlphaSeconds = 1.0F;
   float fadeDelay;
   float fadeDelaySeconds = 1.0F;
   boolean cancelTouchFocus = true;
   boolean flickScroll = true;
   float velocityX;
   float velocityY;
   float flingTimer;
   private boolean overscrollX = true;
   private boolean overscrollY = true;
   float flingTime = 1.0F;
   private float overscrollDistance = 50.0F;
   private float overscrollSpeedMin = 30.0F;
   private float overscrollSpeedMax = 200.0F;
   private boolean forceScrollX;
   private boolean forceScrollY;
   boolean disableX;
   boolean disableY;
   private boolean clamp = true;
   private boolean scrollbarsOnTop;
   private boolean variableSizeKnobs = true;
   int draggingPointer = -1;

   public ScrollPane(Actor widget) {
      this(widget, new ScrollPane.ScrollPaneStyle());
   }

   public ScrollPane(Actor widget, Skin skin) {
      this(widget, skin.get(ScrollPane.ScrollPaneStyle.class));
   }

   public ScrollPane(Actor widget, Skin skin, String styleName) {
      this(widget, skin.get(styleName, ScrollPane.ScrollPaneStyle.class));
   }

   public ScrollPane(Actor widget, ScrollPane.ScrollPaneStyle style) {
      if (style == null) {
         throw new IllegalArgumentException("style cannot be null.");
      } else {
         this.style = style;
         this.setWidget(widget);
         this.setSize(150.0F, 150.0F);
         this.addCaptureListener(new InputListener() {
            private float handlePosition;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
               if (ScrollPane.this.draggingPointer != -1) {
                  return false;
               } else if (pointer == 0 && button != 0) {
                  return false;
               } else {
                  ScrollPane.this.getStage().setScrollFocus(ScrollPane.this);
                  if (!ScrollPane.this.flickScroll) {
                     ScrollPane.this.resetFade();
                  }

                  if (ScrollPane.this.fadeAlpha == 0.0F) {
                     return false;
                  } else if (ScrollPane.this.scrollX && ScrollPane.this.hScrollBounds.contains(x, y)) {
                     event.stop();
                     ScrollPane.this.resetFade();
                     if (ScrollPane.this.hKnobBounds.contains(x, y)) {
                        ScrollPane.this.lastPoint.set(x, y);
                        this.handlePosition = ScrollPane.this.hKnobBounds.x;
                        ScrollPane.this.touchScrollH = true;
                        ScrollPane.this.draggingPointer = pointer;
                        return true;
                     } else {
                        ScrollPane.this.setScrollX(ScrollPane.this.amountX + ScrollPane.this.areaWidth * (x < ScrollPane.this.hKnobBounds.x ? -1 : 1));
                        return true;
                     }
                  } else if (ScrollPane.this.scrollY && ScrollPane.this.vScrollBounds.contains(x, y)) {
                     event.stop();
                     ScrollPane.this.resetFade();
                     if (ScrollPane.this.vKnobBounds.contains(x, y)) {
                        ScrollPane.this.lastPoint.set(x, y);
                        this.handlePosition = ScrollPane.this.vKnobBounds.y;
                        ScrollPane.this.touchScrollV = true;
                        ScrollPane.this.draggingPointer = pointer;
                        return true;
                     } else {
                        ScrollPane.this.setScrollY(ScrollPane.this.amountY + ScrollPane.this.areaHeight * (y < ScrollPane.this.vKnobBounds.y ? 1 : -1));
                        return true;
                     }
                  } else {
                     return false;
                  }
               }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
               if (pointer == ScrollPane.this.draggingPointer) {
                  ScrollPane.this.cancel();
               }
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
               if (pointer == ScrollPane.this.draggingPointer) {
                  if (ScrollPane.this.touchScrollH) {
                     float delta = x - ScrollPane.this.lastPoint.x;
                     float scrollH = this.handlePosition + delta;
                     this.handlePosition = scrollH;
                     scrollH = Math.max(ScrollPane.this.hScrollBounds.x, scrollH);
                     scrollH = Math.min(ScrollPane.this.hScrollBounds.x + ScrollPane.this.hScrollBounds.width - ScrollPane.this.hKnobBounds.width, scrollH);
                     float total = ScrollPane.this.hScrollBounds.width - ScrollPane.this.hKnobBounds.width;
                     if (total != 0.0F) {
                        ScrollPane.this.setScrollPercentX((scrollH - ScrollPane.this.hScrollBounds.x) / total);
                     }

                     ScrollPane.this.lastPoint.set(x, y);
                  } else if (ScrollPane.this.touchScrollV) {
                     float delta = y - ScrollPane.this.lastPoint.y;
                     float scrollV = this.handlePosition + delta;
                     this.handlePosition = scrollV;
                     scrollV = Math.max(ScrollPane.this.vScrollBounds.y, scrollV);
                     scrollV = Math.min(ScrollPane.this.vScrollBounds.y + ScrollPane.this.vScrollBounds.height - ScrollPane.this.vKnobBounds.height, scrollV);
                     float total = ScrollPane.this.vScrollBounds.height - ScrollPane.this.vKnobBounds.height;
                     if (total != 0.0F) {
                        ScrollPane.this.setScrollPercentY(1.0F - (scrollV - ScrollPane.this.vScrollBounds.y) / total);
                     }

                     ScrollPane.this.lastPoint.set(x, y);
                  }
               }
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
               if (!ScrollPane.this.flickScroll) {
                  ScrollPane.this.resetFade();
               }

               return false;
            }
         });
         this.flickScrollListener = new ActorGestureListener() {
            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
               ScrollPane.this.resetFade();
               ScrollPane.this.amountX -= deltaX;
               ScrollPane.this.amountY += deltaY;
               ScrollPane.this.clamp();
               if (ScrollPane.this.cancelTouchFocus && (ScrollPane.this.scrollX && deltaX != 0.0F || ScrollPane.this.scrollY && deltaY != 0.0F)) {
                  ScrollPane.this.cancelTouchFocus();
               }
            }

            @Override
            public void fling(InputEvent event, float x, float y, int button) {
               if (Math.abs(x) > 150.0F && ScrollPane.this.scrollX) {
                  ScrollPane.this.flingTimer = ScrollPane.this.flingTime;
                  ScrollPane.this.velocityX = x;
                  if (ScrollPane.this.cancelTouchFocus) {
                     ScrollPane.this.cancelTouchFocus();
                  }
               }

               if (Math.abs(y) > 150.0F && ScrollPane.this.scrollY) {
                  ScrollPane.this.flingTimer = ScrollPane.this.flingTime;
                  ScrollPane.this.velocityY = -y;
                  if (ScrollPane.this.cancelTouchFocus) {
                     ScrollPane.this.cancelTouchFocus();
                  }
               }
            }

            @Override
            public boolean handle(Event event) {
               if (super.handle(event)) {
                  if (((InputEvent)event).getType() == InputEvent.Type.touchDown) {
                     ScrollPane.this.flingTimer = 0.0F;
                  }

                  return true;
               } else {
                  return false;
               }
            }
         };
         this.addListener(this.flickScrollListener);
         this.addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, int amount) {
               ScrollPane.this.resetFade();
               if (ScrollPane.this.scrollY) {
                  ScrollPane.this.setScrollY(ScrollPane.this.amountY + ScrollPane.this.getMouseWheelY() * amount);
               } else {
                  if (!ScrollPane.this.scrollX) {
                     return false;
                  }

                  ScrollPane.this.setScrollX(ScrollPane.this.amountX + ScrollPane.this.getMouseWheelX() * amount);
               }

               return true;
            }
         });
      }
   }

   void resetFade() {
      this.fadeAlpha = this.fadeAlphaSeconds;
      this.fadeDelay = this.fadeDelaySeconds;
   }

   public void cancelTouchFocus() {
      Stage stage = this.getStage();
      if (stage != null) {
         stage.cancelTouchFocusExcept(this.flickScrollListener, this);
      }
   }

   public void cancel() {
      this.draggingPointer = -1;
      this.touchScrollH = false;
      this.touchScrollV = false;
      this.flickScrollListener.getGestureDetector().cancel();
   }

   void clamp() {
      if (this.clamp) {
         this.scrollX(
            this.overscrollX
               ? MathUtils.clamp(this.amountX, -this.overscrollDistance, this.maxX + this.overscrollDistance)
               : MathUtils.clamp(this.amountX, 0.0F, this.maxX)
         );
         this.scrollY(
            this.overscrollY
               ? MathUtils.clamp(this.amountY, -this.overscrollDistance, this.maxY + this.overscrollDistance)
               : MathUtils.clamp(this.amountY, 0.0F, this.maxY)
         );
      }
   }

   public void setStyle(ScrollPane.ScrollPaneStyle style) {
      if (style == null) {
         throw new IllegalArgumentException("style cannot be null.");
      } else {
         this.style = style;
         this.invalidateHierarchy();
      }
   }

   public ScrollPane.ScrollPaneStyle getStyle() {
      return this.style;
   }

   @Override
   public void act(float delta) {
      super.act(delta);
      boolean panning = this.flickScrollListener.getGestureDetector().isPanning();
      boolean animating = false;
      if (this.fadeAlpha > 0.0F && this.fadeScrollBars && !panning && !this.touchScrollH && !this.touchScrollV) {
         this.fadeDelay -= delta;
         if (this.fadeDelay <= 0.0F) {
            this.fadeAlpha = Math.max(0.0F, this.fadeAlpha - delta);
         }

         animating = true;
      }

      if (this.flingTimer > 0.0F) {
         this.resetFade();
         float alpha = this.flingTimer / this.flingTime;
         this.amountX = this.amountX - this.velocityX * alpha * delta;
         this.amountY = this.amountY - this.velocityY * alpha * delta;
         this.clamp();
         if (this.amountX == -this.overscrollDistance) {
            this.velocityX = 0.0F;
         }

         if (this.amountX >= this.maxX + this.overscrollDistance) {
            this.velocityX = 0.0F;
         }

         if (this.amountY == -this.overscrollDistance) {
            this.velocityY = 0.0F;
         }

         if (this.amountY >= this.maxY + this.overscrollDistance) {
            this.velocityY = 0.0F;
         }

         this.flingTimer -= delta;
         if (this.flingTimer <= 0.0F) {
            this.velocityX = 0.0F;
            this.velocityY = 0.0F;
         }

         animating = true;
      }

      if (this.smoothScrolling
         && this.flingTimer <= 0.0F
         && !panning
         && (!this.touchScrollH || this.scrollX && this.maxX / (this.hScrollBounds.width - this.hKnobBounds.width) > this.areaWidth * 0.1F)
         && (!this.touchScrollV || this.scrollY && this.maxY / (this.vScrollBounds.height - this.vKnobBounds.height) > this.areaHeight * 0.1F)) {
         if (this.visualAmountX != this.amountX) {
            if (this.visualAmountX < this.amountX) {
               this.visualScrollX(Math.min(this.amountX, this.visualAmountX + Math.max(200.0F * delta, (this.amountX - this.visualAmountX) * 7.0F * delta)));
            } else {
               this.visualScrollX(Math.max(this.amountX, this.visualAmountX - Math.max(200.0F * delta, (this.visualAmountX - this.amountX) * 7.0F * delta)));
            }

            animating = true;
         }

         if (this.visualAmountY != this.amountY) {
            if (this.visualAmountY < this.amountY) {
               this.visualScrollY(Math.min(this.amountY, this.visualAmountY + Math.max(200.0F * delta, (this.amountY - this.visualAmountY) * 7.0F * delta)));
            } else {
               this.visualScrollY(Math.max(this.amountY, this.visualAmountY - Math.max(200.0F * delta, (this.visualAmountY - this.amountY) * 7.0F * delta)));
            }

            animating = true;
         }
      } else {
         if (this.visualAmountX != this.amountX) {
            this.visualScrollX(this.amountX);
         }

         if (this.visualAmountY != this.amountY) {
            this.visualScrollY(this.amountY);
         }
      }

      if (!panning) {
         if (this.overscrollX && this.scrollX) {
            if (this.amountX < 0.0F) {
               this.resetFade();
               this.amountX = this.amountX
                  + (this.overscrollSpeedMin + (this.overscrollSpeedMax - this.overscrollSpeedMin) * -this.amountX / this.overscrollDistance) * delta;
               if (this.amountX > 0.0F) {
                  this.scrollX(0.0F);
               }

               animating = true;
            } else if (this.amountX > this.maxX) {
               this.resetFade();
               this.amountX = this.amountX
                  - (this.overscrollSpeedMin + (this.overscrollSpeedMax - this.overscrollSpeedMin) * -(this.maxX - this.amountX) / this.overscrollDistance)
                     * delta;
               if (this.amountX < this.maxX) {
                  this.scrollX(this.maxX);
               }

               animating = true;
            }
         }

         if (this.overscrollY && this.scrollY) {
            if (this.amountY < 0.0F) {
               this.resetFade();
               this.amountY = this.amountY
                  + (this.overscrollSpeedMin + (this.overscrollSpeedMax - this.overscrollSpeedMin) * -this.amountY / this.overscrollDistance) * delta;
               if (this.amountY > 0.0F) {
                  this.scrollY(0.0F);
               }

               animating = true;
            } else if (this.amountY > this.maxY) {
               this.resetFade();
               this.amountY = this.amountY
                  - (this.overscrollSpeedMin + (this.overscrollSpeedMax - this.overscrollSpeedMin) * -(this.maxY - this.amountY) / this.overscrollDistance)
                     * delta;
               if (this.amountY < this.maxY) {
                  this.scrollY(this.maxY);
               }

               animating = true;
            }
         }
      }

      if (animating) {
         Stage stage = this.getStage();
         if (stage != null && stage.getActionsRequestRendering()) {
            Gdx.graphics.requestRendering();
         }
      }
   }

   @Override
   public void layout() {
      Drawable bg = this.style.background;
      Drawable hScrollKnob = this.style.hScrollKnob;
      Drawable vScrollKnob = this.style.vScrollKnob;
      float bgLeftWidth = 0.0F;
      float bgRightWidth = 0.0F;
      float bgTopHeight = 0.0F;
      float bgBottomHeight = 0.0F;
      if (bg != null) {
         bgLeftWidth = bg.getLeftWidth();
         bgRightWidth = bg.getRightWidth();
         bgTopHeight = bg.getTopHeight();
         bgBottomHeight = bg.getBottomHeight();
      }

      float width = this.getWidth();
      float height = this.getHeight();
      float scrollbarHeight = 0.0F;
      if (hScrollKnob != null) {
         scrollbarHeight = hScrollKnob.getMinHeight();
      }

      if (this.style.hScroll != null) {
         scrollbarHeight = Math.max(scrollbarHeight, this.style.hScroll.getMinHeight());
      }

      float scrollbarWidth = 0.0F;
      if (vScrollKnob != null) {
         scrollbarWidth = vScrollKnob.getMinWidth();
      }

      if (this.style.vScroll != null) {
         scrollbarWidth = Math.max(scrollbarWidth, this.style.vScroll.getMinWidth());
      }

      this.areaWidth = width - bgLeftWidth - bgRightWidth;
      this.areaHeight = height - bgTopHeight - bgBottomHeight;
      if (this.widget != null) {
         float widgetWidth;
         float widgetHeight;
         if (this.widget instanceof Layout) {
            Layout layout = (Layout)this.widget;
            widgetWidth = layout.getPrefWidth();
            widgetHeight = layout.getPrefHeight();
         } else {
            widgetWidth = this.widget.getWidth();
            widgetHeight = this.widget.getHeight();
         }

         this.scrollX = this.forceScrollX || widgetWidth > this.areaWidth && !this.disableX;
         this.scrollY = this.forceScrollY || widgetHeight > this.areaHeight && !this.disableY;
         boolean fade = this.fadeScrollBars;
         if (!fade) {
            if (this.scrollY) {
               this.areaWidth -= scrollbarWidth;
               if (!this.scrollX && widgetWidth > this.areaWidth && !this.disableX) {
                  this.scrollX = true;
               }
            }

            if (this.scrollX) {
               this.areaHeight -= scrollbarHeight;
               if (!this.scrollY && widgetHeight > this.areaHeight && !this.disableY) {
                  this.scrollY = true;
                  this.areaWidth -= scrollbarWidth;
               }
            }
         }

         this.widgetAreaBounds.set(bgLeftWidth, bgBottomHeight, this.areaWidth, this.areaHeight);
         if (fade) {
            if (this.scrollX && this.scrollY) {
               this.areaHeight -= scrollbarHeight;
               this.areaWidth -= scrollbarWidth;
            }
         } else if (this.scrollbarsOnTop) {
            if (this.scrollX) {
               this.widgetAreaBounds.height += scrollbarHeight;
            }

            if (this.scrollY) {
               this.widgetAreaBounds.width += scrollbarWidth;
            }
         } else {
            if (this.scrollX && this.hScrollOnBottom) {
               this.widgetAreaBounds.y += scrollbarHeight;
            }

            if (this.scrollY && !this.vScrollOnRight) {
               this.widgetAreaBounds.x += scrollbarWidth;
            }
         }

         widgetWidth = this.disableX ? this.areaWidth : Math.max(this.areaWidth, widgetWidth);
         widgetHeight = this.disableY ? this.areaHeight : Math.max(this.areaHeight, widgetHeight);
         this.maxX = widgetWidth - this.areaWidth;
         this.maxY = widgetHeight - this.areaHeight;
         if (fade && this.scrollX && this.scrollY) {
            this.maxY -= scrollbarHeight;
            this.maxX -= scrollbarWidth;
         }

         this.scrollX(MathUtils.clamp(this.amountX, 0.0F, this.maxX));
         this.scrollY(MathUtils.clamp(this.amountY, 0.0F, this.maxY));
         if (this.scrollX) {
            if (hScrollKnob != null) {
               float hScrollHeight = this.style.hScroll != null ? this.style.hScroll.getMinHeight() : hScrollKnob.getMinHeight();
               float boundsX = this.vScrollOnRight ? bgLeftWidth : bgLeftWidth + scrollbarWidth;
               float boundsY = this.hScrollOnBottom ? bgBottomHeight : height - bgTopHeight - hScrollHeight;
               this.hScrollBounds.set(boundsX, boundsY, this.areaWidth, hScrollHeight);
               if (this.variableSizeKnobs) {
                  this.hKnobBounds.width = Math.max(hScrollKnob.getMinWidth(), (float)((int)(this.hScrollBounds.width * this.areaWidth / widgetWidth)));
               } else {
                  this.hKnobBounds.width = hScrollKnob.getMinWidth();
               }

               this.hKnobBounds.height = hScrollKnob.getMinHeight();
               this.hKnobBounds.x = this.hScrollBounds.x + (int)((this.hScrollBounds.width - this.hKnobBounds.width) * this.getScrollPercentX());
               this.hKnobBounds.y = this.hScrollBounds.y;
            } else {
               this.hScrollBounds.set(0.0F, 0.0F, 0.0F, 0.0F);
               this.hKnobBounds.set(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         if (this.scrollY) {
            if (vScrollKnob != null) {
               float vScrollWidth = this.style.vScroll != null ? this.style.vScroll.getMinWidth() : vScrollKnob.getMinWidth();
               float boundsY;
               if (this.hScrollOnBottom) {
                  boundsY = height - bgTopHeight - this.areaHeight;
               } else {
                  boundsY = bgBottomHeight;
               }

               float boundsX;
               if (this.vScrollOnRight) {
                  boundsX = width - bgRightWidth - vScrollWidth;
               } else {
                  boundsX = bgLeftWidth;
               }

               this.vScrollBounds.set(boundsX, boundsY, vScrollWidth, this.areaHeight);
               this.vKnobBounds.width = vScrollKnob.getMinWidth();
               if (this.variableSizeKnobs) {
                  this.vKnobBounds.height = Math.max(vScrollKnob.getMinHeight(), (float)((int)(this.vScrollBounds.height * this.areaHeight / widgetHeight)));
               } else {
                  this.vKnobBounds.height = vScrollKnob.getMinHeight();
               }

               if (this.vScrollOnRight) {
                  this.vKnobBounds.x = width - bgRightWidth - vScrollKnob.getMinWidth();
               } else {
                  this.vKnobBounds.x = bgLeftWidth;
               }

               this.vKnobBounds.y = this.vScrollBounds.y + (int)((this.vScrollBounds.height - this.vKnobBounds.height) * (1.0F - this.getScrollPercentY()));
            } else {
               this.vScrollBounds.set(0.0F, 0.0F, 0.0F, 0.0F);
               this.vKnobBounds.set(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         this.widget.setSize(widgetWidth, widgetHeight);
         if (this.widget instanceof Layout) {
            ((Layout)this.widget).validate();
         }
      }
   }

   @Override
   public void draw(Batch batch, float parentAlpha) {
      if (this.widget != null) {
         this.validate();
         this.applyTransform(batch, this.computeTransform());
         if (this.scrollX) {
            this.hKnobBounds.x = this.hScrollBounds.x + (int)((this.hScrollBounds.width - this.hKnobBounds.width) * this.getVisualScrollPercentX());
         }

         if (this.scrollY) {
            this.vKnobBounds.y = this.vScrollBounds.y + (int)((this.vScrollBounds.height - this.vKnobBounds.height) * (1.0F - this.getVisualScrollPercentY()));
         }

         float y = this.widgetAreaBounds.y;
         if (!this.scrollY) {
            y -= (int)this.maxY;
         } else {
            y -= (int)(this.maxY - this.visualAmountY);
         }

         float x = this.widgetAreaBounds.x;
         if (this.scrollX) {
            x -= (int)this.visualAmountX;
         }

         if (!this.fadeScrollBars && this.scrollbarsOnTop) {
            if (this.scrollX && this.hScrollOnBottom) {
               float scrollbarHeight = 0.0F;
               if (this.style.hScrollKnob != null) {
                  scrollbarHeight = this.style.hScrollKnob.getMinHeight();
               }

               if (this.style.hScroll != null) {
                  scrollbarHeight = Math.max(scrollbarHeight, this.style.hScroll.getMinHeight());
               }

               y += scrollbarHeight;
            }

            if (this.scrollY && !this.vScrollOnRight) {
               float scrollbarWidth = 0.0F;
               if (this.style.hScrollKnob != null) {
                  scrollbarWidth = this.style.hScrollKnob.getMinWidth();
               }

               if (this.style.hScroll != null) {
                  scrollbarWidth = Math.max(scrollbarWidth, this.style.hScroll.getMinWidth());
               }

               x += scrollbarWidth;
            }
         }

         this.widget.setPosition(x, y);
         if (this.widget instanceof Cullable) {
            this.widgetCullingArea.x = -this.widget.getX() + this.widgetAreaBounds.x;
            this.widgetCullingArea.y = -this.widget.getY() + this.widgetAreaBounds.y;
            this.widgetCullingArea.width = this.widgetAreaBounds.width;
            this.widgetCullingArea.height = this.widgetAreaBounds.height;
            ((Cullable)this.widget).setCullingArea(this.widgetCullingArea);
         }

         Color color = this.getColor();
         batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
         if (this.style.background != null) {
            this.style.background.draw(batch, 0.0F, 0.0F, this.getWidth(), this.getHeight());
         }

         this.getStage().calculateScissors(this.widgetAreaBounds, this.scissorBounds);
         batch.flush();
         if (ScissorStack.pushScissors(this.scissorBounds)) {
            this.drawChildren(batch, parentAlpha);
            batch.flush();
            ScissorStack.popScissors();
         }

         batch.setColor(color.r, color.g, color.b, color.a * parentAlpha * Interpolation.fade.apply(this.fadeAlpha / this.fadeAlphaSeconds));
         if (this.scrollX && this.scrollY && this.style.corner != null) {
            this.style
               .corner
               .draw(batch, this.hScrollBounds.x + this.hScrollBounds.width, this.hScrollBounds.y, this.vScrollBounds.width, this.vScrollBounds.y);
         }

         if (this.scrollX) {
            if (this.style.hScroll != null) {
               this.style.hScroll.draw(batch, this.hScrollBounds.x, this.hScrollBounds.y, this.hScrollBounds.width, this.hScrollBounds.height);
            }

            if (this.style.hScrollKnob != null) {
               this.style.hScrollKnob.draw(batch, this.hKnobBounds.x, this.hKnobBounds.y, this.hKnobBounds.width, this.hKnobBounds.height);
            }
         }

         if (this.scrollY) {
            if (this.style.vScroll != null) {
               this.style.vScroll.draw(batch, this.vScrollBounds.x, this.vScrollBounds.y, this.vScrollBounds.width, this.vScrollBounds.height);
            }

            if (this.style.vScrollKnob != null) {
               this.style.vScrollKnob.draw(batch, this.vKnobBounds.x, this.vKnobBounds.y, this.vKnobBounds.width, this.vKnobBounds.height);
            }
         }

         this.resetTransform(batch);
      }
   }

   public void fling(float flingTime, float velocityX, float velocityY) {
      this.flingTimer = flingTime;
      this.velocityX = velocityX;
      this.velocityY = velocityY;
   }

   @Override
   public float getPrefWidth() {
      if (this.widget instanceof Layout) {
         float width = ((Layout)this.widget).getPrefWidth();
         if (this.style.background != null) {
            width += this.style.background.getLeftWidth() + this.style.background.getRightWidth();
         }

         if (this.forceScrollY) {
            float scrollbarWidth = 0.0F;
            if (this.style.vScrollKnob != null) {
               scrollbarWidth = this.style.vScrollKnob.getMinWidth();
            }

            if (this.style.vScroll != null) {
               scrollbarWidth = Math.max(scrollbarWidth, this.style.vScroll.getMinWidth());
            }

            width += scrollbarWidth;
         }

         return width;
      } else {
         return 150.0F;
      }
   }

   @Override
   public float getPrefHeight() {
      if (this.widget instanceof Layout) {
         float height = ((Layout)this.widget).getPrefHeight();
         if (this.style.background != null) {
            height += this.style.background.getTopHeight() + this.style.background.getBottomHeight();
         }

         if (this.forceScrollX) {
            float scrollbarHeight = 0.0F;
            if (this.style.hScrollKnob != null) {
               scrollbarHeight = this.style.hScrollKnob.getMinHeight();
            }

            if (this.style.hScroll != null) {
               scrollbarHeight = Math.max(scrollbarHeight, this.style.hScroll.getMinHeight());
            }

            height += scrollbarHeight;
         }

         return height;
      } else {
         return 150.0F;
      }
   }

   @Override
   public float getMinWidth() {
      return 0.0F;
   }

   @Override
   public float getMinHeight() {
      return 0.0F;
   }

   public void setWidget(Actor widget) {
      if (widget == this) {
         throw new IllegalArgumentException("widget cannot be the ScrollPane.");
      } else {
         if (this.widget != null) {
            super.removeActor(this.widget);
         }

         this.widget = widget;
         if (widget != null) {
            super.addActor(widget);
         }
      }
   }

   public Actor getWidget() {
      return this.widget;
   }

   /** @deprecated */
   @Override
   public void addActor(Actor actor) {
      throw new UnsupportedOperationException("Use ScrollPane#setWidget.");
   }

   /** @deprecated */
   @Override
   public void addActorAt(int index, Actor actor) {
      throw new UnsupportedOperationException("Use ScrollPane#setWidget.");
   }

   /** @deprecated */
   @Override
   public void addActorBefore(Actor actorBefore, Actor actor) {
      throw new UnsupportedOperationException("Use ScrollPane#setWidget.");
   }

   /** @deprecated */
   @Override
   public void addActorAfter(Actor actorAfter, Actor actor) {
      throw new UnsupportedOperationException("Use ScrollPane#setWidget.");
   }

   @Override
   public boolean removeActor(Actor actor) {
      if (actor != this.widget) {
         return false;
      } else {
         this.setWidget(null);
         return true;
      }
   }

   @Override
   public Actor hit(float x, float y, boolean touchable) {
      if (x < 0.0F || x >= this.getWidth() || y < 0.0F || y >= this.getHeight()) {
         return null;
      } else if (this.scrollX && this.hScrollBounds.contains(x, y)) {
         return this;
      } else {
         return (Actor)(this.scrollY && this.vScrollBounds.contains(x, y) ? this : super.hit(x, y, touchable));
      }
   }

   protected void scrollX(float pixelsX) {
      this.amountX = pixelsX;
   }

   protected void scrollY(float pixelsY) {
      this.amountY = pixelsY;
   }

   protected void visualScrollX(float pixelsX) {
      this.visualAmountX = pixelsX;
   }

   protected void visualScrollY(float pixelsY) {
      this.visualAmountY = pixelsY;
   }

   protected float getMouseWheelX() {
      return Math.min(this.areaWidth, Math.max(this.areaWidth * 0.9F, this.maxX * 0.1F) / 4.0F);
   }

   protected float getMouseWheelY() {
      return Math.min(this.areaHeight, Math.max(this.areaHeight * 0.9F, this.maxY * 0.1F) / 4.0F);
   }

   public void setScrollX(float pixels) {
      this.scrollX(MathUtils.clamp(pixels, 0.0F, this.maxX));
   }

   public float getScrollX() {
      return this.amountX;
   }

   public void setScrollY(float pixels) {
      this.scrollY(MathUtils.clamp(pixels, 0.0F, this.maxY));
   }

   public float getScrollY() {
      return this.amountY;
   }

   public void updateVisualScroll() {
      this.visualAmountX = this.amountX;
      this.visualAmountY = this.amountY;
   }

   public float getVisualScrollX() {
      return !this.scrollX ? 0.0F : this.visualAmountX;
   }

   public float getVisualScrollY() {
      return !this.scrollY ? 0.0F : this.visualAmountY;
   }

   public float getVisualScrollPercentX() {
      return MathUtils.clamp(this.visualAmountX / this.maxX, 0.0F, 1.0F);
   }

   public float getVisualScrollPercentY() {
      return MathUtils.clamp(this.visualAmountY / this.maxY, 0.0F, 1.0F);
   }

   public float getScrollPercentX() {
      return MathUtils.clamp(this.amountX / this.maxX, 0.0F, 1.0F);
   }

   public void setScrollPercentX(float percentX) {
      this.scrollX(this.maxX * MathUtils.clamp(percentX, 0.0F, 1.0F));
   }

   public float getScrollPercentY() {
      return MathUtils.clamp(this.amountY / this.maxY, 0.0F, 1.0F);
   }

   public void setScrollPercentY(float percentY) {
      this.scrollY(this.maxY * MathUtils.clamp(percentY, 0.0F, 1.0F));
   }

   public void setFlickScroll(boolean flickScroll) {
      if (this.flickScroll != flickScroll) {
         this.flickScroll = flickScroll;
         if (flickScroll) {
            this.addListener(this.flickScrollListener);
         } else {
            this.removeListener(this.flickScrollListener);
         }

         this.invalidate();
      }
   }

   public void setFlickScrollTapSquareSize(float halfTapSquareSize) {
      this.flickScrollListener.getGestureDetector().setTapSquareSize(halfTapSquareSize);
   }

   public void scrollTo(float x, float y, float width, float height) {
      this.scrollTo(x, y, width, height, false, false);
   }

   public void scrollTo(float x, float y, float width, float height, boolean centerHorizontal, boolean centerVertical) {
      float amountX = this.amountX;
      if (centerHorizontal) {
         amountX = x - this.areaWidth / 2.0F + width / 2.0F;
      } else {
         if (x + width > amountX + this.areaWidth) {
            amountX = x + width - this.areaWidth;
         }

         if (x < amountX) {
            amountX = x;
         }
      }

      this.scrollX(MathUtils.clamp(amountX, 0.0F, this.maxX));
      float amountY = this.amountY;
      if (centerVertical) {
         amountY = this.maxY - y + this.areaHeight / 2.0F - height / 2.0F;
      } else {
         if (amountY > this.maxY - y - height + this.areaHeight) {
            amountY = this.maxY - y - height + this.areaHeight;
         }

         if (amountY < this.maxY - y) {
            amountY = this.maxY - y;
         }
      }

      this.scrollY(MathUtils.clamp(amountY, 0.0F, this.maxY));
   }

   public float getMaxX() {
      return this.maxX;
   }

   public float getMaxY() {
      return this.maxY;
   }

   public float getScrollBarHeight() {
      if (!this.scrollX) {
         return 0.0F;
      } else {
         float height = 0.0F;
         if (this.style.hScrollKnob != null) {
            height = this.style.hScrollKnob.getMinHeight();
         }

         if (this.style.hScroll != null) {
            height = Math.max(height, this.style.hScroll.getMinHeight());
         }

         return height;
      }
   }

   public float getScrollBarWidth() {
      if (!this.scrollY) {
         return 0.0F;
      } else {
         float width = 0.0F;
         if (this.style.vScrollKnob != null) {
            width = this.style.vScrollKnob.getMinWidth();
         }

         if (this.style.vScroll != null) {
            width = Math.max(width, this.style.vScroll.getMinWidth());
         }

         return width;
      }
   }

   public float getScrollWidth() {
      return this.areaWidth;
   }

   public float getScrollHeight() {
      return this.areaHeight;
   }

   public boolean isScrollX() {
      return this.scrollX;
   }

   public boolean isScrollY() {
      return this.scrollY;
   }

   public void setScrollingDisabled(boolean x, boolean y) {
      this.disableX = x;
      this.disableY = y;
   }

   public boolean isScrollingDisabledX() {
      return this.disableX;
   }

   public boolean isScrollingDisabledY() {
      return this.disableY;
   }

   public boolean isLeftEdge() {
      return !this.scrollX || this.amountX <= 0.0F;
   }

   public boolean isRightEdge() {
      return !this.scrollX || this.amountX >= this.maxX;
   }

   public boolean isTopEdge() {
      return !this.scrollY || this.amountY <= 0.0F;
   }

   public boolean isBottomEdge() {
      return !this.scrollY || this.amountY >= this.maxY;
   }

   public boolean isDragging() {
      return this.draggingPointer != -1;
   }

   public boolean isPanning() {
      return this.flickScrollListener.getGestureDetector().isPanning();
   }

   public boolean isFlinging() {
      return this.flingTimer > 0.0F;
   }

   public void setVelocityX(float velocityX) {
      this.velocityX = velocityX;
   }

   public float getVelocityX() {
      return this.velocityX;
   }

   public void setVelocityY(float velocityY) {
      this.velocityY = velocityY;
   }

   public float getVelocityY() {
      return this.velocityY;
   }

   public void setOverscroll(boolean overscrollX, boolean overscrollY) {
      this.overscrollX = overscrollX;
      this.overscrollY = overscrollY;
   }

   public void setupOverscroll(float distance, float speedMin, float speedMax) {
      this.overscrollDistance = distance;
      this.overscrollSpeedMin = speedMin;
      this.overscrollSpeedMax = speedMax;
   }

   public void setForceScroll(boolean x, boolean y) {
      this.forceScrollX = x;
      this.forceScrollY = y;
   }

   public boolean isForceScrollX() {
      return this.forceScrollX;
   }

   public boolean isForceScrollY() {
      return this.forceScrollY;
   }

   public void setFlingTime(float flingTime) {
      this.flingTime = flingTime;
   }

   public void setClamp(boolean clamp) {
      this.clamp = clamp;
   }

   public void setScrollBarPositions(boolean bottom, boolean right) {
      this.hScrollOnBottom = bottom;
      this.vScrollOnRight = right;
   }

   public void setFadeScrollBars(boolean fadeScrollBars) {
      if (this.fadeScrollBars != fadeScrollBars) {
         this.fadeScrollBars = fadeScrollBars;
         if (!fadeScrollBars) {
            this.fadeAlpha = this.fadeAlphaSeconds;
         }

         this.invalidate();
      }
   }

   public void setupFadeScrollBars(float fadeAlphaSeconds, float fadeDelaySeconds) {
      this.fadeAlphaSeconds = fadeAlphaSeconds;
      this.fadeDelaySeconds = fadeDelaySeconds;
   }

   public void setSmoothScrolling(boolean smoothScrolling) {
      this.smoothScrolling = smoothScrolling;
   }

   public void setScrollbarsOnTop(boolean scrollbarsOnTop) {
      this.scrollbarsOnTop = scrollbarsOnTop;
      this.invalidate();
   }

   public boolean getVariableSizeKnobs() {
      return this.variableSizeKnobs;
   }

   public void setVariableSizeKnobs(boolean variableSizeKnobs) {
      this.variableSizeKnobs = variableSizeKnobs;
   }

   public void setCancelTouchFocus(boolean cancelTouchFocus) {
      this.cancelTouchFocus = cancelTouchFocus;
   }

   @Override
   public void drawDebug(ShapeRenderer shapes) {
      shapes.flush();
      this.applyTransform(shapes, this.computeTransform());
      if (ScissorStack.pushScissors(this.scissorBounds)) {
         this.drawDebugChildren(shapes);
         ScissorStack.popScissors();
      }

      this.resetTransform(shapes);
   }

   public static class ScrollPaneStyle {
      public Drawable background;
      public Drawable corner;
      public Drawable hScroll;
      public Drawable hScrollKnob;
      public Drawable vScroll;
      public Drawable vScrollKnob;

      public ScrollPaneStyle() {
      }

      public ScrollPaneStyle(Drawable background, Drawable hScroll, Drawable hScrollKnob, Drawable vScroll, Drawable vScrollKnob) {
         this.background = background;
         this.hScroll = hScroll;
         this.hScrollKnob = hScrollKnob;
         this.vScroll = vScroll;
         this.vScrollKnob = vScrollKnob;
      }

      public ScrollPaneStyle(ScrollPane.ScrollPaneStyle style) {
         this.background = style.background;
         this.hScroll = style.hScroll;
         this.hScrollKnob = style.hScrollKnob;
         this.vScroll = style.vScroll;
         this.vScrollKnob = style.vScrollKnob;
      }
   }
}
