package com.badlogic.gdx.graphics.g2d.freetype;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class FreeType {
   public static int FT_PIXEL_MODE_NONE = 0;
   public static int FT_PIXEL_MODE_MONO = 1;
   public static int FT_PIXEL_MODE_GRAY = 2;
   public static int FT_PIXEL_MODE_GRAY2 = 3;
   public static int FT_PIXEL_MODE_GRAY4 = 4;
   public static int FT_PIXEL_MODE_LCD = 5;
   public static int FT_PIXEL_MODE_LCD_V = 6;
   public static int FT_ENCODING_NONE = 0;
   public static int FT_ENCODING_MS_SYMBOL = encode('s', 'y', 'm', 'b');
   public static int FT_ENCODING_UNICODE = encode('u', 'n', 'i', 'c');
   public static int FT_ENCODING_SJIS = encode('s', 'j', 'i', 's');
   public static int FT_ENCODING_GB2312 = encode('g', 'b', ' ', ' ');
   public static int FT_ENCODING_BIG5 = encode('b', 'i', 'g', '5');
   public static int FT_ENCODING_WANSUNG = encode('w', 'a', 'n', 's');
   public static int FT_ENCODING_JOHAB = encode('j', 'o', 'h', 'a');
   public static int FT_ENCODING_ADOBE_STANDARD = encode('A', 'D', 'O', 'B');
   public static int FT_ENCODING_ADOBE_EXPERT = encode('A', 'D', 'B', 'E');
   public static int FT_ENCODING_ADOBE_CUSTOM = encode('A', 'D', 'B', 'C');
   public static int FT_ENCODING_ADOBE_LATIN_1 = encode('l', 'a', 't', '1');
   public static int FT_ENCODING_OLD_LATIN_2 = encode('l', 'a', 't', '2');
   public static int FT_ENCODING_APPLE_ROMAN = encode('a', 'r', 'm', 'n');
   public static int FT_FACE_FLAG_SCALABLE = 1;
   public static int FT_FACE_FLAG_FIXED_SIZES = 2;
   public static int FT_FACE_FLAG_FIXED_WIDTH = 4;
   public static int FT_FACE_FLAG_SFNT = 8;
   public static int FT_FACE_FLAG_HORIZONTAL = 16;
   public static int FT_FACE_FLAG_VERTICAL = 32;
   public static int FT_FACE_FLAG_KERNING = 64;
   public static int FT_FACE_FLAG_FAST_GLYPHS = 128;
   public static int FT_FACE_FLAG_MULTIPLE_MASTERS = 256;
   public static int FT_FACE_FLAG_GLYPH_NAMES = 512;
   public static int FT_FACE_FLAG_EXTERNAL_STREAM = 1024;
   public static int FT_FACE_FLAG_HINTER = 2048;
   public static int FT_FACE_FLAG_CID_KEYED = 4096;
   public static int FT_FACE_FLAG_TRICKY = 8192;
   public static int FT_STYLE_FLAG_ITALIC = 1;
   public static int FT_STYLE_FLAG_BOLD = 2;
   public static int FT_LOAD_DEFAULT = 0;
   public static int FT_LOAD_NO_SCALE = 1;
   public static int FT_LOAD_NO_HINTING = 2;
   public static int FT_LOAD_RENDER = 4;
   public static int FT_LOAD_NO_BITMAP = 8;
   public static int FT_LOAD_VERTICAL_LAYOUT = 16;
   public static int FT_LOAD_FORCE_AUTOHINT = 32;
   public static int FT_LOAD_CROP_BITMAP = 64;
   public static int FT_LOAD_PEDANTIC = 128;
   public static int FT_LOAD_IGNORE_GLOBAL_ADVANCE_WIDTH = 512;
   public static int FT_LOAD_NO_RECURSE = 1024;
   public static int FT_LOAD_IGNORE_TRANSFORM = 2048;
   public static int FT_LOAD_MONOCHROME = 4096;
   public static int FT_LOAD_LINEAR_DESIGN = 8192;
   public static int FT_LOAD_NO_AUTOHINT = 32768;
   public static int FT_LOAD_TARGET_NORMAL = 0;
   public static int FT_LOAD_TARGET_LIGHT = 65536;
   public static int FT_LOAD_TARGET_MONO = 131072;
   public static int FT_LOAD_TARGET_LCD = 196608;
   public static int FT_LOAD_TARGET_LCD_V = 262144;
   public static int FT_RENDER_MODE_NORMAL = 0;
   public static int FT_RENDER_MODE_LIGHT = 1;
   public static int FT_RENDER_MODE_MONO = 2;
   public static int FT_RENDER_MODE_LCD = 3;
   public static int FT_RENDER_MODE_LCD_V = 4;
   public static int FT_RENDER_MODE_MAX = 5;
   public static int FT_KERNING_DEFAULT = 0;
   public static int FT_KERNING_UNFITTED = 1;
   public static int FT_KERNING_UNSCALED = 2;
   public static int FT_STROKER_LINECAP_BUTT = 0;
   public static int FT_STROKER_LINECAP_ROUND = 1;
   public static int FT_STROKER_LINECAP_SQUARE = 2;
   public static int FT_STROKER_LINEJOIN_ROUND = 0;
   public static int FT_STROKER_LINEJOIN_BEVEL = 1;
   public static int FT_STROKER_LINEJOIN_MITER_VARIABLE = 2;
   public static int FT_STROKER_LINEJOIN_MITER = FT_STROKER_LINEJOIN_MITER_VARIABLE;
   public static int FT_STROKER_LINEJOIN_MITER_FIXED = 3;

   static native int getLastErrorCode();

   private static int encode(char a, char b, char c, char d) {
      return a << 24 | b << 16 | c << 8 | d;
   }

   public static FreeType.Library initFreeType() {
      new SharedLibraryLoader().load("gdx-freetype");
      long address = initFreeTypeJni();
      if (address == 0L) {
         throw new GdxRuntimeException("Couldn't initialize FreeType library, FreeType error code: " + getLastErrorCode());
      } else {
         return new FreeType.Library(address);
      }
   }

   private static native long initFreeTypeJni();

   public static int toInt(int value) {
      return (value + 63 & -64) >> 6;
   }

   public static class Bitmap extends FreeType.Pointer {
      Bitmap(long address) {
         super(address);
      }

      public int getRows() {
         return getRows(this.address);
      }

      private static native int getRows(long var0);

      public int getWidth() {
         return getWidth(this.address);
      }

      private static native int getWidth(long var0);

      public int getPitch() {
         return getPitch(this.address);
      }

      private static native int getPitch(long var0);

      public ByteBuffer getBuffer() {
         return this.getRows() == 0 ? BufferUtils.newByteBuffer(1) : getBuffer(this.address);
      }

      private static native ByteBuffer getBuffer(long var0);

      public Pixmap getPixmap(Pixmap.Format format, Color color, float gamma) {
         int width = this.getWidth();
         int rows = this.getRows();
         ByteBuffer src = this.getBuffer();
         int pixelMode = this.getPixelMode();
         int rowBytes = Math.abs(this.getPitch());
         Pixmap pixmap;
         if (color == Color.WHITE && pixelMode == FreeType.FT_PIXEL_MODE_GRAY && rowBytes == width && gamma == 1.0F) {
            pixmap = new Pixmap(width, rows, Pixmap.Format.Alpha);
            BufferUtils.copy(src, pixmap.getPixels(), pixmap.getPixels().capacity());
         } else {
            pixmap = new Pixmap(width, rows, Pixmap.Format.RGBA8888);
            int rgba = Color.rgba8888(color);
            byte[] srcRow = new byte[rowBytes];
            int[] dstRow = new int[width];
            IntBuffer dst = pixmap.getPixels().asIntBuffer();
            if (pixelMode == FreeType.FT_PIXEL_MODE_MONO) {
               for (int y = 0; y < rows; y++) {
                  src.get(srcRow);
                  int i = 0;

                  for (int x = 0; x < width; x += 8) {
                     byte b = srcRow[i];
                     int ii = 0;

                     for (int n = Math.min(8, width - x); ii < n; ii++) {
                        if ((b & 1 << 7 - ii) != 0) {
                           dstRow[x + ii] = rgba;
                        } else {
                           dstRow[x + ii] = 0;
                        }
                     }

                     i++;
                  }

                  dst.put(dstRow);
               }
            } else {
               int rgb = rgba & -256;
               int a = rgba & 0xFF;

               for (int y = 0; y < rows; y++) {
                  src.get(srcRow);

                  for (int x = 0; x < width; x++) {
                     float alpha = (srcRow[x] & 255) / 255.0F;
                     alpha = (float)Math.pow(alpha, gamma);
                     dstRow[x] = rgb | (int)(a * alpha);
                  }

                  dst.put(dstRow);
               }
            }
         }

         Pixmap converted = pixmap;
         if (format != pixmap.getFormat()) {
            converted = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), format);
            Pixmap.Blending blending = Pixmap.getBlending();
            Pixmap.setBlending(Pixmap.Blending.None);
            converted.drawPixmap(pixmap, 0, 0);
            Pixmap.setBlending(blending);
            pixmap.dispose();
         }

         return converted;
      }

      public int getNumGray() {
         return getNumGray(this.address);
      }

      private static native int getNumGray(long var0);

      public int getPixelMode() {
         return getPixelMode(this.address);
      }

      private static native int getPixelMode(long var0);
   }

   public static class Face extends FreeType.Pointer implements Disposable {
      FreeType.Library library;

      public Face(long address, FreeType.Library library) {
         super(address);
         this.library = library;
      }

      @Override
      public void dispose() {
         doneFace(this.address);
         ByteBuffer buffer = this.library.fontData.get(this.address);
         if (buffer != null) {
            this.library.fontData.remove(this.address);
            BufferUtils.disposeUnsafeByteBuffer(buffer);
         }
      }

      private static native void doneFace(long var0);

      public int getFaceFlags() {
         return getFaceFlags(this.address);
      }

      private static native int getFaceFlags(long var0);

      public int getStyleFlags() {
         return getStyleFlags(this.address);
      }

      private static native int getStyleFlags(long var0);

      public int getNumGlyphs() {
         return getNumGlyphs(this.address);
      }

      private static native int getNumGlyphs(long var0);

      public int getAscender() {
         return getAscender(this.address);
      }

      private static native int getAscender(long var0);

      public int getDescender() {
         return getDescender(this.address);
      }

      private static native int getDescender(long var0);

      public int getHeight() {
         return getHeight(this.address);
      }

      private static native int getHeight(long var0);

      public int getMaxAdvanceWidth() {
         return getMaxAdvanceWidth(this.address);
      }

      private static native int getMaxAdvanceWidth(long var0);

      public int getMaxAdvanceHeight() {
         return getMaxAdvanceHeight(this.address);
      }

      private static native int getMaxAdvanceHeight(long var0);

      public int getUnderlinePosition() {
         return getUnderlinePosition(this.address);
      }

      private static native int getUnderlinePosition(long var0);

      public int getUnderlineThickness() {
         return getUnderlineThickness(this.address);
      }

      private static native int getUnderlineThickness(long var0);

      public boolean selectSize(int strikeIndex) {
         return selectSize(this.address, strikeIndex);
      }

      private static native boolean selectSize(long var0, int var2);

      public boolean setCharSize(int charWidth, int charHeight, int horzResolution, int vertResolution) {
         return setCharSize(this.address, charWidth, charHeight, horzResolution, vertResolution);
      }

      private static native boolean setCharSize(long var0, int var2, int var3, int var4, int var5);

      public boolean setPixelSizes(int pixelWidth, int pixelHeight) {
         return setPixelSizes(this.address, pixelWidth, pixelHeight);
      }

      private static native boolean setPixelSizes(long var0, int var2, int var3);

      public boolean loadGlyph(int glyphIndex, int loadFlags) {
         return loadGlyph(this.address, glyphIndex, loadFlags);
      }

      private static native boolean loadGlyph(long var0, int var2, int var3);

      public boolean loadChar(int charCode, int loadFlags) {
         return loadChar(this.address, charCode, loadFlags);
      }

      private static native boolean loadChar(long var0, int var2, int var3);

      public FreeType.GlyphSlot getGlyph() {
         return new FreeType.GlyphSlot(getGlyph(this.address));
      }

      private static native long getGlyph(long var0);

      public FreeType.Size getSize() {
         return new FreeType.Size(getSize(this.address));
      }

      private static native long getSize(long var0);

      public boolean hasKerning() {
         return hasKerning(this.address);
      }

      private static native boolean hasKerning(long var0);

      public int getKerning(int leftGlyph, int rightGlyph, int kernMode) {
         return getKerning(this.address, leftGlyph, rightGlyph, kernMode);
      }

      private static native int getKerning(long var0, int var2, int var3, int var4);

      public int getCharIndex(int charCode) {
         return getCharIndex(this.address, charCode);
      }

      private static native int getCharIndex(long var0, int var2);
   }

   public static class Glyph extends FreeType.Pointer implements Disposable {
      private boolean rendered;

      Glyph(long address) {
         super(address);
      }

      @Override
      public void dispose() {
         done(this.address);
      }

      private static native void done(long var0);

      public void strokeBorder(FreeType.Stroker stroker, boolean inside) {
         this.address = strokeBorder(this.address, stroker.address, inside);
      }

      private static native long strokeBorder(long var0, long var2, boolean var4);

      public void toBitmap(int renderMode) {
         long bitmap = toBitmap(this.address, renderMode);
         if (bitmap == 0L) {
            throw new GdxRuntimeException("Couldn't render glyph, FreeType error code: " + FreeType.getLastErrorCode());
         } else {
            this.address = bitmap;
            this.rendered = true;
         }
      }

      private static native long toBitmap(long var0, int var2);

      public FreeType.Bitmap getBitmap() {
         if (!this.rendered) {
            throw new GdxRuntimeException("Glyph is not yet rendered");
         } else {
            return new FreeType.Bitmap(getBitmap(this.address));
         }
      }

      private static native long getBitmap(long var0);

      public int getLeft() {
         if (!this.rendered) {
            throw new GdxRuntimeException("Glyph is not yet rendered");
         } else {
            return getLeft(this.address);
         }
      }

      private static native int getLeft(long var0);

      public int getTop() {
         if (!this.rendered) {
            throw new GdxRuntimeException("Glyph is not yet rendered");
         } else {
            return getTop(this.address);
         }
      }

      private static native int getTop(long var0);
   }

   public static class GlyphMetrics extends FreeType.Pointer {
      GlyphMetrics(long address) {
         super(address);
      }

      public int getWidth() {
         return getWidth(this.address);
      }

      private static native int getWidth(long var0);

      public int getHeight() {
         return getHeight(this.address);
      }

      private static native int getHeight(long var0);

      public int getHoriBearingX() {
         return getHoriBearingX(this.address);
      }

      private static native int getHoriBearingX(long var0);

      public int getHoriBearingY() {
         return getHoriBearingY(this.address);
      }

      private static native int getHoriBearingY(long var0);

      public int getHoriAdvance() {
         return getHoriAdvance(this.address);
      }

      private static native int getHoriAdvance(long var0);

      public int getVertBearingX() {
         return getVertBearingX(this.address);
      }

      private static native int getVertBearingX(long var0);

      public int getVertBearingY() {
         return getVertBearingY(this.address);
      }

      private static native int getVertBearingY(long var0);

      public int getVertAdvance() {
         return getVertAdvance(this.address);
      }

      private static native int getVertAdvance(long var0);
   }

   public static class GlyphSlot extends FreeType.Pointer {
      GlyphSlot(long address) {
         super(address);
      }

      public FreeType.GlyphMetrics getMetrics() {
         return new FreeType.GlyphMetrics(getMetrics(this.address));
      }

      private static native long getMetrics(long var0);

      public int getLinearHoriAdvance() {
         return getLinearHoriAdvance(this.address);
      }

      private static native int getLinearHoriAdvance(long var0);

      public int getLinearVertAdvance() {
         return getLinearVertAdvance(this.address);
      }

      private static native int getLinearVertAdvance(long var0);

      public int getAdvanceX() {
         return getAdvanceX(this.address);
      }

      private static native int getAdvanceX(long var0);

      public int getAdvanceY() {
         return getAdvanceY(this.address);
      }

      private static native int getAdvanceY(long var0);

      public int getFormat() {
         return getFormat(this.address);
      }

      private static native int getFormat(long var0);

      public FreeType.Bitmap getBitmap() {
         return new FreeType.Bitmap(getBitmap(this.address));
      }

      private static native long getBitmap(long var0);

      public int getBitmapLeft() {
         return getBitmapLeft(this.address);
      }

      private static native int getBitmapLeft(long var0);

      public int getBitmapTop() {
         return getBitmapTop(this.address);
      }

      private static native int getBitmapTop(long var0);

      public boolean renderGlyph(int renderMode) {
         return renderGlyph(this.address, renderMode);
      }

      private static native boolean renderGlyph(long var0, int var2);

      public FreeType.Glyph getGlyph() {
         long glyph = getGlyph(this.address);
         if (glyph == 0L) {
            throw new GdxRuntimeException("Couldn't get glyph, FreeType error code: " + FreeType.getLastErrorCode());
         } else {
            return new FreeType.Glyph(glyph);
         }
      }

      private static native long getGlyph(long var0);
   }

   public static class Library extends FreeType.Pointer implements Disposable {
      LongMap<ByteBuffer> fontData = new LongMap<>();

      Library(long address) {
         super(address);
      }

      @Override
      public void dispose() {
         doneFreeType(this.address);

         for (ByteBuffer buffer : this.fontData.values()) {
            BufferUtils.disposeUnsafeByteBuffer(buffer);
         }
      }

      private static native void doneFreeType(long var0);

      public FreeType.Face newFace(FileHandle font, int faceIndex) {
         byte[] data = font.readBytes();
         return this.newMemoryFace(data, data.length, faceIndex);
      }

      public FreeType.Face newMemoryFace(byte[] data, int dataSize, int faceIndex) {
         ByteBuffer buffer = BufferUtils.newUnsafeByteBuffer(data.length);
         BufferUtils.copy(data, 0, buffer, data.length);
         return this.newMemoryFace(buffer, faceIndex);
      }

      public FreeType.Face newMemoryFace(ByteBuffer buffer, int faceIndex) {
         long face = newMemoryFace(this.address, buffer, buffer.remaining(), faceIndex);
         if (face == 0L) {
            BufferUtils.disposeUnsafeByteBuffer(buffer);
            throw new GdxRuntimeException("Couldn't load font, FreeType error code: " + FreeType.getLastErrorCode());
         } else {
            this.fontData.put(face, buffer);
            return new FreeType.Face(face, this);
         }
      }

      private static native long newMemoryFace(long var0, ByteBuffer var2, int var3, int var4);

      public FreeType.Stroker createStroker() {
         long stroker = strokerNew(this.address);
         if (stroker == 0L) {
            throw new GdxRuntimeException("Couldn't create FreeType stroker, FreeType error code: " + FreeType.getLastErrorCode());
         } else {
            return new FreeType.Stroker(stroker);
         }
      }

      private static native long strokerNew(long var0);
   }

   private static class Pointer {
      long address;

      Pointer(long address) {
         this.address = address;
      }
   }

   public static class Size extends FreeType.Pointer {
      Size(long address) {
         super(address);
      }

      public FreeType.SizeMetrics getMetrics() {
         return new FreeType.SizeMetrics(getMetrics(this.address));
      }

      private static native long getMetrics(long var0);
   }

   public static class SizeMetrics extends FreeType.Pointer {
      SizeMetrics(long address) {
         super(address);
      }

      public int getXppem() {
         return getXppem(this.address);
      }

      private static native int getXppem(long var0);

      public int getYppem() {
         return getYppem(this.address);
      }

      private static native int getYppem(long var0);

      public int getXScale() {
         return getXscale(this.address);
      }

      private static native int getXscale(long var0);

      public int getYscale() {
         return getYscale(this.address);
      }

      private static native int getYscale(long var0);

      public int getAscender() {
         return getAscender(this.address);
      }

      private static native int getAscender(long var0);

      public int getDescender() {
         return getDescender(this.address);
      }

      private static native int getDescender(long var0);

      public int getHeight() {
         return getHeight(this.address);
      }

      private static native int getHeight(long var0);

      public int getMaxAdvance() {
         return getMaxAdvance(this.address);
      }

      private static native int getMaxAdvance(long var0);
   }

   public static class Stroker extends FreeType.Pointer implements Disposable {
      Stroker(long address) {
         super(address);
      }

      public void set(int radius, int lineCap, int lineJoin, int miterLimit) {
         set(this.address, radius, lineCap, lineJoin, miterLimit);
      }

      private static native void set(long var0, int var2, int var3, int var4, int var5);

      @Override
      public void dispose() {
         done(this.address);
      }

      private static native void done(long var0);
   }
}
