package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Colors;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

public class GlyphLayout implements Pool.Poolable {
   public final Array<GlyphLayout.GlyphRun> runs = new Array<>();
   public float width;
   public float height;
   private final Array<Color> colorStack = new Array<>(4);

   public GlyphLayout() {
   }

   public GlyphLayout(BitmapFont font, CharSequence str) {
      this.setText(font, str);
   }

   public GlyphLayout(BitmapFont font, CharSequence str, Color color, float targetWidth, int halign, boolean wrap) {
      this.setText(font, str, color, targetWidth, halign, wrap);
   }

   public GlyphLayout(BitmapFont font, CharSequence str, int start, int end, Color color, float targetWidth, int halign, boolean wrap, String truncate) {
      this.setText(font, str, start, end, color, targetWidth, halign, wrap, truncate);
   }

   public void setText(BitmapFont font, CharSequence str) {
      this.setText(font, str, 0, str.length(), font.getColor(), 0.0F, 8, false, null);
   }

   public void setText(BitmapFont font, CharSequence str, Color color, float targetWidth, int halign, boolean wrap) {
      this.setText(font, str, 0, str.length(), color, targetWidth, halign, wrap, null);
   }

   public void setText(BitmapFont font, CharSequence str, int start, int end, Color color, float targetWidth, int halign, boolean wrap, String truncate) {
      if (truncate != null) {
         wrap = true;
      } else if (targetWidth <= font.data.spaceWidth) {
         wrap = false;
      }

      BitmapFont.BitmapFontData fontData = font.data;
      boolean markupEnabled = fontData.markupEnabled;
      Pool<GlyphLayout.GlyphRun> glyphRunPool = Pools.get(GlyphLayout.GlyphRun.class);
      Array<GlyphLayout.GlyphRun> runs = this.runs;
      glyphRunPool.freeAll(runs);
      runs.clear();
      float x = 0.0F;
      float y = 0.0F;
      float width = 0.0F;
      int lines = 0;
      int blankLines = 0;
      Array<Color> colorStack = this.colorStack;
      Color nextColor = color;
      colorStack.add(color);
      Pool<Color> colorPool = Pools.get(Color.class);
      int runStart = start;

      label150:
      while (true) {
         int runEnd = -1;
         boolean newline = false;
         boolean colorRun = false;
         if (start == end) {
            if (runStart == end) {
               break;
            }

            runEnd = end;
         } else {
            switch (str.charAt(start++)) {
               case '\n':
                  runEnd = start - 1;
                  newline = true;
                  break;
               case '[':
                  if (markupEnabled) {
                     int length = this.parseColorMarkup(str, start, end, colorPool);
                     if (length >= 0) {
                        runEnd = start - 1;
                        start += length + 1;
                        nextColor = colorStack.peek();
                        colorRun = true;
                     } else if (length == -2) {
                        start++;
                        continue;
                     }
                  }
            }
         }

         if (runEnd != -1) {
            if (runEnd != runStart) {
               GlyphLayout.GlyphRun run = glyphRunPool.obtain();
               run.color.set(color);
               run.x = x;
               run.y = y;
               fontData.getGlyphs(run, str, runStart, runEnd, colorRun);
               if (run.glyphs.size == 0) {
                  glyphRunPool.free(run);
               } else {
                  runs.add(run);
                  float[] xAdvances = run.xAdvances.items;
                  int i = 0;

                  for (int n = run.xAdvances.size; i < n; i++) {
                     float xAdvance = xAdvances[i];
                     x += xAdvance;
                     if (wrap
                        && x > targetWidth
                        && i > 1
                        && x - xAdvance + (run.glyphs.get(i - 1).xoffset + run.glyphs.get(i - 1).width) * fontData.scaleX - 1.0E-4F > targetWidth) {
                        if (truncate != null) {
                           this.truncate(fontData, run, targetWidth, truncate, i, glyphRunPool);
                           x = run.x + run.width;
                           break label150;
                        }

                        int wrapIndex = fontData.getWrapIndex(run.glyphs, i);
                        if (run.x == 0.0F && wrapIndex == 0 || wrapIndex >= run.glyphs.size) {
                           wrapIndex = i - 1;
                        }

                        GlyphLayout.GlyphRun next;
                        if (wrapIndex == 0) {
                           next = run;
                        } else {
                           next = this.wrap(fontData, run, glyphRunPool, wrapIndex, i);
                           runs.add(next);
                        }

                        width = Math.max(width, run.x + run.width);
                        x = 0.0F;
                        y += fontData.down;
                        lines++;
                        next.x = 0.0F;
                        next.y = y;
                        i = -1;
                        n = next.xAdvances.size;
                        xAdvances = next.xAdvances.items;
                        run = next;
                     } else {
                        run.width += xAdvance;
                     }
                  }
               }
            }

            if (newline) {
               width = Math.max(width, x);
               x = 0.0F;
               float down = fontData.down;
               if (runEnd == runStart) {
                  down *= fontData.blankLineScale;
                  blankLines++;
               } else {
                  lines++;
               }

               y += down;
            }

            runStart = start;
            color = nextColor;
         }
      }

      width = Math.max(width, x);
      int i = 1;

      for (int nx = colorStack.size; i < nx; i++) {
         colorPool.free(colorStack.get(i));
      }

      colorStack.clear();
      if ((halign & 8) == 0) {
         boolean center = (halign & 1) != 0;
         float lineWidth = 0.0F;
         float lineY = -2.1474836E9F;
         int lineStart = 0;
         int nx = runs.size;

         for (int ix = 0; ix < nx; ix++) {
            GlyphLayout.GlyphRun run = runs.get(ix);
            if (run.y != lineY) {
               lineY = run.y;
               float shift = targetWidth - lineWidth;
               if (center) {
                  shift /= 2.0F;
               }

               while (lineStart < ix) {
                  runs.get(lineStart++).x += shift;
               }

               lineWidth = 0.0F;
            }

            lineWidth += run.width;
         }

         float shift = targetWidth - lineWidth;
         if (center) {
            shift /= 2.0F;
         }

         while (lineStart < nx) {
            runs.get(lineStart++).x += shift;
         }
      }

      this.width = width;
      this.height = fontData.capHeight + lines * fontData.lineHeight + blankLines * fontData.lineHeight * fontData.blankLineScale;
   }

