package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.SnapshotArray;

public class HorizontalGroup extends WidgetGroup {
   private float prefWidth;
   private float prefHeight;
   private float lastPrefHeight;
   private boolean sizeInvalid = true;
   private FloatArray rowSizes;
   private int align = 8;
   private int rowAlign;
   private boolean reverse;
   private boolean round = true;
   private boolean wrap;
   private boolean expand;
   private float space;
   private float wrapSpace;
   private float fill;
   private float padTop;
   private float padLeft;
   private float padBottom;
   private float padRight;

   public HorizontalGroup() {
      this.setTouchable(Touchable.childrenOnly);
   }

   @Override
   public void invalidate() {
      super.invalidate();
      this.sizeInvalid = true;
   }

   private void computeSize() {
      this.sizeInvalid = false;
      SnapshotArray<Actor> children = this.getChildren();
      int n = children.size;
      this.prefHeight = 0.0F;
      if (this.wrap) {
         this.prefWidth = 0.0F;
         if (this.rowSizes == null) {
            this.rowSizes = new FloatArray();
         } else {
            this.rowSizes.clear();
         }

         FloatArray rowSizes = this.rowSizes;
         float space = this.space;
         float wrapSpace = this.wrapSpace;
         float pad = this.padLeft + this.padRight;
         float groupWidth = this.getWidth() - pad;
         float x = 0.0F;
         float y = 0.0F;
         float rowHeight = 0.0F;
         int i = 0;
         int incr = 1;
         if (this.reverse) {
            i = n - 1;
            n = -1;
            incr = -1;
         }

         while (i != n) {
            Actor child = children.get(i);
            float width;
            float height;
            if (child instanceof Layout) {
               Layout layout = (Layout)child;
               width = layout.getPrefWidth();
               height = layout.getPrefHeight();
            } else {
               width = child.getWidth();
               height = child.getHeight();
            }

            float incrX = width + (x > 0.0F ? space : 0.0F);
            if (x + incrX > groupWidth && x > 0.0F) {
               rowSizes.add(x);
               rowSizes.add(rowHeight);
               this.prefWidth = Math.max(this.prefWidth, x + pad);
               if (y > 0.0F) {
                  y += wrapSpace;
               }

               y += rowHeight;
               rowHeight = 0.0F;
               x = 0.0F;
               incrX = width;
            }

            x += incrX;
            rowHeight = Math.max(rowHeight, height);
            i += incr;
         }

         rowSizes.add(x);
         rowSizes.add(rowHeight);
         this.prefWidth = Math.max(this.prefWidth, x + pad);
         if (y > 0.0F) {
            y += wrapSpace;
         }

         this.prefHeight = Math.max(this.prefHeight, y + rowHeight);
      } else {
         this.prefWidth = this.padLeft + this.padRight + this.space * (n - 1);

         for (int ix = 0; ix < n; ix++) {
            Actor childx = children.get(ix);
            if (childx instanceof Layout) {
               Layout layout = (Layout)childx;
               this.prefWidth = this.prefWidth + layout.getPrefWidth();
               this.prefHeight = Math.max(this.prefHeight, layout.getPrefHeight());
            } else {
               this.prefWidth = this.prefWidth + childx.getWidth();
               this.prefHeight = Math.max(this.prefHeight, childx.getHeight());
            }
         }
      }

      this.prefHeight = this.prefHeight + (this.padTop + this.padBottom);
      if (this.round) {
         this.prefWidth = Math.round(this.prefWidth);
         this.prefHeight = Math.round(this.prefHeight);
      }
   }

