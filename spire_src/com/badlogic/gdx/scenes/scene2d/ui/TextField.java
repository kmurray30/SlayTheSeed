package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.Timer;

public class TextField extends Widget implements Disableable {
   private static final char BACKSPACE = '\b';
   protected static final char ENTER_DESKTOP = '\r';
   protected static final char ENTER_ANDROID = '\n';
   private static final char TAB = '\t';
   private static final char DELETE = '\u007f';
   private static final char BULLET = '\u0095';
   private static final Vector2 tmp1 = new Vector2();
   private static final Vector2 tmp2 = new Vector2();
   private static final Vector2 tmp3 = new Vector2();
   public static float keyRepeatInitialTime = 0.4F;
   public static float keyRepeatTime = 0.1F;
   protected String text;
   protected int cursor;
   protected int selectionStart;
   protected boolean hasSelection;
   protected boolean writeEnters;
   protected final GlyphLayout layout = new GlyphLayout();
   protected final FloatArray glyphPositions = new FloatArray();
   TextField.TextFieldStyle style;
   private String messageText;
   protected CharSequence displayText;
   Clipboard clipboard;
   InputListener inputListener;
   TextField.TextFieldListener listener;
   TextField.TextFieldFilter filter;
   TextField.OnscreenKeyboard keyboard = new TextField.DefaultOnscreenKeyboard();
   boolean focusTraversal = true;
   boolean onlyFontChars = true;
   boolean disabled;
   private int textHAlign = 8;
   private float selectionX;
   private float selectionWidth;
   String undoText = "";
   long lastChangeTime;
   boolean passwordMode;
   private StringBuilder passwordBuffer;
   private char passwordCharacter = 149;
   protected float fontOffset;
   protected float textHeight;
   protected float textOffset;
   float renderOffset;
   private int visibleTextStart;
   private int visibleTextEnd;
   private int maxLength = 0;
   private float blinkTime = 0.32F;
   boolean cursorOn = true;
   long lastBlink;
   TextField.KeyRepeatTask keyRepeatTask = new TextField.KeyRepeatTask();
   boolean programmaticChangeEvents;

   public TextField(String text, Skin skin) {
      this(text, skin.get(TextField.TextFieldStyle.class));
   }

   public TextField(String text, Skin skin, String styleName) {
      this(text, skin.get(styleName, TextField.TextFieldStyle.class));
   }

   public TextField(String text, TextField.TextFieldStyle style) {
      this.setStyle(style);
      this.clipboard = Gdx.app.getClipboard();
      this.initialize();
      this.setText(text);
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
   }

   protected void initialize() {
      this.addListener(this.inputListener = this.createInputListener());
   }

   protected InputListener createInputListener() {
      return new TextField.TextFieldClickListener();
   }

   protected int letterUnderCursor(float x) {
      x -= this.textOffset + this.fontOffset - this.style.font.getData().cursorX - this.glyphPositions.get(this.visibleTextStart);
      Drawable background = this.getBackgroundDrawable();
      if (background != null) {
         x -= this.style.background.getLeftWidth();
      }

      int n = this.glyphPositions.size;
      float[] glyphPositions = this.glyphPositions.items;

      for (int i = 1; i < n; i++) {
         if (glyphPositions[i] > x) {
            if (glyphPositions[i] - x <= x - glyphPositions[i - 1]) {
               return i;
            }

            return i - 1;
         }
      }

      return n - 1;
   }

   protected boolean isWordCharacter(char c) {
      return Character.isLetterOrDigit(c);
   }

   protected int[] wordUnderCursor(int at) {
      String text = this.text;
      int right = text.length();
      int left = 0;
      int index = at;
      if (at >= text.length()) {
         left = text.length();
         int rightx = 0;
         return new int[]{left, rightx};
      } else {
         while (index < right) {
            if (!this.isWordCharacter(text.charAt(index))) {
               right = index;
               break;
            }

            index++;
         }

         for (int var7 = at - 1; var7 > -1; var7--) {
            if (!this.isWordCharacter(text.charAt(var7))) {
               left = var7 + 1;
               return new int[]{left, right};
            }
         }

         return new int[]{left, right};
      }
   }

   int[] wordUnderCursor(float x) {
      return this.wordUnderCursor(this.letterUnderCursor(x));
   }

   boolean withinMaxLength(int size) {
      return this.maxLength <= 0 || size < this.maxLength;
   }

