package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.SnapshotArray;

public class VerticalGroup extends WidgetGroup {
   private float prefWidth;
   private float prefHeight;
   private float lastPrefWidth;
   private boolean sizeInvalid = true;
   private FloatArray columnSizes;
   private int align = 2;
   private int columnAlign;
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

   public VerticalGroup() {
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
      this.prefWidth = 0.0F;
      if (this.wrap) {
         this.prefHeight = 0.0F;
         if (this.columnSizes == null) {
            this.columnSizes = new FloatArray();
         } else {
            this.columnSizes.clear();
         }

         FloatArray columnSizes = this.columnSizes;
         float space = this.space;
         float wrapSpace = this.wrapSpace;
         float pad = this.padTop + this.padBottom;
         float groupHeight = this.getHeight() - pad;
         float x = 0.0F;
         float y = 0.0F;
         float columnWidth = 0.0F;
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

            float incrY = height + (y > 0.0F ? space : 0.0F);
            if (y + incrY > groupHeight && y > 0.0F) {
               columnSizes.add(y);
               columnSizes.add(columnWidth);
               this.prefHeight = Math.max(this.prefHeight, y + pad);
               if (x > 0.0F) {
                  x += wrapSpace;
               }

               x += columnWidth;
               columnWidth = 0.0F;
               y = 0.0F;
               incrY = height;
            }

            y += incrY;
            columnWidth = Math.max(columnWidth, width);
            i += incr;
         }

         columnSizes.add(y);
         columnSizes.add(columnWidth);
         this.prefHeight = Math.max(this.prefHeight, y + pad);
         if (x > 0.0F) {
            x += wrapSpace;
         }

         this.prefWidth = Math.max(this.prefWidth, x + columnWidth);
      } else {
         this.prefHeight = this.padTop + this.padBottom + this.space * (n - 1);

         for (int ix = 0; ix < n; ix++) {
            Actor childx = children.get(ix);
            if (childx instanceof Layout) {
               Layout layout = (Layout)childx;
               this.prefWidth = Math.max(this.prefWidth, layout.getPrefWidth());
               this.prefHeight = this.prefHeight + layout.getPrefHeight();
            } else {
               this.prefWidth = Math.max(this.prefWidth, childx.getWidth());
               this.prefHeight = this.prefHeight + childx.getHeight();
            }
         }
      }

      this.prefWidth = this.prefWidth + (this.padLeft + this.padRight);
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
         float padLeft = this.padLeft;
         float fill = this.fill;
         float columnWidth = (this.expand ? this.getWidth() : this.prefWidth) - padLeft - this.padRight;
         float y = this.prefHeight - this.padTop + space;
         if ((align & 2) != 0) {
            y += this.getHeight() - this.prefHeight;
         } else if ((align & 4) == 0) {
            y += (this.getHeight() - this.prefHeight) / 2.0F;
         }

         float startX;
         if ((align & 8) != 0) {
            startX = padLeft;
         } else if ((align & 16) != 0) {
            startX = this.getWidth() - this.padRight - columnWidth;
         } else {
            startX = padLeft + (this.getWidth() - padLeft - this.padRight - columnWidth) / 2.0F;
         }

