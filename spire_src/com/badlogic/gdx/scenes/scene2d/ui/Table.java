package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

public class Table extends WidgetGroup {
   public static Color debugTableColor = new Color(0.0F, 0.0F, 1.0F, 1.0F);
   public static Color debugCellColor = new Color(1.0F, 0.0F, 0.0F, 1.0F);
   public static Color debugActorColor = new Color(0.0F, 1.0F, 0.0F, 1.0F);
   static final Pool<Cell> cellPool = new Pool<Cell>() {
      protected Cell newObject() {
         return new Cell();
      }
   };
   private static float[] columnWeightedWidth;
   private static float[] rowWeightedHeight;
   private int columns;
   private int rows;
   private boolean implicitEndRow;
   private final Array<Cell> cells = new Array<>(4);
   private final Cell cellDefaults;
   private final Array<Cell> columnDefaults = new Array<>(2);
   private Cell rowDefaults;
   private boolean sizeInvalid = true;
   private float[] columnMinWidth;
   private float[] rowMinHeight;
   private float[] columnPrefWidth;
   private float[] rowPrefHeight;
   private float tableMinWidth;
   private float tableMinHeight;
   private float tablePrefWidth;
   private float tablePrefHeight;
   private float[] columnWidth;
   private float[] rowHeight;
   private float[] expandWidth;
   private float[] expandHeight;
   Value padTop;
   Value padLeft;
   Value padBottom;
   Value padRight;
   int align;
   Table.Debug debug;
   Array<Table.DebugRect> debugRects;
   Drawable background;
   private boolean clip;
   private Skin skin;
   boolean round;
   public static Value backgroundTop = new Value() {
      @Override
      public float get(Actor context) {
         Drawable background = ((Table)context).background;
         return background == null ? 0.0F : background.getTopHeight();
      }
   };
   public static Value backgroundLeft = new Value() {
      @Override
      public float get(Actor context) {
         Drawable background = ((Table)context).background;
         return background == null ? 0.0F : background.getLeftWidth();
      }
   };
   public static Value backgroundBottom = new Value() {
      @Override
      public float get(Actor context) {
         Drawable background = ((Table)context).background;
         return background == null ? 0.0F : background.getBottomHeight();
      }
   };
   public static Value backgroundRight = new Value() {
      @Override
      public float get(Actor context) {
         Drawable background = ((Table)context).background;
         return background == null ? 0.0F : background.getRightWidth();
      }
   };

   public Table() {
      this(null);
   }

   public Table(Skin skin) {
      this.padTop = backgroundTop;
      this.padLeft = backgroundLeft;
      this.padBottom = backgroundBottom;
      this.padRight = backgroundRight;
      this.align = 1;
      this.debug = Table.Debug.none;
      this.round = true;
      this.skin = skin;
      this.cellDefaults = this.obtainCell();
      this.setTransform(false);
      this.setTouchable(Touchable.childrenOnly);
   }

   private Cell obtainCell() {
      Cell cell = cellPool.obtain();
      cell.setLayout(this);
      return cell;
   }

   @Override
   public void draw(Batch batch, float parentAlpha) {
      this.validate();
      if (this.isTransform()) {
         this.applyTransform(batch, this.computeTransform());
         this.drawBackground(batch, parentAlpha, 0.0F, 0.0F);
         if (this.clip) {
            batch.flush();
            float padLeft = this.padLeft.get(this);
            float padBottom = this.padBottom.get(this);
            if (this.clipBegin(padLeft, padBottom, this.getWidth() - padLeft - this.padRight.get(this), this.getHeight() - padBottom - this.padTop.get(this))) {
               this.drawChildren(batch, parentAlpha);
               batch.flush();
               this.clipEnd();
            }
         } else {
            this.drawChildren(batch, parentAlpha);
         }

         this.resetTransform(batch);
      } else {
         this.drawBackground(batch, parentAlpha, this.getX(), this.getY());
         super.draw(batch, parentAlpha);
      }
   }

   protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
      if (this.background != null) {
         Color color = this.getColor();
         batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
         this.background.draw(batch, x, y, this.getWidth(), this.getHeight());
      }
   }

   public void setBackground(String drawableName) {
      if (this.skin == null) {
         throw new IllegalStateException("Table must have a skin set to use this method.");
      } else {
         this.setBackground(this.skin.getDrawable(drawableName));
      }
   }

   public void setBackground(Drawable background) {
      if (this.background != background) {
         float padTopOld = this.getPadTop();
         float padLeftOld = this.getPadLeft();
         float padBottomOld = this.getPadBottom();
         float padRightOld = this.getPadRight();
         this.background = background;
         float padTopNew = this.getPadTop();
         float padLeftNew = this.getPadLeft();
         float padBottomNew = this.getPadBottom();
         float padRightNew = this.getPadRight();
         if (padTopOld + padBottomOld != padTopNew + padBottomNew || padLeftOld + padRightOld != padLeftNew + padRightNew) {
            this.invalidateHierarchy();
         } else if (padTopOld != padTopNew || padLeftOld != padLeftNew || padBottomOld != padBottomNew || padRightOld != padRightNew) {
            this.invalidate();
         }
      }
   }

   public Table background(Drawable background) {
      this.setBackground(background);
      return this;
   }

   public Table background(String drawableName) {
      this.setBackground(drawableName);
      return this;
   }

   public Drawable getBackground() {
      return this.background;
   }

   @Override
   public Actor hit(float x, float y, boolean touchable) {
      if (this.clip) {
         if (touchable && this.getTouchable() == Touchable.disabled) {
            return null;
         }

         if (x < 0.0F || x >= this.getWidth() || y < 0.0F || y >= this.getHeight()) {
            return null;
         }
      }

      return super.hit(x, y, touchable);
   }

   public void setClip(boolean enabled) {
      this.clip = enabled;
      this.setTransform(enabled);
      this.invalidate();
   }

   public boolean getClip() {
      return this.clip;
   }

   @Override
   public void invalidate() {
      this.sizeInvalid = true;
      super.invalidate();
   }

   public <T extends Actor> Cell<T> add(T actor) {
      Cell<T> cell = this.obtainCell();
      cell.actor = actor;
      if (this.implicitEndRow) {
         this.implicitEndRow = false;
         this.rows--;
         this.cells.peek().endRow = false;
      }

      Array<Cell> cells = this.cells;
      int cellCount = cells.size;
      if (cellCount > 0) {
         Cell lastCell = cells.peek();
         if (!lastCell.endRow) {
            cell.column = lastCell.column + lastCell.colspan;
            cell.row = lastCell.row;
         } else {
            cell.column = 0;
            cell.row = lastCell.row + 1;
         }

         if (cell.row > 0) {
            label45:
            for (int i = cellCount - 1; i >= 0; i--) {
               Cell other = cells.get(i);
               int column = other.column;

               for (int nn = column + other.colspan; column < nn; column++) {
                  if (column == cell.column) {
                     cell.cellAboveIndex = i;
                     break label45;
                  }
               }
            }
         }
      } else {
         cell.column = 0;
         cell.row = 0;
      }

      cells.add(cell);
      cell.set(this.cellDefaults);
      if (cell.column < this.columnDefaults.size) {
         Cell columnCell = this.columnDefaults.get(cell.column);
         if (columnCell != null) {
            cell.merge(columnCell);
         }
      }

      cell.merge(this.rowDefaults);
      if (actor != null) {
         this.addActor(actor);
      }

      return cell;
   }

   public void add(Actor... actors) {
      int i = 0;

      for (int n = actors.length; i < n; i++) {
         this.add(actors[i]);
      }
   }

   public Cell<Label> add(CharSequence text) {
      if (this.skin == null) {
         throw new IllegalStateException("Table must have a skin set to use this method.");
      } else {
         return this.add(new Label(text, this.skin));
      }
   }

   public Cell<Label> add(CharSequence text, String labelStyleName) {
      if (this.skin == null) {
         throw new IllegalStateException("Table must have a skin set to use this method.");
      } else {
         return this.add(new Label(text, this.skin.get(labelStyleName, Label.LabelStyle.class)));
      }
   }

   public Cell<Label> add(CharSequence text, String fontName, Color color) {
      if (this.skin == null) {
         throw new IllegalStateException("Table must have a skin set to use this method.");
      } else {
         return this.add(new Label(text, new Label.LabelStyle(this.skin.getFont(fontName), color)));
      }
   }

   public Cell<Label> add(CharSequence text, String fontName, String colorName) {
      if (this.skin == null) {
         throw new IllegalStateException("Table must have a skin set to use this method.");
      } else {
         return this.add(new Label(text, new Label.LabelStyle(this.skin.getFont(fontName), this.skin.getColor(colorName))));
      }
   }

   public Cell add() {
      return this.add((Actor)null);
   }

   public Cell<Stack> stack(Actor... actors) {
      Stack stack = new Stack();
      if (actors != null) {
         int i = 0;

         for (int n = actors.length; i < n; i++) {
            stack.addActor(actors[i]);
         }
      }

      return this.add(stack);
   }

   @Override
   public boolean removeActor(Actor actor) {
      return this.removeActor(actor, true);
   }

   @Override
   public boolean removeActor(Actor actor, boolean unfocus) {
      if (!super.removeActor(actor, unfocus)) {
         return false;
      } else {
         Cell cell = this.getCell(actor);
         if (cell != null) {
            cell.actor = null;
         }

         return true;
      }
   }

   @Override
   public void clearChildren() {
      Array<Cell> cells = this.cells;

      for (int i = cells.size - 1; i >= 0; i--) {
         Cell cell = cells.get(i);
         Actor actor = cell.actor;
         if (actor != null) {
            actor.remove();
         }
      }

      cellPool.freeAll(cells);
      cells.clear();
      this.rows = 0;
      this.columns = 0;
      if (this.rowDefaults != null) {
         cellPool.free(this.rowDefaults);
      }

      this.rowDefaults = null;
      this.implicitEndRow = false;
      super.clearChildren();
   }

   public void reset() {
      this.clearChildren();
      this.padTop = backgroundTop;
      this.padLeft = backgroundLeft;
      this.padBottom = backgroundBottom;
      this.padRight = backgroundRight;
      this.align = 1;
      this.debug(Table.Debug.none);
      this.cellDefaults.reset();
      int i = 0;

      for (int n = this.columnDefaults.size; i < n; i++) {
         Cell columnCell = this.columnDefaults.get(i);
         if (columnCell != null) {
            cellPool.free(columnCell);
         }
      }

      this.columnDefaults.clear();
   }

   public Cell row() {
      if (this.cells.size > 0) {
         if (!this.implicitEndRow) {
            this.endRow();
         }

         this.invalidate();
      }

      this.implicitEndRow = false;
      if (this.rowDefaults != null) {
         cellPool.free(this.rowDefaults);
      }

      this.rowDefaults = this.obtainCell();
      this.rowDefaults.clear();
      return this.rowDefaults;
   }

   private void endRow() {
      Array<Cell> cells = this.cells;
      int rowColumns = 0;

      for (int i = cells.size - 1; i >= 0; i--) {
         Cell cell = cells.get(i);
         if (cell.endRow) {
            break;
         }

         rowColumns += cell.colspan;
      }

      this.columns = Math.max(this.columns, rowColumns);
      this.rows++;
      cells.peek().endRow = true;
   }

   public Cell columnDefaults(int column) {
      Cell cell = this.columnDefaults.size > column ? this.columnDefaults.get(column) : null;
      if (cell == null) {
         cell = this.obtainCell();
         cell.clear();
         if (column >= this.columnDefaults.size) {
            for (int i = this.columnDefaults.size; i < column; i++) {
               this.columnDefaults.add(null);
            }

            this.columnDefaults.add(cell);
         } else {
            this.columnDefaults.set(column, cell);
         }
      }

      return cell;
   }

   public <T extends Actor> Cell<T> getCell(T actor) {
      Array<Cell> cells = this.cells;
      int i = 0;

      for (int n = cells.size; i < n; i++) {
         Cell c = cells.get(i);
         if (c.actor == actor) {
            return c;
         }
      }

      return null;
   }

   public Array<Cell> getCells() {
      return this.cells;
   }

   @Override
   public float getPrefWidth() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      float width = this.tablePrefWidth;
      return this.background != null ? Math.max(width, this.background.getMinWidth()) : width;
   }

   @Override
   public float getPrefHeight() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      float height = this.tablePrefHeight;
      return this.background != null ? Math.max(height, this.background.getMinHeight()) : height;
   }

   @Override
   public float getMinWidth() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.tableMinWidth;
   }

   @Override
   public float getMinHeight() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.tableMinHeight;
   }

   public Cell defaults() {
      return this.cellDefaults;
   }

   public Table pad(Value pad) {
      if (pad == null) {
         throw new IllegalArgumentException("pad cannot be null.");
      } else {
         this.padTop = pad;
         this.padLeft = pad;
         this.padBottom = pad;
         this.padRight = pad;
         this.sizeInvalid = true;
         return this;
      }
   }

   public Table pad(Value top, Value left, Value bottom, Value right) {
      if (top == null) {
         throw new IllegalArgumentException("top cannot be null.");
      } else if (left == null) {
         throw new IllegalArgumentException("left cannot be null.");
      } else if (bottom == null) {
         throw new IllegalArgumentException("bottom cannot be null.");
      } else if (right == null) {
         throw new IllegalArgumentException("right cannot be null.");
      } else {
         this.padTop = top;
         this.padLeft = left;
         this.padBottom = bottom;
         this.padRight = right;
         this.sizeInvalid = true;
         return this;
      }
   }

   public Table padTop(Value padTop) {
      if (padTop == null) {
         throw new IllegalArgumentException("padTop cannot be null.");
      } else {
         this.padTop = padTop;
         this.sizeInvalid = true;
         return this;
      }
   }

   public Table padLeft(Value padLeft) {
      if (padLeft == null) {
         throw new IllegalArgumentException("padLeft cannot be null.");
      } else {
         this.padLeft = padLeft;
         this.sizeInvalid = true;
         return this;
      }
   }

   public Table padBottom(Value padBottom) {
      if (padBottom == null) {
         throw new IllegalArgumentException("padBottom cannot be null.");
      } else {
         this.padBottom = padBottom;
         this.sizeInvalid = true;
         return this;
      }
   }

   public Table padRight(Value padRight) {
      if (padRight == null) {
         throw new IllegalArgumentException("padRight cannot be null.");
      } else {
         this.padRight = padRight;
         this.sizeInvalid = true;
         return this;
      }
   }

   public Table pad(float pad) {
      this.pad(new Value.Fixed(pad));
      return this;
   }

   public Table pad(float top, float left, float bottom, float right) {
      this.padTop = new Value.Fixed(top);
      this.padLeft = new Value.Fixed(left);
      this.padBottom = new Value.Fixed(bottom);
      this.padRight = new Value.Fixed(right);
      this.sizeInvalid = true;
      return this;
   }

   public Table padTop(float padTop) {
      this.padTop = new Value.Fixed(padTop);
      this.sizeInvalid = true;
      return this;
   }

   public Table padLeft(float padLeft) {
      this.padLeft = new Value.Fixed(padLeft);
      this.sizeInvalid = true;
      return this;
   }

   public Table padBottom(float padBottom) {
      this.padBottom = new Value.Fixed(padBottom);
      this.sizeInvalid = true;
      return this;
   }

   public Table padRight(float padRight) {
      this.padRight = new Value.Fixed(padRight);
      this.sizeInvalid = true;
      return this;
   }

   public Table align(int align) {
      this.align = align;
      return this;
   }

   public Table center() {
      this.align = 1;
      return this;
   }

   public Table top() {
      this.align |= 2;
      this.align &= -5;
      return this;
   }

   public Table left() {
      this.align |= 8;
      this.align &= -17;
      return this;
   }

   public Table bottom() {
      this.align |= 4;
      this.align &= -3;
      return this;
   }

   public Table right() {
      this.align |= 16;
      this.align &= -9;
      return this;
   }

   @Override
   public void setDebug(boolean enabled) {
      this.debug(enabled ? Table.Debug.all : Table.Debug.none);
   }

   public Table debug() {
      super.debug();
      return this;
   }

   public Table debugAll() {
      super.debugAll();
      return this;
   }

   public Table debugTable() {
      super.setDebug(true);
      if (this.debug != Table.Debug.table) {
         this.debug = Table.Debug.table;
         this.invalidate();
      }

      return this;
   }

   public Table debugCell() {
      super.setDebug(true);
      if (this.debug != Table.Debug.cell) {
         this.debug = Table.Debug.cell;
         this.invalidate();
      }

      return this;
   }

   public Table debugActor() {
      super.setDebug(true);
      if (this.debug != Table.Debug.actor) {
         this.debug = Table.Debug.actor;
         this.invalidate();
      }

      return this;
   }

   public Table debug(Table.Debug debug) {
      super.setDebug(debug != Table.Debug.none);
      if (this.debug != debug) {
         this.debug = debug;
         if (debug == Table.Debug.none) {
            this.clearDebugRects();
         } else {
            this.invalidate();
         }
      }

      return this;
   }

   public Table.Debug getTableDebug() {
      return this.debug;
   }

   public Value getPadTopValue() {
      return this.padTop;
   }

   public float getPadTop() {
      return this.padTop.get(this);
   }

   public Value getPadLeftValue() {
      return this.padLeft;
   }

   public float getPadLeft() {
      return this.padLeft.get(this);
   }

   public Value getPadBottomValue() {
      return this.padBottom;
   }

   public float getPadBottom() {
      return this.padBottom.get(this);
   }

   public Value getPadRightValue() {
      return this.padRight;
   }

   public float getPadRight() {
      return this.padRight.get(this);
   }

   public float getPadX() {
      return this.padLeft.get(this) + this.padRight.get(this);
   }

   public float getPadY() {
      return this.padTop.get(this) + this.padBottom.get(this);
   }

   public int getAlign() {
      return this.align;
   }

   public int getRow(float y) {
      Array<Cell> cells = this.cells;
      int row = 0;
      y += this.getPadTop();
      int i = 0;
      int n = cells.size;
      if (n == 0) {
         return -1;
      } else if (n == 1) {
         return 0;
      } else {
         while (i < n) {
            Cell c = cells.get(i++);
            if (c.actorY + c.computedPadTop < y) {
               break;
            }

            if (c.endRow) {
               row++;
            }
         }

         return row;
      }
   }

   public void setSkin(Skin skin) {
      this.skin = skin;
   }

   public void setRound(boolean round) {
      this.round = round;
   }

   public int getRows() {
      return this.rows;
   }

   public int getColumns() {
      return this.columns;
   }

   public float getRowHeight(int rowIndex) {
      return this.rowHeight[rowIndex];
   }

   public float getColumnWidth(int columnIndex) {
      return this.columnWidth[columnIndex];
   }

   private float[] ensureSize(float[] array, int size) {
      if (array != null && array.length >= size) {
         int i = 0;

         for (int n = array.length; i < n; i++) {
            array[i] = 0.0F;
         }

         return array;
      } else {
         return new float[size];
      }
   }

   @Override
   public void layout() {
      float width = this.getWidth();
      float height = this.getHeight();
      this.layout(0.0F, 0.0F, width, height);
      Array<Cell> cells = this.cells;
      if (this.round) {
         int i = 0;

         for (int n = cells.size; i < n; i++) {
            Cell c = cells.get(i);
            float actorWidth = Math.round(c.actorWidth);
            float actorHeight = Math.round(c.actorHeight);
            float actorX = Math.round(c.actorX);
            float actorY = height - Math.round(c.actorY) - actorHeight;
            c.setActorBounds(actorX, actorY, actorWidth, actorHeight);
            Actor actor = c.actor;
            if (actor != null) {
               actor.setBounds(actorX, actorY, actorWidth, actorHeight);
            }
         }
      } else {
         int i = 0;

         for (int nx = cells.size; i < nx; i++) {
            Cell c = cells.get(i);
            float actorHeight = c.actorHeight;
            float actorY = height - c.actorY - actorHeight;
            c.setActorY(actorY);
            Actor actor = c.actor;
            if (actor != null) {
               actor.setBounds(c.actorX, actorY, c.actorWidth, actorHeight);
            }
         }
      }

      Array<Actor> children = this.getChildren();
      int i = 0;

      for (int nxx = children.size; i < nxx; i++) {
         Actor child = children.get(i);
         if (child instanceof Layout) {
            ((Layout)child).validate();
         }
      }
   }

   private void computeSize() {
      this.sizeInvalid = false;
      Array<Cell> cells = this.cells;
      int cellCount = cells.size;
      if (cellCount > 0 && !cells.peek().endRow) {
         this.endRow();
         this.implicitEndRow = true;
      }

      int columns = this.columns;
      int rows = this.rows;
      float[] columnMinWidth = this.columnMinWidth = this.ensureSize(this.columnMinWidth, columns);
      float[] rowMinHeight = this.rowMinHeight = this.ensureSize(this.rowMinHeight, rows);
      float[] columnPrefWidth = this.columnPrefWidth = this.ensureSize(this.columnPrefWidth, columns);
      float[] rowPrefHeight = this.rowPrefHeight = this.ensureSize(this.rowPrefHeight, rows);
      float[] columnWidth = this.columnWidth = this.ensureSize(this.columnWidth, columns);
      float[] rowHeight = this.rowHeight = this.ensureSize(this.rowHeight, rows);
      float[] expandWidth = this.expandWidth = this.ensureSize(this.expandWidth, columns);
      float[] expandHeight = this.expandHeight = this.ensureSize(this.expandHeight, rows);
      float spaceRightLast = 0.0F;

      for (int i = 0; i < cellCount; i++) {
         Cell c = cells.get(i);
         int column = c.column;
         int row = c.row;
         int colspan = c.colspan;
         Actor a = c.actor;
         if (c.expandY != 0 && expandHeight[row] == 0.0F) {
            expandHeight[row] = c.expandY.intValue();
         }

         if (colspan == 1 && c.expandX != 0 && expandWidth[column] == 0.0F) {
            expandWidth[column] = c.expandX.intValue();
         }

         c.computedPadLeft = c.padLeft.get(a) + (column == 0 ? 0.0F : Math.max(0.0F, c.spaceLeft.get(a) - spaceRightLast));
         c.computedPadTop = c.padTop.get(a);
         if (c.cellAboveIndex != -1) {
            Cell above = cells.get(c.cellAboveIndex);
            c.computedPadTop = c.computedPadTop + Math.max(0.0F, c.spaceTop.get(a) - above.spaceBottom.get(a));
         }

         float spaceRight = c.spaceRight.get(a);
         c.computedPadRight = c.padRight.get(a) + (column + colspan == columns ? 0.0F : spaceRight);
         c.computedPadBottom = c.padBottom.get(a) + (row == rows - 1 ? 0.0F : c.spaceBottom.get(a));
         spaceRightLast = spaceRight;
         float prefWidth = c.prefWidth.get(a);
         float prefHeight = c.prefHeight.get(a);
         float minWidth = c.minWidth.get(a);
         float minHeight = c.minHeight.get(a);
         float maxWidth = c.maxWidth.get(a);
         float maxHeight = c.maxHeight.get(a);
         if (prefWidth < minWidth) {
            prefWidth = minWidth;
         }

         if (prefHeight < minHeight) {
            prefHeight = minHeight;
         }

         if (maxWidth > 0.0F && prefWidth > maxWidth) {
            prefWidth = maxWidth;
         }

         if (maxHeight > 0.0F && prefHeight > maxHeight) {
            prefHeight = maxHeight;
         }

         if (colspan == 1) {
            float hpadding = c.computedPadLeft + c.computedPadRight;
            columnPrefWidth[column] = Math.max(columnPrefWidth[column], prefWidth + hpadding);
            columnMinWidth[column] = Math.max(columnMinWidth[column], minWidth + hpadding);
         }

         float vpadding = c.computedPadTop + c.computedPadBottom;
         rowPrefHeight[row] = Math.max(rowPrefHeight[row], prefHeight + vpadding);
         rowMinHeight[row] = Math.max(rowMinHeight[row], minHeight + vpadding);
      }

      float uniformMinWidth = 0.0F;
      float uniformMinHeight = 0.0F;
      float uniformPrefWidth = 0.0F;
      float uniformPrefHeight = 0.0F;

      for (int i = 0; i < cellCount; i++) {
         Cell cx = cells.get(i);
         int columnx = cx.column;
         int expandX = cx.expandX;
         if (expandX != 0) {
            int nn = columnx + cx.colspan;
            int ii = columnx;

            label194:
            while (true) {
               if (ii >= nn) {
                  ii = columnx;

                  while (true) {
                     if (ii >= nn) {
                        break label194;
                     }

                     expandWidth[ii] = expandX;
                     ii++;
                  }
               }

               if (expandWidth[ii] != 0.0F) {
                  break;
               }

               ii++;
            }
         }

         if (cx.uniformX == Boolean.TRUE && cx.colspan == 1) {
            float hpadding = cx.computedPadLeft + cx.computedPadRight;
            uniformMinWidth = Math.max(uniformMinWidth, columnMinWidth[columnx] - hpadding);
            uniformPrefWidth = Math.max(uniformPrefWidth, columnPrefWidth[columnx] - hpadding);
         }

         if (cx.uniformY == Boolean.TRUE) {
            float vpadding = cx.computedPadTop + cx.computedPadBottom;
            uniformMinHeight = Math.max(uniformMinHeight, rowMinHeight[cx.row] - vpadding);
            uniformPrefHeight = Math.max(uniformPrefHeight, rowPrefHeight[cx.row] - vpadding);
         }
      }

      if (uniformPrefWidth > 0.0F || uniformPrefHeight > 0.0F) {
         for (int i = 0; i < cellCount; i++) {
            Cell cxx = cells.get(i);
            if (uniformPrefWidth > 0.0F && cxx.uniformX == Boolean.TRUE && cxx.colspan == 1) {
               float hpadding = cxx.computedPadLeft + cxx.computedPadRight;
               columnMinWidth[cxx.column] = uniformMinWidth + hpadding;
               columnPrefWidth[cxx.column] = uniformPrefWidth + hpadding;
            }

            if (uniformPrefHeight > 0.0F && cxx.uniformY == Boolean.TRUE) {
               float vpadding = cxx.computedPadTop + cxx.computedPadBottom;
               rowMinHeight[cxx.row] = uniformMinHeight + vpadding;
               rowPrefHeight[cxx.row] = uniformPrefHeight + vpadding;
            }
         }
      }

      for (int i = 0; i < cellCount; i++) {
         Cell cxxx = cells.get(i);
         int colspanx = cxxx.colspan;
         if (colspanx != 1) {
            int columnxx = cxxx.column;
            Actor ax = cxxx.actor;
            float minWidthx = cxxx.minWidth.get(ax);
            float prefWidthx = cxxx.prefWidth.get(ax);
            float maxWidthx = cxxx.maxWidth.get(ax);
            if (prefWidthx < minWidthx) {
               prefWidthx = minWidthx;
            }

            if (maxWidthx > 0.0F && prefWidthx > maxWidthx) {
               prefWidthx = maxWidthx;
            }

            float spannedMinWidth = -(cxxx.computedPadLeft + cxxx.computedPadRight);
            float spannedPrefWidth = spannedMinWidth;
            float totalExpandWidth = 0.0F;
            int ii = columnxx;

            for (int nn = columnxx + colspanx; ii < nn; ii++) {
               spannedMinWidth += columnMinWidth[ii];
               spannedPrefWidth += columnPrefWidth[ii];
               totalExpandWidth += expandWidth[ii];
            }

            float extraMinWidth = Math.max(0.0F, minWidthx - spannedMinWidth);
            float extraPrefWidth = Math.max(0.0F, prefWidthx - spannedPrefWidth);
            int iix = columnxx;

            for (int nn = columnxx + colspanx; iix < nn; iix++) {
               float ratio = totalExpandWidth == 0.0F ? 1.0F / colspanx : expandWidth[iix] / totalExpandWidth;
               columnMinWidth[iix] += extraMinWidth * ratio;
               columnPrefWidth[iix] += extraPrefWidth * ratio;
            }
         }
      }

      this.tableMinWidth = 0.0F;
      this.tableMinHeight = 0.0F;
      this.tablePrefWidth = 0.0F;
      this.tablePrefHeight = 0.0F;

      for (int ix = 0; ix < columns; ix++) {
         this.tableMinWidth = this.tableMinWidth + columnMinWidth[ix];
         this.tablePrefWidth = this.tablePrefWidth + columnPrefWidth[ix];
      }

      for (int ix = 0; ix < rows; ix++) {
         this.tableMinHeight = this.tableMinHeight + rowMinHeight[ix];
         this.tablePrefHeight = this.tablePrefHeight + Math.max(rowMinHeight[ix], rowPrefHeight[ix]);
      }

      float hpadding = this.padLeft.get(this) + this.padRight.get(this);
      float vpadding = this.padTop.get(this) + this.padBottom.get(this);
      this.tableMinWidth += hpadding;
      this.tableMinHeight += vpadding;
      this.tablePrefWidth = Math.max(this.tablePrefWidth + hpadding, this.tableMinWidth);
      this.tablePrefHeight = Math.max(this.tablePrefHeight + vpadding, this.tableMinHeight);
   }

   private void layout(float layoutX, float layoutY, float layoutWidth, float layoutHeight) {
      Array<Cell> cells = this.cells;
      int cellCount = cells.size;
      if (this.sizeInvalid) {
         this.computeSize();
      }

      float padLeft = this.padLeft.get(this);
      float hpadding = padLeft + this.padRight.get(this);
      float padTop = this.padTop.get(this);
      float vpadding = padTop + this.padBottom.get(this);
      int columns = this.columns;
      int rows = this.rows;
      float[] expandWidth = this.expandWidth;
      float[] expandHeight = this.expandHeight;
      float[] columnWidth = this.columnWidth;
      float[] rowHeight = this.rowHeight;
      float totalExpandWidth = 0.0F;
      float totalExpandHeight = 0.0F;

      for (int i = 0; i < columns; i++) {
         totalExpandWidth += expandWidth[i];
      }

      for (int i = 0; i < rows; i++) {
         totalExpandHeight += expandHeight[i];
      }

      float totalGrowWidth = this.tablePrefWidth - this.tableMinWidth;
      float[] columnWeightedWidth;
      if (totalGrowWidth == 0.0F) {
         columnWeightedWidth = this.columnMinWidth;
      } else {
         float extraWidth = Math.min(totalGrowWidth, Math.max(0.0F, layoutWidth - this.tableMinWidth));
         columnWeightedWidth = Table.columnWeightedWidth = this.ensureSize(Table.columnWeightedWidth, columns);
         float[] columnMinWidth = this.columnMinWidth;
         float[] columnPrefWidth = this.columnPrefWidth;

         for (int i = 0; i < columns; i++) {
            float growWidth = columnPrefWidth[i] - columnMinWidth[i];
            float growRatio = growWidth / totalGrowWidth;
            columnWeightedWidth[i] = columnMinWidth[i] + extraWidth * growRatio;
         }
      }

      float totalGrowHeight = this.tablePrefHeight - this.tableMinHeight;
      float[] rowWeightedHeight;
      if (totalGrowHeight == 0.0F) {
         rowWeightedHeight = this.rowMinHeight;
      } else {
         rowWeightedHeight = Table.rowWeightedHeight = this.ensureSize(Table.rowWeightedHeight, rows);
         float extraHeight = Math.min(totalGrowHeight, Math.max(0.0F, layoutHeight - this.tableMinHeight));
         float[] rowMinHeight = this.rowMinHeight;
         float[] rowPrefHeight = this.rowPrefHeight;

         for (int i = 0; i < rows; i++) {
            float growHeight = rowPrefHeight[i] - rowMinHeight[i];
            float growRatio = growHeight / totalGrowHeight;
            rowWeightedHeight[i] = rowMinHeight[i] + extraHeight * growRatio;
         }
      }

      for (int i = 0; i < cellCount; i++) {
         Cell c = cells.get(i);
         int column = c.column;
         int row = c.row;
         Actor a = c.actor;
         float spannedWeightedWidth = 0.0F;
         int colspan = c.colspan;
         int ii = column;

         for (int nn = column + colspan; ii < nn; ii++) {
            spannedWeightedWidth += columnWeightedWidth[ii];
         }

         float weightedHeight = rowWeightedHeight[row];
         float prefWidth = c.prefWidth.get(a);
         float prefHeight = c.prefHeight.get(a);
         float minWidth = c.minWidth.get(a);
         float minHeight = c.minHeight.get(a);
         float maxWidth = c.maxWidth.get(a);
         float maxHeight = c.maxHeight.get(a);
         if (prefWidth < minWidth) {
            prefWidth = minWidth;
         }

         if (prefHeight < minHeight) {
            prefHeight = minHeight;
         }

         if (maxWidth > 0.0F && prefWidth > maxWidth) {
            prefWidth = maxWidth;
         }

         if (maxHeight > 0.0F && prefHeight > maxHeight) {
            prefHeight = maxHeight;
         }

         c.actorWidth = Math.min(spannedWeightedWidth - c.computedPadLeft - c.computedPadRight, prefWidth);
         c.actorHeight = Math.min(weightedHeight - c.computedPadTop - c.computedPadBottom, prefHeight);
         if (colspan == 1) {
            columnWidth[column] = Math.max(columnWidth[column], spannedWeightedWidth);
         }

         rowHeight[row] = Math.max(rowHeight[row], weightedHeight);
      }

      if (totalExpandWidth > 0.0F) {
         float extra = layoutWidth - hpadding;

         for (int i = 0; i < columns; i++) {
            extra -= columnWidth[i];
         }

         float used = 0.0F;
         int lastIndex = 0;

         for (int i = 0; i < columns; i++) {
            if (expandWidth[i] != 0.0F) {
               float amount = extra * expandWidth[i] / totalExpandWidth;
               columnWidth[i] += amount;
               used += amount;
               lastIndex = i;
            }
         }

         columnWidth[lastIndex] += extra - used;
      }

      if (totalExpandHeight > 0.0F) {
         float extra = layoutHeight - vpadding;

         for (int ix = 0; ix < rows; ix++) {
            extra -= rowHeight[ix];
         }

         float used = 0.0F;
         int lastIndex = 0;

         for (int ix = 0; ix < rows; ix++) {
            if (expandHeight[ix] != 0.0F) {
               float amount = extra * expandHeight[ix] / totalExpandHeight;
               rowHeight[ix] += amount;
               used += amount;
               lastIndex = ix;
            }
         }

         rowHeight[lastIndex] += extra - used;
      }

      for (int ixx = 0; ixx < cellCount; ixx++) {
         Cell c = cells.get(ixx);
         int colspan = c.colspan;
         if (colspan != 1) {
            float extraWidth = 0.0F;
            int column = c.column;

            for (int nn = column + colspan; column < nn; column++) {
               extraWidth += columnWeightedWidth[column] - columnWidth[column];
            }

            extraWidth -= Math.max(0.0F, c.computedPadLeft + c.computedPadRight);
            extraWidth /= colspan;
            if (extraWidth > 0.0F) {
               column = c.column;

               for (int nn = column + colspan; column < nn; column++) {
                  columnWidth[column] += extraWidth;
               }
            }
         }
      }

      float tableWidth = hpadding;
      float tableHeight = vpadding;

      for (int ixxx = 0; ixxx < columns; ixxx++) {
         tableWidth += columnWidth[ixxx];
      }

      for (int ixxx = 0; ixxx < rows; ixxx++) {
         tableHeight += rowHeight[ixxx];
      }

      int align = this.align;
      float x = layoutX + padLeft;
      if ((align & 16) != 0) {
         x += layoutWidth - tableWidth;
      } else if ((align & 8) == 0) {
         x += (layoutWidth - tableWidth) / 2.0F;
      }

      float y = layoutY + padTop;
      if ((align & 4) != 0) {
         y += layoutHeight - tableHeight;
      } else if ((align & 2) == 0) {
         y += (layoutHeight - tableHeight) / 2.0F;
      }

      float currentX = x;
      float currentY = y;

      for (int ixxx = 0; ixxx < cellCount; ixxx++) {
         Cell c = cells.get(ixxx);
         float spannedCellWidth = 0.0F;
         int column = c.column;

         for (int nn = column + c.colspan; column < nn; column++) {
            spannedCellWidth += columnWidth[column];
         }

         spannedCellWidth -= c.computedPadLeft + c.computedPadRight;
         currentX += c.computedPadLeft;
         float fillX = c.fillX;
         float fillY = c.fillY;
         if (fillX > 0.0F) {
            c.actorWidth = Math.max(spannedCellWidth * fillX, c.minWidth.get(c.actor));
            float maxWidthx = c.maxWidth.get(c.actor);
            if (maxWidthx > 0.0F) {
               c.actorWidth = Math.min(c.actorWidth, maxWidthx);
            }
         }

         if (fillY > 0.0F) {
            c.actorHeight = Math.max(rowHeight[c.row] * fillY - c.computedPadTop - c.computedPadBottom, c.minHeight.get(c.actor));
            float maxHeightx = c.maxHeight.get(c.actor);
            if (maxHeightx > 0.0F) {
               c.actorHeight = Math.min(c.actorHeight, maxHeightx);
            }
         }

         align = c.align;
         if ((align & 8) != 0) {
            c.actorX = currentX;
         } else if ((align & 16) != 0) {
            c.actorX = currentX + spannedCellWidth - c.actorWidth;
         } else {
            c.actorX = currentX + (spannedCellWidth - c.actorWidth) / 2.0F;
         }

         if ((align & 2) != 0) {
            c.actorY = currentY + c.computedPadTop;
         } else if ((align & 4) != 0) {
            c.actorY = currentY + rowHeight[c.row] - c.actorHeight - c.computedPadBottom;
         } else {
            c.actorY = currentY + (rowHeight[c.row] - c.actorHeight + c.computedPadTop - c.computedPadBottom) / 2.0F;
         }

         if (c.endRow) {
            currentX = x;
            currentY += rowHeight[c.row];
         } else {
            currentX += spannedCellWidth + c.computedPadRight;
         }
      }

      if (this.debug != Table.Debug.none) {
         this.clearDebugRects();
         currentX = x;
         currentY = y;
         if (this.debug == Table.Debug.table || this.debug == Table.Debug.all) {
            this.addDebugRect(layoutX, layoutY, layoutWidth, layoutHeight, debugTableColor);
            this.addDebugRect(x, y, tableWidth - hpadding, tableHeight - vpadding, debugTableColor);
         }

         for (int ixxx = 0; ixxx < cellCount; ixxx++) {
            Cell c = cells.get(ixxx);
            if (this.debug == Table.Debug.actor || this.debug == Table.Debug.all) {
               this.addDebugRect(c.actorX, c.actorY, c.actorWidth, c.actorHeight, debugActorColor);
            }

            float spannedCellWidth = 0.0F;
            int column = c.column;

            for (int nn = column + c.colspan; column < nn; column++) {
               spannedCellWidth += columnWidth[column];
            }

            spannedCellWidth -= c.computedPadLeft + c.computedPadRight;
            currentX += c.computedPadLeft;
            if (this.debug == Table.Debug.cell || this.debug == Table.Debug.all) {
               this.addDebugRect(
                  currentX, currentY + c.computedPadTop, spannedCellWidth, rowHeight[c.row] - c.computedPadTop - c.computedPadBottom, debugCellColor
               );
            }

            if (c.endRow) {
               currentX = x;
               currentY += rowHeight[c.row];
            } else {
               currentX += spannedCellWidth + c.computedPadRight;
            }
         }
      }
   }

   private void clearDebugRects() {
      if (this.debugRects != null) {
         Table.DebugRect.pool.freeAll(this.debugRects);
         this.debugRects.clear();
      }
   }

   private void addDebugRect(float x, float y, float w, float h, Color color) {
      if (this.debugRects == null) {
         this.debugRects = new Array<>();
      }

      Table.DebugRect rect = Table.DebugRect.pool.obtain();
      rect.color = color;
      rect.set(x, this.getHeight() - y - h, w, h);
      this.debugRects.add(rect);
   }

   @Override
   public void drawDebug(ShapeRenderer shapes) {
      if (this.isTransform()) {
         this.applyTransform(shapes, this.computeTransform());
         this.drawDebugRects(shapes);
         if (this.clip) {
            shapes.flush();
            float x = 0.0F;
            float y = 0.0F;
            float width = this.getWidth();
            float height = this.getHeight();
            if (this.background != null) {
               x = this.padLeft.get(this);
               y = this.padBottom.get(this);
               width -= x + this.padRight.get(this);
               height -= y + this.padTop.get(this);
            }

            if (this.clipBegin(x, y, width, height)) {
               this.drawDebugChildren(shapes);
               this.clipEnd();
            }
         } else {
            this.drawDebugChildren(shapes);
         }

         this.resetTransform(shapes);
      } else {
         this.drawDebugRects(shapes);
         super.drawDebug(shapes);
      }
   }

   @Override
   protected void drawDebugBounds(ShapeRenderer shapes) {
   }

   private void drawDebugRects(ShapeRenderer shapes) {
      if (this.debugRects != null && this.getDebug()) {
         shapes.set(ShapeRenderer.ShapeType.Line);
         shapes.setColor(this.getStage().getDebugColor());
         float x = 0.0F;
         float y = 0.0F;
         if (!this.isTransform()) {
            x = this.getX();
            y = this.getY();
         }

         int i = 0;

         for (int n = this.debugRects.size; i < n; i++) {
            Table.DebugRect debugRect = this.debugRects.get(i);
            shapes.setColor(debugRect.color);
            shapes.rect(x + debugRect.x, y + debugRect.y, debugRect.width, debugRect.height);
         }
      }
   }

   public Skin getSkin() {
      return this.skin;
   }

   public static enum Debug {
      none,
      all,
      table,
      cell,
      actor;
   }

   public static class DebugRect extends Rectangle {
      static Pool<Table.DebugRect> pool = Pools.get(Table.DebugRect.class);
      Color color;
   }
}