   public void setMaxLength(int maxLength) {
      this.maxLength = maxLength;
   }

   public int getMaxLength() {
      return this.maxLength;
   }

   public void setOnlyFontChars(boolean onlyFontChars) {
      this.onlyFontChars = onlyFontChars;
   }

   public void setStyle(TextField.TextFieldStyle style) {
      if (style == null) {
         throw new IllegalArgumentException("style cannot be null.");
      } else {
         this.style = style;
         this.textHeight = style.font.getCapHeight() - style.font.getDescent() * 2.0F;
         this.invalidateHierarchy();
      }
   }

   public TextField.TextFieldStyle getStyle() {
      return this.style;
   }

   protected void calculateOffsets() {
      float visibleWidth = this.getWidth();
      Drawable background = this.getBackgroundDrawable();
      if (background != null) {
         visibleWidth -= background.getLeftWidth() + background.getRightWidth();
      }

      int glyphCount = this.glyphPositions.size;
      float[] glyphPositions = this.glyphPositions.items;
      float distance = glyphPositions[Math.max(0, this.cursor - 1)] + this.renderOffset;
      if (distance <= 0.0F) {
         this.renderOffset -= distance;
      } else {
         int index = Math.min(glyphCount - 1, this.cursor + 1);
         float minX = glyphPositions[index] - visibleWidth;
         if (-this.renderOffset < minX) {
            this.renderOffset = -minX;
         }
      }

      float maxOffset = 0.0F;
      float width = glyphPositions[glyphCount - 1];

      for (int i = glyphCount - 2; i >= 0; i--) {
         float x = glyphPositions[i];
         if (width - x > visibleWidth) {
            break;
         }

         maxOffset = x;
      }

      if (-this.renderOffset > maxOffset) {
         this.renderOffset = -maxOffset;
      }

      this.visibleTextStart = 0;
      float startX = 0.0F;

      for (int i = 0; i < glyphCount; i++) {
         if (glyphPositions[i] >= -this.renderOffset) {
            this.visibleTextStart = Math.max(0, i);
            startX = glyphPositions[i];
            break;
         }
      }

      int length = Math.min(this.displayText.length(), glyphPositions.length - 1);
      this.visibleTextEnd = Math.min(length, this.cursor + 1);

      while (this.visibleTextEnd <= length && !(glyphPositions[this.visibleTextEnd] > startX + visibleWidth)) {
         this.visibleTextEnd++;
      }

      this.visibleTextEnd = Math.max(0, this.visibleTextEnd - 1);
      if ((this.textHAlign & 8) == 0) {
         this.textOffset = visibleWidth - (glyphPositions[this.visibleTextEnd] - startX);
         if ((this.textHAlign & 1) != 0) {
            this.textOffset = Math.round(this.textOffset * 0.5F);
         }
      } else {
         this.textOffset = startX + this.renderOffset;
      }

      if (this.hasSelection) {
         int minIndex = Math.min(this.cursor, this.selectionStart);
         int maxIndex = Math.max(this.cursor, this.selectionStart);
         float minXx = Math.max(glyphPositions[minIndex] - glyphPositions[this.visibleTextStart], -this.textOffset);
         float maxX = Math.min(glyphPositions[maxIndex] - glyphPositions[this.visibleTextStart], visibleWidth - this.textOffset);
         this.selectionX = minXx;
         this.selectionWidth = maxX - minXx - this.style.font.getData().cursorX;
      }
   }

   private Drawable getBackgroundDrawable() {
      Stage stage = this.getStage();
      boolean focused = stage != null && stage.getKeyboardFocus() == this;
      return this.disabled && this.style.disabledBackground != null
         ? this.style.disabledBackground
         : (focused && this.style.focusedBackground != null ? this.style.focusedBackground : this.style.background);
   }