   @Override
   public void layout() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      if (this.wrap) {
         this.layoutWrapped();
      } else {
         boolean round = this.round;
         int align = this.align;
         float space = this.space;
         float padBottom = this.padBottom;
         float fill = this.fill;
         float rowHeight = (this.expand ? this.getHeight() : this.prefHeight) - this.padTop - padBottom;
         float x = this.padLeft;
         if ((align & 16) != 0) {
            x += this.getWidth() - this.prefWidth;
         } else if ((align & 8) == 0) {
            x += (this.getWidth() - this.prefWidth) / 2.0F;
         }

         float startY;
         if ((align & 4) != 0) {
            startY = padBottom;
         } else if ((align & 2) != 0) {
            startY = this.getHeight() - this.padTop - rowHeight;
         } else {
            startY = padBottom + (this.getHeight() - padBottom - this.padTop - rowHeight) / 2.0F;
         }

         align = this.rowAlign;
         SnapshotArray<Actor> children = this.getChildren();
         int i = 0;
         int n = children.size;
         int incr = 1;
         if (this.reverse) {
            i = n - 1;
            n = -1;
            incr = -1;
         }

         for (int r = 0; i != n; i += incr) {
            Actor child = children.get(i);
            Layout layout = null;
            float width;
            float height;
            if (child instanceof Layout) {
               layout = (Layout)child;
               width = layout.getPrefWidth();
               height = layout.getPrefHeight();
            } else {
               width = child.getWidth();
               height = child.getHeight();
            }

            if (fill > 0.0F) {
               height = rowHeight * fill;
            }

            if (layout != null) {
               height = Math.max(height, layout.getMinHeight());
               float maxHeight = layout.getMaxHeight();
               if (maxHeight > 0.0F && height > maxHeight) {
                  height = maxHeight;
               }
            }

            float y = startY;
            if ((align & 2) != 0) {
               y = startY + (rowHeight - height);
            } else if ((align & 4) == 0) {
               y = startY + (rowHeight - height) / 2.0F;
            }

            if (round) {
               child.setBounds(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
            } else {
               child.setBounds(x, y, width, height);
            }

            x += width + space;
            if (layout != null) {
               layout.validate();
            }
         }
      }
   }

   private void layoutWrapped() {
      float prefHeight = this.getPrefHeight();
      if (prefHeight != this.lastPrefHeight) {
         this.lastPrefHeight = prefHeight;
         this.invalidateHierarchy();
      }

      int align = this.align;
      boolean round = this.round;
      float space = this.space;
      float padBottom = this.padBottom;
      float fill = this.fill;
      float wrapSpace = this.wrapSpace;
      float maxWidth = this.prefWidth - this.padLeft - this.padRight;
      float rowY = prefHeight - this.padTop;
      float groupWidth = this.getWidth();
      float xStart = this.padLeft;
      float x = 0.0F;
      float rowHeight = 0.0F;
      if ((align & 2) != 0) {
         rowY += this.getHeight() - prefHeight;
      } else if ((align & 4) == 0) {
         rowY += (this.getHeight() - prefHeight) / 2.0F;
      }

      if ((align & 16) != 0) {
         xStart += groupWidth - this.prefWidth;
      } else if ((align & 8) == 0) {
         xStart += (groupWidth - this.prefWidth) / 2.0F;
      }

      groupWidth -= this.padRight;
      align = this.rowAlign;
      FloatArray rowSizes = this.rowSizes;
      SnapshotArray<Actor> children = this.getChildren();
      int i = 0;
      int n = children.size;
      int incr = 1;
      if (this.reverse) {
         i = n - 1;
         n = -1;
         incr = -1;
      }

      for (int r = 0; i != n; i += incr) {
         Actor child = children.get(i);
         Layout layout = null;
         float width;
         float height;
         if (child instanceof Layout) {
            layout = (Layout)child;
            width = layout.getPrefWidth();
            height = layout.getPrefHeight();
         } else {
            width = child.getWidth();
            height = child.getHeight();
         }

         if (x + width > groupWidth || r == 0) {
            x = xStart;
            if ((align & 16) != 0) {
               x = xStart + (maxWidth - rowSizes.get(r));
            } else if ((align & 8) == 0) {
               x = xStart + (maxWidth - rowSizes.get(r)) / 2.0F;
            }

            rowHeight = rowSizes.get(r + 1);
            if (r > 0) {
               rowY -= wrapSpace;
            }

            rowY -= rowHeight;
            r += 2;
         }

         if (fill > 0.0F) {
            height = rowHeight * fill;
         }

         if (layout != null) {
            height = Math.max(height, layout.getMinHeight());
            float maxHeight = layout.getMaxHeight();
            if (maxHeight > 0.0F && height > maxHeight) {
               height = maxHeight;
            }
         }

         float y = rowY;
         if ((align & 2) != 0) {
            y = rowY + (rowHeight - height);
         } else if ((align & 4) == 0) {
            y = rowY + (rowHeight - height) / 2.0F;
         }

         if (round) {
            child.setBounds(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
         } else {
            child.setBounds(x, y, width, height);
         }

         x += width + space;
         if (layout != null) {
            layout.validate();
         }
      }
   }

   @Override
   public float getPrefWidth() {
      if (this.wrap) {
         return 0.0F;
      } else {
         if (this.sizeInvalid) {
            this.computeSize();
         }

         return this.prefWidth;
      }
   }

   @Override
   public float getPrefHeight() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.prefHeight;
   }