   private void truncate(
      BitmapFont.BitmapFontData fontData, GlyphLayout.GlyphRun run, float targetWidth, String truncate, int widthIndex, Pool<GlyphLayout.GlyphRun> glyphRunPool
   ) {
      GlyphLayout.GlyphRun truncateRun = glyphRunPool.obtain();
      fontData.getGlyphs(truncateRun, truncate, 0, truncate.length(), true);
      float truncateWidth = 0.0F;
      int i = 1;

      for (int n = truncateRun.xAdvances.size; i < n; i++) {
         truncateWidth += truncateRun.xAdvances.get(i);
      }

      targetWidth -= truncateWidth;
      i = 0;

      for (float width = run.x; i < run.xAdvances.size; i++) {
         float xAdvance = run.xAdvances.get(i);
         width += xAdvance;
         if (width > targetWidth) {
            run.width = width - run.x - xAdvance;
            break;
         }
      }

      if (i > 1) {
         run.glyphs.truncate(i - 1);
         run.xAdvances.truncate(i);
         this.adjustLastGlyph(fontData, run);
         if (truncateRun.xAdvances.size > 0) {
            run.xAdvances.addAll(truncateRun.xAdvances, 1, truncateRun.xAdvances.size - 1);
         }
      } else {
         run.glyphs.clear();
         run.xAdvances.clear();
         run.xAdvances.addAll(truncateRun.xAdvances);
         if (truncateRun.xAdvances.size > 0) {
            run.width = run.width + truncateRun.xAdvances.get(0);
         }
      }

      run.glyphs.addAll(truncateRun.glyphs);
      run.width += truncateWidth;
      glyphRunPool.free(truncateRun);
   }

   private GlyphLayout.GlyphRun wrap(
      BitmapFont.BitmapFontData fontData, GlyphLayout.GlyphRun first, Pool<GlyphLayout.GlyphRun> glyphRunPool, int wrapIndex, int widthIndex
   ) {
      GlyphLayout.GlyphRun second = glyphRunPool.obtain();
      second.color.set(first.color);
      int glyphCount = first.glyphs.size;

      while (widthIndex < wrapIndex) {
         first.width = first.width + first.xAdvances.get(widthIndex++);
      }

      while (widthIndex > wrapIndex + 1) {
         widthIndex--;
         first.width = first.width - first.xAdvances.get(widthIndex);
      }

      if (wrapIndex < glyphCount) {
         Array<BitmapFont.Glyph> glyphs1 = second.glyphs;
         Array<BitmapFont.Glyph> glyphs2 = first.glyphs;
         glyphs1.addAll(glyphs2, 0, wrapIndex);
         glyphs2.removeRange(0, wrapIndex - 1);
         first.glyphs = glyphs1;
         second.glyphs = glyphs2;
         FloatArray xAdvances1 = second.xAdvances;
         FloatArray xAdvances2 = first.xAdvances;
         xAdvances1.addAll(xAdvances2, 0, wrapIndex + 1);
         xAdvances2.removeRange(1, wrapIndex);
         xAdvances2.set(0, -glyphs2.first().xoffset * fontData.scaleX - fontData.padLeft);
         first.xAdvances = xAdvances1;
         second.xAdvances = xAdvances2;
      }

      if (wrapIndex == 0) {
         glyphRunPool.free(first);
         this.runs.pop();
      } else {
         this.adjustLastGlyph(fontData, first);
      }

      return second;
   }