   @Override
   public void draw(Batch batch, float parentAlpha) {
      Stage stage = this.getStage();
      boolean focused = stage != null && stage.getKeyboardFocus() == this;
      if (!focused) {
         this.keyRepeatTask.cancel();
      }

      BitmapFont font = this.style.font;
      Color fontColor = this.disabled && this.style.disabledFontColor != null
         ? this.style.disabledFontColor
         : (focused && this.style.focusedFontColor != null ? this.style.focusedFontColor : this.style.fontColor);
      Drawable selection = this.style.selection;
      Drawable cursorPatch = this.style.cursor;
      Drawable background = this.getBackgroundDrawable();
      Color color = this.getColor();
      float x = this.getX();
      float y = this.getY();
      float width = this.getWidth();
      float height = this.getHeight();
      batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
      float bgLeftWidth = 0.0F;
      float bgRightWidth = 0.0F;
      if (background != null) {
         background.draw(batch, x, y, width, height);
         bgLeftWidth = background.getLeftWidth();
         bgRightWidth = background.getRightWidth();
      }

      float textY = this.getTextY(font, background);
      this.calculateOffsets();
      if (focused && this.hasSelection && selection != null) {
         this.drawSelection(selection, batch, font, x + bgLeftWidth, y + textY);
      }

      float yOffset = font.isFlipped() ? -this.textHeight : 0.0F;
      if (this.displayText.length() == 0) {
         if (!focused && this.messageText != null) {
            if (this.style.messageFontColor != null) {
               font.setColor(
                  this.style.messageFontColor.r,
                  this.style.messageFontColor.g,
                  this.style.messageFontColor.b,
                  this.style.messageFontColor.a * color.a * parentAlpha
               );
            } else {
               font.setColor(0.7F, 0.7F, 0.7F, color.a * parentAlpha);
            }

            BitmapFont messageFont = this.style.messageFont != null ? this.style.messageFont : font;
            messageFont.draw(
               batch,
               this.messageText,
               x + bgLeftWidth,
               y + textY + yOffset,
               0,
               this.messageText.length(),
               width - bgLeftWidth - bgRightWidth,
               this.textHAlign,
               false,
               "..."
            );
         }
      } else {
         font.setColor(fontColor.r, fontColor.g, fontColor.b, fontColor.a * color.a * parentAlpha);
         this.drawText(batch, font, x + bgLeftWidth, y + textY + yOffset);
      }

      if (focused && !this.disabled) {
         this.blink();
         if (this.cursorOn && cursorPatch != null) {
            this.drawCursor(cursorPatch, batch, font, x + bgLeftWidth, y + textY);
         }
      }
   }

   protected float getTextY(BitmapFont font, Drawable background) {
      float height = this.getHeight();
      float textY = this.textHeight / 2.0F + font.getDescent();
      if (background != null) {
         float bottom = background.getBottomHeight();
         textY = textY + (height - background.getTopHeight() - bottom) / 2.0F + bottom;
      } else {
         textY += height / 2.0F;
      }

      if (font.usesIntegerPositions()) {
         textY = (int)textY;
      }

      return textY;
   }

   protected void drawSelection(Drawable selection, Batch batch, BitmapFont font, float x, float y) {
      selection.draw(
         batch, x + this.textOffset + this.selectionX + this.fontOffset, y - this.textHeight - font.getDescent(), this.selectionWidth, this.textHeight
      );
   }

   protected void drawText(Batch batch, BitmapFont font, float x, float y) {
      font.draw(batch, this.displayText, x + this.textOffset, y, this.visibleTextStart, this.visibleTextEnd, 0.0F, 8, false);
   }

   protected void drawCursor(Drawable cursorPatch, Batch batch, BitmapFont font, float x, float y) {
      cursorPatch.draw(
         batch,
         x + this.textOffset + this.glyphPositions.get(this.cursor) - this.glyphPositions.get(this.visibleTextStart) + this.fontOffset + font.getData().cursorX,
         y - this.textHeight - font.getDescent(),
         cursorPatch.getMinWidth(),
         this.textHeight
      );
   }

   void updateDisplayText() {
      BitmapFont font = this.style.font;
      BitmapFont.BitmapFontData data = font.getData();
      String text = this.text;
      int textLength = text.length();
      StringBuilder buffer = new StringBuilder();

      for (int i = 0; i < textLength; i++) {
         char c = text.charAt(i);
         buffer.append(data.hasGlyph(c) ? c : ' ');
      }

      String newDisplayText = buffer.toString();
      if (this.passwordMode && data.hasGlyph(this.passwordCharacter)) {
         if (this.passwordBuffer == null) {
            this.passwordBuffer = new StringBuilder(newDisplayText.length());
         }

         if (this.passwordBuffer.length() > textLength) {
            this.passwordBuffer.setLength(textLength);
         } else {
            for (int i = this.passwordBuffer.length(); i < textLength; i++) {
               this.passwordBuffer.append(this.passwordCharacter);
            }
         }

         this.displayText = this.passwordBuffer;
      } else {
         this.displayText = newDisplayText;
      }

      this.layout.setText(font, this.displayText);
      this.glyphPositions.clear();
      float x = 0.0F;
      if (this.layout.runs.size > 0) {
         GlyphLayout.GlyphRun run = this.layout.runs.first();
         FloatArray xAdvances = run.xAdvances;
         this.fontOffset = xAdvances.first();
         int i = 1;

         for (int n = xAdvances.size; i < n; i++) {
            this.glyphPositions.add(x);
            x += xAdvances.get(i);
         }
      } else {
         this.fontOffset = 0.0F;
      }

      this.glyphPositions.add(x);
      if (this.selectionStart > newDisplayText.length()) {
         this.selectionStart = textLength;
      }
   }