   public void setRound(boolean round) {
      this.round = round;
   }

   public HorizontalGroup reverse() {
      this.reverse = true;
      return this;
   }

   public HorizontalGroup reverse(boolean reverse) {
      this.reverse = reverse;
      return this;
   }

   public boolean getReverse() {
      return this.reverse;
   }

   public HorizontalGroup space(float space) {
      this.space = space;
      return this;
   }

   public float getSpace() {
      return this.space;
   }

   public HorizontalGroup wrapSpace(float wrapSpace) {
      this.wrapSpace = wrapSpace;
      return this;
   }

   public float getWrapSpace() {
      return this.wrapSpace;
   }

   public HorizontalGroup pad(float pad) {
      this.padTop = pad;
      this.padLeft = pad;
      this.padBottom = pad;
      this.padRight = pad;
      return this;
   }

   public HorizontalGroup pad(float top, float left, float bottom, float right) {
      this.padTop = top;
      this.padLeft = left;
      this.padBottom = bottom;
      this.padRight = right;
      return this;
   }

   public HorizontalGroup padTop(float padTop) {
      this.padTop = padTop;
      return this;
   }

   public HorizontalGroup padLeft(float padLeft) {
      this.padLeft = padLeft;
      return this;
   }

   public HorizontalGroup padBottom(float padBottom) {
      this.padBottom = padBottom;
      return this;
   }

   public HorizontalGroup padRight(float padRight) {
      this.padRight = padRight;
      return this;
   }

   public float getPadTop() {
      return this.padTop;
   }

   public float getPadLeft() {
      return this.padLeft;
   }

   public float getPadBottom() {
      return this.padBottom;
   }

   public float getPadRight() {
      return this.padRight;
   }

   public HorizontalGroup align(int align) {
      this.align = align;
      return this;
   }

   public HorizontalGroup center() {
      this.align = 1;
      return this;
   }

   public HorizontalGroup top() {
      this.align |= 2;
      this.align &= -5;
      return this;
   }

   public HorizontalGroup left() {
      this.align |= 8;
      this.align &= -17;
      return this;
   }

   public HorizontalGroup bottom() {
      this.align |= 4;
      this.align &= -3;
      return this;
   }

   public HorizontalGroup right() {
      this.align |= 16;
      this.align &= -9;
      return this;
   }

   public int getAlign() {
      return this.align;
   }

   public HorizontalGroup fill() {
      this.fill = 1.0F;
      return this;
   }

   public HorizontalGroup fill(float fill) {
      this.fill = fill;
      return this;
   }

   public float getFill() {
      return this.fill;
   }

   public HorizontalGroup expand() {
      this.expand = true;
      return this;
   }

   public HorizontalGroup expand(boolean expand) {
      this.expand = expand;
      return this;
   }

   public boolean getExpand() {
      return this.expand;
   }

   public HorizontalGroup grow() {
      this.expand = true;
      this.fill = 1.0F;
      return this;
   }

   public HorizontalGroup wrap() {
      this.wrap = true;
      return this;
   }

   public HorizontalGroup wrap(boolean wrap) {
      this.wrap = wrap;
      return this;
   }

   public boolean getWrap() {
      return this.wrap;
   }

   public HorizontalGroup rowAlign(int row) {
      this.rowAlign = row;
      return this;
   }

   public HorizontalGroup rowCenter() {
      this.rowAlign = 1;
      return this;
   }

   public HorizontalGroup rowTop() {
      this.rowAlign |= 2;
      this.rowAlign &= -5;
      return this;
   }

   public HorizontalGroup rowBottom() {
      this.rowAlign |= 4;
      this.rowAlign &= -3;
      return this;
   }

   @Override
   protected void drawDebugBounds(ShapeRenderer shapes) {
      super.drawDebugBounds(shapes);
      if (this.getDebug()) {
         shapes.set(ShapeRenderer.ShapeType.Line);
         shapes.setColor(this.getStage().getDebugColor());
         shapes.rect(
            this.getX() + this.padLeft,
            this.getY() + this.padBottom,
            this.getOriginX(),
            this.getOriginY(),
            this.getWidth() - this.padLeft - this.padRight,
            this.getHeight() - this.padBottom - this.padTop,
            this.getScaleX(),
            this.getScaleY(),
            this.getRotation()
         );
      }
   }
}