   private void adjustLastGlyph(BitmapFont.BitmapFontData fontData, GlyphLayout.GlyphRun run) {
      BitmapFont.Glyph last = run.glyphs.peek();
      if (!fontData.isWhitespace((char)last.id)) {
         float width = (last.xoffset + last.width) * fontData.scaleX - fontData.padRight;
         run.width = run.width + (width - run.xAdvances.peek());
         run.xAdvances.set(run.xAdvances.size - 1, width);
      }
   }

   private int parseColorMarkup(CharSequence str, int start, int end, Pool<Color> colorPool) {
      if (start == end) {
         return -1;
      } else {
         switch (str.charAt(start)) {
            case '#':
               int colorInt = 0;
               int i = start + 1;

               for (; i < end; i++) {
                  char ch = str.charAt(i);
                  if (ch == ']') {
                     if (i >= start + 2 && i <= start + 9) {
                        if (i - start <= 7) {
                           int ii = 0;

                           for (int nn = 9 - (i - start); ii < nn; ii++) {
                              colorInt <<= 4;
                           }

                           colorInt |= 255;
                        }

                        Color color = colorPool.obtain();
                        this.colorStack.add(color);
                        Color.rgba8888ToColor(color, colorInt);
                        return i - start;
                     }
                     break;
                  }

                  if (ch >= '0' && ch <= '9') {
                     colorInt = colorInt * 16 + (ch - '0');
                  } else if (ch >= 'a' && ch <= 'f') {
                     colorInt = colorInt * 16 + (ch - 'W');
                  } else {
                     if (ch < 'A' || ch > 'F') {
                        break;
                     }

                     colorInt = colorInt * 16 + (ch - '7');
                  }
               }

               return -1;
            case '[':
               return -2;
            case ']':
               if (this.colorStack.size > 1) {
                  colorPool.free(this.colorStack.pop());
               }

               return 0;
            default:
               for (int i = start + 1; i < end; i++) {
                  char chx = str.charAt(i);
                  if (chx == ']') {
                     Color namedColor = Colors.get(str.subSequence(start, i).toString());
                     if (namedColor == null) {
                        return -1;
                     }

                     Color color = colorPool.obtain();
                     this.colorStack.add(color);
                     color.set(namedColor);
                     return i - start;
                  }
               }

               return -1;
         }
      }
   }

   @Override
   public void reset() {
      Pools.get(GlyphLayout.GlyphRun.class).freeAll(this.runs);
      this.runs.clear();
      this.width = 0.0F;
      this.height = 0.0F;
   }

   @Override
   public String toString() {
      if (this.runs.size == 0) {
         return "";
      } else {
         StringBuilder buffer = new StringBuilder(128);
         buffer.append(this.width);
         buffer.append('x');
         buffer.append(this.height);
         buffer.append('\n');
         int i = 0;

         for (int n = this.runs.size; i < n; i++) {
            buffer.append(this.runs.get(i).toString());
            buffer.append('\n');
         }

         buffer.setLength(buffer.length() - 1);
         return buffer.toString();
      }
   }

   public static class GlyphRun implements Pool.Poolable {
      public Array<BitmapFont.Glyph> glyphs = new Array<>();
      public FloatArray xAdvances = new FloatArray();
      public float x;
      public float y;
      public float width;
      public final Color color = new Color();

      @Override
      public void reset() {
         this.glyphs.clear();
         this.xAdvances.clear();
         this.width = 0.0F;
      }

      @Override
      public String toString() {
         StringBuilder buffer = new StringBuilder(this.glyphs.size);
         Array<BitmapFont.Glyph> glyphs = this.glyphs;
         int i = 0;

         for (int n = glyphs.size; i < n; i++) {
            BitmapFont.Glyph g = glyphs.get(i);
            buffer.append((char)g.id);
         }

         buffer.append(", #");
         buffer.append(this.color);
         buffer.append(", ");
         buffer.append(this.x);
         buffer.append(", ");
         buffer.append(this.y);
         buffer.append(", ");
         buffer.append(this.width);
         return buffer.toString();
      }
   }
}