   private void blink() {
      if (!Gdx.graphics.isContinuousRendering()) {
         this.cursorOn = true;
      } else {
         long time = TimeUtils.nanoTime();
         if ((float)(time - this.lastBlink) / 1.0E9F > this.blinkTime) {
            this.cursorOn = !this.cursorOn;
            this.lastBlink = time;
         }
      }
   }

   public void copy() {
      if (this.hasSelection && !this.passwordMode) {
         this.clipboard.setContents(this.text.substring(Math.min(this.cursor, this.selectionStart), Math.max(this.cursor, this.selectionStart)));
      }
   }

   public void cut() {
      this.cut(this.programmaticChangeEvents);
   }

   void cut(boolean fireChangeEvent) {
      if (this.hasSelection && !this.passwordMode) {
         this.copy();
         this.cursor = this.delete(fireChangeEvent);
         this.updateDisplayText();
      }
   }

   void paste(String content, boolean fireChangeEvent) {
      if (content != null) {
         StringBuilder buffer = new StringBuilder();
         int textLength = this.text.length();
         if (this.hasSelection) {
            textLength -= Math.abs(this.cursor - this.selectionStart);
         }

         BitmapFont.BitmapFontData data = this.style.font.getData();
         int i = 0;

         for (int n = content.length(); i < n && this.withinMaxLength(textLength + buffer.length()); i++) {
            char c = content.charAt(i);
            if (this.writeEnters && (c == '\n' || c == '\r')
               || c != '\r' && c != '\n' && (!this.onlyFontChars || data.hasGlyph(c)) && (this.filter == null || this.filter.acceptChar(this, c))) {
               buffer.append(c);
            }
         }

         content = buffer.toString();
         if (this.hasSelection) {
            this.cursor = this.delete(fireChangeEvent);
         }

         if (fireChangeEvent) {
            this.changeText(this.text, this.insert(this.cursor, content, this.text));
         } else {
            this.text = this.insert(this.cursor, content, this.text);
         }

         this.updateDisplayText();
         this.cursor = this.cursor + content.length();
      }
   }

   String insert(int position, CharSequence text, String to) {
      return to.length() == 0 ? text.toString() : to.substring(0, position) + text + to.substring(position, to.length());
   }

   int delete(boolean fireChangeEvent) {
      int from = this.selectionStart;
      int to = this.cursor;
      int minIndex = Math.min(from, to);
      int maxIndex = Math.max(from, to);
      String newText = (minIndex > 0 ? this.text.substring(0, minIndex) : "")
         + (maxIndex < this.text.length() ? this.text.substring(maxIndex, this.text.length()) : "");
      if (fireChangeEvent) {
         this.changeText(this.text, newText);
      } else {
         this.text = newText;
      }

      this.clearSelection();
      return minIndex;
   }

   public void next(boolean up) {
      Stage stage = this.getStage();
      if (stage != null) {
         TextField current = this;

         while (true) {
            current.getParent().localToStageCoordinates(tmp1.set(this.getX(), this.getY()));
            TextField textField = current.findNextTextField(stage.getActors(), null, tmp2, tmp1, up);
            if (textField == null) {
               if (up) {
                  tmp1.set(Float.MIN_VALUE, Float.MIN_VALUE);
               } else {
                  tmp1.set(Float.MAX_VALUE, Float.MAX_VALUE);
               }

               textField = current.findNextTextField(this.getStage().getActors(), null, tmp2, tmp1, up);
            }

            if (textField == null) {
               Gdx.input.setOnscreenKeyboardVisible(false);
               break;
            }

            if (stage.setKeyboardFocus(textField)) {
               break;
            }

            current = textField;
         }
      }
   }