         align = this.columnAlign;
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
               width = columnWidth * fill;
            }

            if (layout != null) {
               width = Math.max(width, layout.getMinWidth());
               float maxWidth = layout.getMaxWidth();
               if (maxWidth > 0.0F && width > maxWidth) {
                  width = maxWidth;
               }
            }

            float x = startX;
            if ((align & 16) != 0) {
               x = startX + (columnWidth - width);
            } else if ((align & 8) == 0) {
               x = startX + (columnWidth - width) / 2.0F;
            }

            y -= height + space;
            if (round) {
               child.setBounds(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
            } else {
               child.setBounds(x, y, width, height);
            }

            if (layout != null) {
               layout.validate();
            }
         }
      }
   }

   private void layoutWrapped() {
      float prefWidth = this.getPrefWidth();
      if (prefWidth != this.lastPrefWidth) {
         this.lastPrefWidth = prefWidth;
         this.invalidateHierarchy();
      }

      int align = this.align;
      boolean round = this.round;
      float space = this.space;
      float padLeft = this.padLeft;
      float fill = this.fill;
      float wrapSpace = this.wrapSpace;
      float maxHeight = this.prefHeight - this.padTop - this.padBottom;
      float columnX = padLeft;
      float groupHeight = this.getHeight();
      float yStart = this.prefHeight - this.padTop + space;
      float y = 0.0F;
      float columnWidth = 0.0F;
      if ((align & 16) != 0) {
         columnX = padLeft + (this.getWidth() - prefWidth);
      } else if ((align & 8) == 0) {
         columnX = padLeft + (this.getWidth() - prefWidth) / 2.0F;
      }

      if ((align & 2) != 0) {
         yStart += groupHeight - this.prefHeight;
      } else if ((align & 4) == 0) {
         yStart += (groupHeight - this.prefHeight) / 2.0F;
      }

      groupHeight -= this.padTop;
      align = this.columnAlign;
      FloatArray columnSizes = this.columnSizes;
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

         if (y - height - space < this.padBottom || r == 0) {
            y = yStart;
            if ((align & 4) != 0) {
               y = yStart - (maxHeight - columnSizes.get(r));
            } else if ((align & 2) == 0) {
               y = yStart - (maxHeight - columnSizes.get(r)) / 2.0F;
            }

            if (r > 0) {
               columnX += wrapSpace;
               columnX += columnWidth;
            }

            columnWidth = columnSizes.get(r + 1);
            r += 2;
         }

         if (fill > 0.0F) {
            width = columnWidth * fill;
         }

         if (layout != null) {
            width = Math.max(width, layout.getMinWidth());
            float maxWidth = layout.getMaxWidth();
            if (maxWidth > 0.0F && width > maxWidth) {
               width = maxWidth;
            }
         }

         float x = columnX;
         if ((align & 16) != 0) {
            x = columnX + (columnWidth - width);
         } else if ((align & 8) == 0) {
            x = columnX + (columnWidth - width) / 2.0F;
         }

         y -= height + space;
         if (round) {
            child.setBounds(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
         } else {
            child.setBounds(x, y, width, height);
         }

         if (layout != null) {
            layout.validate();
         }
      }
   }

   @Override
   public float getPrefWidth() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.prefWidth;
   }

   @Override
   public float getPrefHeight() {
      if (this.wrap) {
         return 0.0F;
      } else {
         if (this.sizeInvalid) {
            this.computeSize();
         }

         return this.prefHeight;
      }
   }

   public void setRound(boolean round) {
      this.round = round;
   }

   public VerticalGroup reverse() {
      this.reverse = true;
      return this;
   }

   public VerticalGroup reverse(boolean reverse) {
      this.reverse = reverse;
      return this;
   }

   public boolean getReverse() {
      return this.reverse;
   }

   public VerticalGroup space(float space) {
      this.space = space;
      return this;
   }

   public float getSpace() {
      return this.space;
   }

   public VerticalGroup wrapSpace(float wrapSpace) {
      this.wrapSpace = wrapSpace;
      return this;
   }

   public float getWrapSpace() {
      return this.wrapSpace;
   }

   public VerticalGroup pad(float pad) {
      this.padTop = pad;
      this.padLeft = pad;
      this.padBottom = pad;
      this.padRight = pad;
      return this;
   }

   public VerticalGroup pad(float top, float left, float bottom, float right) {
      this.padTop = top;
      this.padLeft = left;
      this.padBottom = bottom;
      this.padRight = right;
      return this;
   }

   public VerticalGroup padTop(float padTop) {
      this.padTop = padTop;
      return this;
   }

   public VerticalGroup padLeft(float padLeft) {
      this.padLeft = padLeft;
      return this;
   }

   public VerticalGroup padBottom(float padBottom) {
      this.padBottom = padBottom;
      return this;
   }

   public VerticalGroup padRight(float padRight) {
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

   public VerticalGroup align(int align) {
      this.align = align;
      return this;
   }

   public VerticalGroup center() {
      this.align = 1;
      return this;
   }

   public VerticalGroup top() {
      this.align |= 2;
      this.align &= -5;
      return this;
   }

   public VerticalGroup left() {
      this.align |= 8;
      this.align &= -17;
      return this;
   }

   public VerticalGroup bottom() {
      this.align |= 4;
      this.align &= -3;
      return this;
   }

   public VerticalGroup right() {
      this.align |= 16;
      this.align &= -9;
      return this;
   }

   public int getAlign() {
      return this.align;
   }

   public VerticalGroup fill() {
      this.fill = 1.0F;
      return this;
   }

   public VerticalGroup fill(float fill) {
      this.fill = fill;
      return this;
   }

   public float getFill() {
      return this.fill;
   }

   public VerticalGroup expand() {
      this.expand = true;
      return this;
   }

   public VerticalGroup expand(boolean expand) {
      this.expand = expand;
      return this;
   }

   public boolean getExpand() {
      return this.expand;
   }

   public VerticalGroup grow() {
      this.expand = true;
      this.fill = 1.0F;
      return this;
   }

   public VerticalGroup wrap() {
      this.wrap = true;
      return this;
   }

   public VerticalGroup wrap(boolean wrap) {
      this.wrap = wrap;
      return this;
   }

   public boolean getWrap() {
      return this.wrap;
   }

   public VerticalGroup columnAlign(int columnAlign) {
      this.columnAlign = columnAlign;
      return this;
   }

   public VerticalGroup columnCenter() {
      this.columnAlign = 1;
      return this;
   }

   public VerticalGroup columnLeft() {
      this.columnAlign |= 8;
      this.columnAlign &= -17;
      return this;
   }

   public VerticalGroup columnRight() {
      this.columnAlign |= 16;
      this.columnAlign &= -9;
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