   private TextField findNextTextField(Array<Actor> actors, TextField best, Vector2 bestCoords, Vector2 currentCoords, boolean up) {
      int i = 0;

      for (int n = actors.size; i < n; i++) {
         Actor actor = actors.get(i);
         if (actor != this) {
            if (actor instanceof TextField) {
               TextField textField = (TextField)actor;
               if (!textField.isDisabled() && textField.focusTraversal) {
                  Vector2 actorCoords = actor.getParent().localToStageCoordinates(tmp3.set(actor.getX(), actor.getY()));
                  if ((actorCoords.y < currentCoords.y || actorCoords.y == currentCoords.y && actorCoords.x > currentCoords.x) ^ up
                     && (best == null || (actorCoords.y > bestCoords.y || actorCoords.y == bestCoords.y && actorCoords.x < bestCoords.x) ^ up)) {
                     best = (TextField)actor;
                     bestCoords.set(actorCoords);
                  }
               }
            } else if (actor instanceof Group) {
               best = this.findNextTextField(((Group)actor).getChildren(), best, bestCoords, currentCoords, up);
            }
         }
      }

      return best;
   }

   public InputListener getDefaultInputListener() {
      return this.inputListener;
   }

   public void setTextFieldListener(TextField.TextFieldListener listener) {
      this.listener = listener;
   }

   public void setTextFieldFilter(TextField.TextFieldFilter filter) {
      this.filter = filter;
   }

   public TextField.TextFieldFilter getTextFieldFilter() {
      return this.filter;
   }

   public void setFocusTraversal(boolean focusTraversal) {
      this.focusTraversal = focusTraversal;
   }

   public String getMessageText() {
      return this.messageText;
   }

   public void setMessageText(String messageText) {
      this.messageText = messageText;
   }

   public void appendText(String str) {
      if (str == null) {
         str = "";
      }

      this.clearSelection();
      this.cursor = this.text.length();
      this.paste(str, this.programmaticChangeEvents);
   }

   public void setText(String str) {
      if (str == null) {
         str = "";
      }

      if (!str.equals(this.text)) {
         this.clearSelection();
         String oldText = this.text;
         this.text = "";
         this.paste(str, false);
         if (this.programmaticChangeEvents) {
            this.changeText(oldText, this.text);
         }

         this.cursor = 0;
      }
   }

   public String getText() {
      return this.text;
   }

   boolean changeText(String oldText, String newText) {
      if (newText.equals(oldText)) {
         return false;
      } else {
         this.text = newText;
         ChangeListener.ChangeEvent changeEvent = Pools.obtain(ChangeListener.ChangeEvent.class);
         boolean cancelled = this.fire(changeEvent);
         this.text = cancelled ? oldText : newText;
         Pools.free(changeEvent);
         return !cancelled;
      }
   }

   public void setProgrammaticChangeEvents(boolean programmaticChangeEvents) {
      this.programmaticChangeEvents = programmaticChangeEvents;
   }

   public boolean getProgrammaticChangeEvents() {
      return this.programmaticChangeEvents;
   }

   public int getSelectionStart() {
      return this.selectionStart;
   }

   public String getSelection() {
      return this.hasSelection ? this.text.substring(Math.min(this.selectionStart, this.cursor), Math.max(this.selectionStart, this.cursor)) : "";
   }

   public void setSelection(int selectionStart, int selectionEnd) {
      if (selectionStart < 0) {
         throw new IllegalArgumentException("selectionStart must be >= 0");
      } else if (selectionEnd < 0) {
         throw new IllegalArgumentException("selectionEnd must be >= 0");
      } else {
         selectionStart = Math.min(this.text.length(), selectionStart);
         selectionEnd = Math.min(this.text.length(), selectionEnd);
         if (selectionEnd == selectionStart) {
            this.clearSelection();
         } else {
            if (selectionEnd < selectionStart) {
               int temp = selectionEnd;
               selectionEnd = selectionStart;
               selectionStart = temp;
            }

            this.hasSelection = true;
            this.selectionStart = selectionStart;
            this.cursor = selectionEnd;
         }
      }
   }

   public void selectAll() {
      this.setSelection(0, this.text.length());
   }

   public void clearSelection() {
      this.hasSelection = false;
   }

   public void setCursorPosition(int cursorPosition) {
      if (cursorPosition < 0) {
         throw new IllegalArgumentException("cursorPosition must be >= 0");
      } else {
         this.clearSelection();
         this.cursor = Math.min(cursorPosition, this.text.length());
      }
   }

   public int getCursorPosition() {
      return this.cursor;
   }

   public TextField.OnscreenKeyboard getOnscreenKeyboard() {
      return this.keyboard;
   }

   public void setOnscreenKeyboard(TextField.OnscreenKeyboard keyboard) {
      this.keyboard = keyboard;
   }

   public void setClipboard(Clipboard clipboard) {
      this.clipboard = clipboard;
   }

   @Override
   public float getPrefWidth() {
      return 150.0F;
   }

   @Override
   public float getPrefHeight() {
      float topAndBottom = 0.0F;
      float minHeight = 0.0F;
      if (this.style.background != null) {
         topAndBottom = Math.max(topAndBottom, this.style.background.getBottomHeight() + this.style.background.getTopHeight());
         minHeight = Math.max(minHeight, this.style.background.getMinHeight());
      }

      if (this.style.focusedBackground != null) {
         topAndBottom = Math.max(topAndBottom, this.style.focusedBackground.getBottomHeight() + this.style.focusedBackground.getTopHeight());
         minHeight = Math.max(minHeight, this.style.focusedBackground.getMinHeight());
      }

      if (this.style.disabledBackground != null) {
         topAndBottom = Math.max(topAndBottom, this.style.disabledBackground.getBottomHeight() + this.style.disabledBackground.getTopHeight());
         minHeight = Math.max(minHeight, this.style.disabledBackground.getMinHeight());
      }

      return Math.max(topAndBottom + this.textHeight, minHeight);
   }

   public void setAlignment(int alignment) {
      this.textHAlign = alignment;
   }

   public void setPasswordMode(boolean passwordMode) {
      this.passwordMode = passwordMode;
      this.updateDisplayText();
   }

   public boolean isPasswordMode() {
      return this.passwordMode;
   }

   public void setPasswordCharacter(char passwordCharacter) {
      this.passwordCharacter = passwordCharacter;
      if (this.passwordMode) {
         this.updateDisplayText();
      }
   }

   public void setBlinkTime(float blinkTime) {
      this.blinkTime = blinkTime;
   }

   @Override
   public void setDisabled(boolean disabled) {
      this.disabled = disabled;
   }

   @Override
   public boolean isDisabled() {
      return this.disabled;
   }

   protected void moveCursor(boolean forward, boolean jump) {
      int limit = forward ? this.text.length() : 0;
      int charOffset = forward ? 0 : -1;

      while ((forward ? ++this.cursor < limit : --this.cursor > limit) && jump && this.continueCursor(this.cursor, charOffset)) {
      }
   }

   protected boolean continueCursor(int index, int offset) {
      char c = this.text.charAt(index + offset);
      return this.isWordCharacter(c);
   }

   public static class DefaultOnscreenKeyboard implements TextField.OnscreenKeyboard {
      @Override
      public void show(boolean visible) {
         Gdx.input.setOnscreenKeyboardVisible(visible);
      }
   }

   class KeyRepeatTask extends Timer.Task {
      int keycode;

      @Override
      public void run() {
         TextField.this.inputListener.keyDown(null, this.keycode);
      }
   }

   public interface OnscreenKeyboard {
      void show(boolean var1);
   }

   public class TextFieldClickListener extends ClickListener {
      @Override
      public void clicked(InputEvent event, float x, float y) {
         int count = this.getTapCount() % 4;
         if (count == 0) {
            TextField.this.clearSelection();
         }

         if (count == 2) {
            int[] array = TextField.this.wordUnderCursor(x);
            TextField.this.setSelection(array[0], array[1]);
         }

         if (count == 3) {
            TextField.this.selectAll();
         }
      }

      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
         if (!super.touchDown(event, x, y, pointer, button)) {
            return false;
         } else if (pointer == 0 && button != 0) {
            return false;
         } else if (TextField.this.disabled) {
            return true;
         } else {
            this.setCursorPosition(x, y);
            TextField.this.selectionStart = TextField.this.cursor;
            Stage stage = TextField.this.getStage();
            if (stage != null) {
               stage.setKeyboardFocus(TextField.this);
            }

            TextField.this.keyboard.show(true);
            TextField.this.hasSelection = true;
            return true;
         }
      }

      @Override
      public void touchDragged(InputEvent event, float x, float y, int pointer) {
         super.touchDragged(event, x, y, pointer);
         this.setCursorPosition(x, y);
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
         if (TextField.this.selectionStart == TextField.this.cursor) {
            TextField.this.hasSelection = false;
         }

         super.touchUp(event, x, y, pointer, button);
      }

      protected void setCursorPosition(float x, float y) {
         TextField.this.lastBlink = 0L;
         TextField.this.cursorOn = false;
         TextField.this.cursor = TextField.this.letterUnderCursor(x);
      }

      protected void goHome(boolean jump) {
         TextField.this.cursor = 0;
      }

      protected void goEnd(boolean jump) {
         TextField.this.cursor = TextField.this.text.length();
      }

      @Override
      public boolean keyDown(InputEvent event, int keycode) {
         if (TextField.this.disabled) {
            return false;
         } else {
            TextField.this.lastBlink = 0L;
            TextField.this.cursorOn = false;
            Stage stage = TextField.this.getStage();
            if (stage != null && stage.getKeyboardFocus() == TextField.this) {
               boolean repeat = false;
               boolean ctrl = UIUtils.ctrl();
               boolean jump = ctrl && !TextField.this.passwordMode;
               if (ctrl) {
                  if (keycode == 50) {
                     TextField.this.paste(TextField.this.clipboard.getContents(), true);
                     repeat = true;
                  }

                  if (keycode == 31 || keycode == 133) {
                     TextField.this.copy();
                     return true;
                  }

                  if (keycode == 52) {
                     TextField.this.cut(true);
                     return true;
                  }

                  if (keycode == 29) {
                     TextField.this.selectAll();
                     return true;
                  }

                  if (keycode == 54) {
                     String oldText = TextField.this.text;
                     TextField.this.setText(TextField.this.undoText);
                     TextField.this.undoText = oldText;
                     TextField.this.updateDisplayText();
                     return true;
                  }
               }

               label80:
               if (UIUtils.shift()) {
                  if (keycode == 133) {
                     TextField.this.paste(TextField.this.clipboard.getContents(), true);
                  }

                  if (keycode == 112) {
                     TextField.this.cut(true);
                  }

                  int temp = TextField.this.cursor;
                  if (keycode == 21) {
                     TextField.this.moveCursor(false, jump);
                     repeat = true;
                  } else if (keycode == 22) {
                     TextField.this.moveCursor(true, jump);
                     repeat = true;
                  } else if (keycode == 3) {
                     this.goHome(jump);
                  } else {
                     if (keycode != 132) {
                        break label80;
                     }

                     this.goEnd(jump);
                  }

                  if (!TextField.this.hasSelection) {
                     TextField.this.selectionStart = temp;
                     TextField.this.hasSelection = true;
                  }
               } else {
                  if (keycode == 21) {
                     TextField.this.moveCursor(false, jump);
                     TextField.this.clearSelection();
                     repeat = true;
                  }

                  if (keycode == 22) {
                     TextField.this.moveCursor(true, jump);
                     TextField.this.clearSelection();
                     repeat = true;
                  }

                  if (keycode == 3) {
                     this.goHome(jump);
                     TextField.this.clearSelection();
                  }

                  if (keycode == 132) {
                     this.goEnd(jump);
                     TextField.this.clearSelection();
                  }
               }

               TextField.this.cursor = MathUtils.clamp(TextField.this.cursor, 0, TextField.this.text.length());
               if (repeat) {
                  this.scheduleKeyRepeatTask(keycode);
               }

               return true;
            } else {
               return false;
            }
         }
      }

      protected void scheduleKeyRepeatTask(int keycode) {
         if (!TextField.this.keyRepeatTask.isScheduled() || TextField.this.keyRepeatTask.keycode != keycode) {
            TextField.this.keyRepeatTask.keycode = keycode;
            TextField.this.keyRepeatTask.cancel();
            Timer.schedule(TextField.this.keyRepeatTask, TextField.keyRepeatInitialTime, TextField.keyRepeatTime);
         }
      }

      @Override
      public boolean keyUp(InputEvent event, int keycode) {
         if (TextField.this.disabled) {
            return false;
         } else {
            TextField.this.keyRepeatTask.cancel();
            return true;
         }
      }

      @Override
      public boolean keyTyped(InputEvent event, char character) {
         if (TextField.this.disabled) {
            return false;
         } else {
            switch (character) {
               case '\b':
               case '\t':
               case '\n':
               case '\r':
                  break;
               case '\u000b':
               case '\f':
               default:
                  if (character < ' ') {
                     return false;
                  }
            }

            Stage stage = TextField.this.getStage();
            if (stage == null || stage.getKeyboardFocus() != TextField.this) {
               return false;
            } else if (UIUtils.isMac && Gdx.input.isKeyPressed(63)) {
               return true;
            } else {
               if ((character == '\t' || character == '\n') && TextField.this.focusTraversal) {
                  TextField.this.next(UIUtils.shift());
               } else {
                  boolean delete = character == 127;
                  boolean backspace = character == '\b';
                  boolean enter = character == '\r' || character == '\n';
                  boolean add = enter ? TextField.this.writeEnters : !TextField.this.onlyFontChars || TextField.this.style.font.getData().hasGlyph(character);
                  boolean remove = backspace || delete;
                  if (add || remove) {
                     String oldText = TextField.this.text;
                     int oldCursor = TextField.this.cursor;
                     if (TextField.this.hasSelection) {
                        TextField.this.cursor = TextField.this.delete(false);
                     } else {
                        if (backspace && TextField.this.cursor > 0) {
                           TextField.this.text = TextField.this.text.substring(0, TextField.this.cursor - 1)
                              + TextField.this.text.substring(TextField.this.cursor--);
                           TextField.this.renderOffset = 0.0F;
                        }

                        if (delete && TextField.this.cursor < TextField.this.text.length()) {
                           TextField.this.text = TextField.this.text.substring(0, TextField.this.cursor)
                              + TextField.this.text.substring(TextField.this.cursor + 1);
                        }
                     }

                     if (add && !remove) {
                        if (!enter && TextField.this.filter != null && !TextField.this.filter.acceptChar(TextField.this, character)) {
                           return true;
                        }

                        if (!TextField.this.withinMaxLength(TextField.this.text.length())) {
                           return true;
                        }

                        String insertion = enter ? "\n" : String.valueOf(character);
                        TextField.this.text = TextField.this.insert(TextField.this.cursor++, insertion, TextField.this.text);
                     }

                     String tempUndoText = TextField.this.undoText;
                     if (TextField.this.changeText(oldText, TextField.this.text)) {
                        long time = System.currentTimeMillis();
                        if (time - 750L > TextField.this.lastChangeTime) {
                           TextField.this.undoText = oldText;
                        }

                        TextField.this.lastChangeTime = time;
                     } else {
                        TextField.this.cursor = oldCursor;
                     }

                     TextField.this.updateDisplayText();
                  }
               }

               if (TextField.this.listener != null) {
                  TextField.this.listener.keyTyped(TextField.this, character);
               }

               return true;
            }
         }
      }
   }

   public interface TextFieldFilter {
      boolean acceptChar(TextField var1, char var2);

      public static class DigitsOnlyFilter implements TextField.TextFieldFilter {
         @Override
         public boolean acceptChar(TextField textField, char c) {
            return Character.isDigit(c);
         }
      }
   }

   public interface TextFieldListener {
      void keyTyped(TextField var1, char var2);
   }

   public static class TextFieldStyle {
      public BitmapFont font;
      public Color fontColor;
      public Color focusedFontColor;
      public Color disabledFontColor;
      public Drawable background;
      public Drawable focusedBackground;
      public Drawable disabledBackground;
      public Drawable cursor;
      public Drawable selection;
      public BitmapFont messageFont;
      public Color messageFontColor;

      public TextFieldStyle() {
      }

      public TextFieldStyle(BitmapFont font, Color fontColor, Drawable cursor, Drawable selection, Drawable background) {
         this.background = background;
         this.cursor = cursor;
         this.font = font;
         this.fontColor = fontColor;
         this.selection = selection;
      }

      public TextFieldStyle(TextField.TextFieldStyle style) {
         this.messageFont = style.messageFont;
         if (style.messageFontColor != null) {
            this.messageFontColor = new Color(style.messageFontColor);
         }

         this.background = style.background;
         this.focusedBackground = style.focusedBackground;
         this.disabledBackground = style.disabledBackground;
         this.cursor = style.cursor;
         this.font = style.font;
         if (style.fontColor != null) {
            this.fontColor = new Color(style.fontColor);
         }

         if (style.focusedFontColor != null) {
            this.focusedFontColor = new Color(style.focusedFontColor);
         }

         if (style.disabledFontColor != null) {
            this.disabledFontColor = new Color(style.disabledFontColor);
         }

         this.selection = style.selection;
      }
   }
}
