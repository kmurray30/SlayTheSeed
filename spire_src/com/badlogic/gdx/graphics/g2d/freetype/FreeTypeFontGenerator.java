package com.badlogic.gdx.graphics.g2d.freetype;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class FreeTypeFontGenerator implements Disposable {
   public static final String DEFAULT_CHARS = "\u0000ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890\"!`?'.,;:()[]{}<>|/@\\^$€-%+=#_&~*\u007f\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087\u0088\u0089\u008a\u008b\u008c\u008d\u008e\u008f\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097\u0098\u0099\u009a\u009b\u009c\u009d\u009e\u009f ¡¢£¤¥¦§¨©ª«¬\u00ad®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ";
   public static final int NO_MAXIMUM = -1;
   private static int maxTextureSize = 1024;
   final FreeType.Library library;
   final FreeType.Face face;
   final String name;
   boolean bitmapped = false;
   private int pixelWidth;
   private int pixelHeight;

   public FreeTypeFontGenerator(FileHandle fontFile) {
      this.name = fontFile.pathWithoutExtension();
      int fileSize = (int)fontFile.length();
      this.library = FreeType.initFreeType();
      if (this.library == null) {
         throw new GdxRuntimeException("Couldn't initialize FreeType");
      } else {
         InputStream input = fontFile.read();

         ByteBuffer buffer;
         try {
            if (fileSize == 0) {
               byte[] data = StreamUtils.copyStreamToByteArray(input, fileSize > 0 ? (int)(fileSize * 1.5F) : 16384);
               buffer = BufferUtils.newUnsafeByteBuffer(data.length);
               BufferUtils.copy(data, 0, buffer, data.length);
            } else {
               buffer = BufferUtils.newUnsafeByteBuffer(fileSize);
               StreamUtils.copyStream(input, buffer);
            }
         } catch (IOException var9) {
            throw new GdxRuntimeException(var9);
         } finally {
            StreamUtils.closeQuietly(input);
         }

         this.face = this.library.newMemoryFace(buffer, 0);
         if (this.face == null) {
            throw new GdxRuntimeException("Couldn't create face for font: " + fontFile);
         } else if (!this.checkForBitmapFont()) {
            this.setPixelSizes(0, 15);
         }
      }
   }

   private int getLoadingFlags(FreeTypeFontGenerator.FreeTypeFontParameter parameter) {
      int loadingFlags = FreeType.FT_LOAD_DEFAULT;
      switch (parameter.hinting) {
         case None:
            loadingFlags |= FreeType.FT_LOAD_NO_HINTING;
            break;
         case Slight:
            loadingFlags |= FreeType.FT_LOAD_TARGET_LIGHT;
            break;
         case Medium:
            loadingFlags |= FreeType.FT_LOAD_TARGET_NORMAL;
            break;
         case Full:
            loadingFlags |= FreeType.FT_LOAD_TARGET_MONO;
            break;
         case AutoSlight:
            loadingFlags |= FreeType.FT_LOAD_FORCE_AUTOHINT | FreeType.FT_LOAD_TARGET_LIGHT;
            break;
         case AutoMedium:
            loadingFlags |= FreeType.FT_LOAD_FORCE_AUTOHINT | FreeType.FT_LOAD_TARGET_NORMAL;
            break;
         case AutoFull:
            loadingFlags |= FreeType.FT_LOAD_FORCE_AUTOHINT | FreeType.FT_LOAD_TARGET_MONO;
      }

      return loadingFlags;
   }

   private boolean loadChar(int c) {
      return this.loadChar(c, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_FORCE_AUTOHINT);
   }

   private boolean loadChar(int c, int flags) {
      return this.face.loadChar(c, flags);
   }

   private boolean checkForBitmapFont() {
      int faceFlags = this.face.getFaceFlags();
      if ((faceFlags & FreeType.FT_FACE_FLAG_FIXED_SIZES) == FreeType.FT_FACE_FLAG_FIXED_SIZES
         && (faceFlags & FreeType.FT_FACE_FLAG_HORIZONTAL) == FreeType.FT_FACE_FLAG_HORIZONTAL
         && this.loadChar(32)) {
         FreeType.GlyphSlot slot = this.face.getGlyph();
         if (slot.getFormat() == 1651078259) {
            this.bitmapped = true;
         }
      }

      return this.bitmapped;
   }

   public BitmapFont generateFont(FreeTypeFontGenerator.FreeTypeFontParameter parameter) {
      return this.generateFont(parameter, new FreeTypeFontGenerator.FreeTypeBitmapFontData());
   }

   public BitmapFont generateFont(FreeTypeFontGenerator.FreeTypeFontParameter parameter, FreeTypeFontGenerator.FreeTypeBitmapFontData data) {
      this.generateData(parameter, data);
      if (data.regions == null && parameter.packer != null) {
         data.regions = new Array<>();
         parameter.packer.updateTextureRegions(data.regions, parameter.minFilter, parameter.magFilter, parameter.genMipMaps);
      }

      BitmapFont font = new BitmapFont(data, data.regions, true);
      font.setOwnsTexture(parameter.packer == null);
      return font;
   }

   public int scaleForPixelHeight(int height) {
      this.setPixelSizes(0, height);
      FreeType.SizeMetrics fontMetrics = this.face.getSize().getMetrics();
      int ascent = FreeType.toInt(fontMetrics.getAscender());
      int descent = FreeType.toInt(fontMetrics.getDescender());
      return height * height / (ascent - descent);
   }

   public int scaleForPixelWidth(int width, int numChars) {
      FreeType.SizeMetrics fontMetrics = this.face.getSize().getMetrics();
      int advance = FreeType.toInt(fontMetrics.getMaxAdvance());
      int ascent = FreeType.toInt(fontMetrics.getAscender());
      int descent = FreeType.toInt(fontMetrics.getDescender());
      int unscaledHeight = ascent - descent;
      int height = unscaledHeight * width / (advance * numChars);
      this.setPixelSizes(0, height);
      return height;
   }

   public int scaleToFitSquare(int width, int height, int numChars) {
      return Math.min(this.scaleForPixelHeight(height), this.scaleForPixelWidth(width, numChars));
   }

   public FreeTypeFontGenerator.GlyphAndBitmap generateGlyphAndBitmap(int c, int size, boolean flip) {
      this.setPixelSizes(0, size);
      FreeType.SizeMetrics fontMetrics = this.face.getSize().getMetrics();
      int baseline = FreeType.toInt(fontMetrics.getAscender());
      if (this.face.getCharIndex(c) == 0) {
         return null;
      } else if (!this.loadChar(c)) {
         throw new GdxRuntimeException("Unable to load character!");
      } else {
         FreeType.GlyphSlot slot = this.face.getGlyph();
         FreeType.Bitmap bitmap;
         if (this.bitmapped) {
            bitmap = slot.getBitmap();
         } else if (!slot.renderGlyph(FreeType.FT_RENDER_MODE_NORMAL)) {
            bitmap = null;
         } else {
            bitmap = slot.getBitmap();
         }

         FreeType.GlyphMetrics metrics = slot.getMetrics();
         BitmapFont.Glyph glyph = new BitmapFont.Glyph();
         if (bitmap != null) {
            glyph.width = bitmap.getWidth();
            glyph.height = bitmap.getRows();
         } else {
            glyph.width = 0;
            glyph.height = 0;
         }

         glyph.xoffset = slot.getBitmapLeft();
         glyph.yoffset = flip ? -slot.getBitmapTop() + baseline : -(glyph.height - slot.getBitmapTop()) - baseline;
         glyph.xadvance = FreeType.toInt(metrics.getHoriAdvance());
         glyph.srcX = 0;
         glyph.srcY = 0;
         glyph.id = c;
         FreeTypeFontGenerator.GlyphAndBitmap result = new FreeTypeFontGenerator.GlyphAndBitmap();
         result.glyph = glyph;
         result.bitmap = bitmap;
         return result;
      }
   }

   public FreeTypeFontGenerator.FreeTypeBitmapFontData generateData(int size) {
      FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
      parameter.size = size;
      return this.generateData(parameter);
   }

   public FreeTypeFontGenerator.FreeTypeBitmapFontData generateData(FreeTypeFontGenerator.FreeTypeFontParameter parameter) {
      return this.generateData(parameter, new FreeTypeFontGenerator.FreeTypeBitmapFontData());
   }

   void setPixelSizes(int pixelWidth, int pixelHeight) {
      this.pixelWidth = pixelWidth;
      this.pixelHeight = pixelHeight;
      if (!this.bitmapped && !this.face.setPixelSizes(pixelWidth, pixelHeight)) {
         throw new GdxRuntimeException("Couldn't set size for font");
      }
   }

   public FreeTypeFontGenerator.FreeTypeBitmapFontData generateData(
      FreeTypeFontGenerator.FreeTypeFontParameter parameter, FreeTypeFontGenerator.FreeTypeBitmapFontData data
   ) {
      parameter = parameter == null ? new FreeTypeFontGenerator.FreeTypeFontParameter() : parameter;
      char[] characters = parameter.characters.toCharArray();
      int charactersLength = characters.length;
      boolean incremental = parameter.incremental;
      int flags = this.getLoadingFlags(parameter);
      this.setPixelSizes(0, parameter.size);
      FreeType.SizeMetrics fontMetrics = this.face.getSize().getMetrics();
      data.flipped = parameter.flip;
      data.ascent = FreeType.toInt(fontMetrics.getAscender());
      data.descent = FreeType.toInt(fontMetrics.getDescender());
      data.lineHeight = FreeType.toInt(fontMetrics.getHeight());
      float baseLine = data.ascent;
      if (this.bitmapped && data.lineHeight == 0.0F) {
         for (int c = 32; c < 32 + this.face.getNumGlyphs(); c++) {
            if (this.loadChar(c, flags)) {
               int lh = FreeType.toInt(this.face.getGlyph().getMetrics().getHeight());
               data.lineHeight = lh > data.lineHeight ? lh : data.lineHeight;
            }
         }
      }

      data.lineHeight = data.lineHeight + parameter.spaceY;
      if (!this.loadChar(32, flags) && !this.loadChar(108, flags)) {
         data.spaceWidth = this.face.getMaxAdvanceWidth();
      } else {
         data.spaceWidth = FreeType.toInt(this.face.getGlyph().getMetrics().getHoriAdvance());
      }

      for (char xChar : data.xChars) {
         if (this.loadChar(xChar, flags)) {
            data.xHeight = FreeType.toInt(this.face.getGlyph().getMetrics().getHeight());
            break;
         }
      }

      if (data.xHeight == 0.0F) {
         throw new GdxRuntimeException("No x-height character found in font");
      } else {
         for (char capChar : data.capChars) {
            if (this.loadChar(capChar, flags)) {
               data.capHeight = FreeType.toInt(this.face.getGlyph().getMetrics().getHeight());
               break;
            }
         }

         if (!this.bitmapped && data.capHeight == 1.0F) {
            throw new GdxRuntimeException("No cap character found in font");
         } else {
            data.ascent = data.ascent - data.capHeight;
            data.down = -data.lineHeight;
            if (parameter.flip) {
               data.ascent = -data.ascent;
               data.down = -data.down;
            }

            boolean ownsAtlas = false;
            PixmapPacker packer = parameter.packer;
            if (packer == null) {
               int size;
               PixmapPacker.PackStrategy packStrategy;
               if (incremental) {
                  size = maxTextureSize;
                  packStrategy = new PixmapPacker.GuillotineStrategy();
               } else {
                  int maxGlyphHeight = (int)Math.ceil(data.lineHeight);
                  size = MathUtils.nextPowerOfTwo((int)Math.sqrt(maxGlyphHeight * maxGlyphHeight * charactersLength));
                  if (maxTextureSize > 0) {
                     size = Math.min(size, maxTextureSize);
                  }

                  packStrategy = new PixmapPacker.SkylineStrategy();
               }

               ownsAtlas = true;
               packer = new PixmapPacker(size, size, Pixmap.Format.RGBA8888, 1, false, packStrategy);
               packer.setTransparentColor(parameter.color);
               packer.getTransparentColor().a = 0.0F;
               if (parameter.borderWidth > 0.0F) {
                  packer.setTransparentColor(parameter.borderColor);
                  packer.getTransparentColor().a = 0.0F;
               }
            }

            if (incremental) {
               data.glyphs = new Array<>(charactersLength + 32);
            }

            FreeType.Stroker stroker = null;
            if (parameter.borderWidth > 0.0F) {
               stroker = this.library.createStroker();
               stroker.set(
                  (int)(parameter.borderWidth * 64.0F),
                  parameter.borderStraight ? FreeType.FT_STROKER_LINECAP_BUTT : FreeType.FT_STROKER_LINECAP_ROUND,
                  parameter.borderStraight ? FreeType.FT_STROKER_LINEJOIN_MITER_FIXED : FreeType.FT_STROKER_LINEJOIN_ROUND,
                  0
               );
            }

            BitmapFont.Glyph missingGlyph = this.createGlyph('\u0000', data, parameter, stroker, baseLine, packer);
            if (missingGlyph != null && missingGlyph.width != 0 && missingGlyph.height != 0) {
               data.setGlyph(0, missingGlyph);
               if (incremental) {
                  data.glyphs.add(missingGlyph);
               }
            }

            int[] heights = new int[charactersLength];
            int i = 0;

            for (int n = charactersLength; i < n; i++) {
               int height = this.loadChar(characters[i], flags) ? FreeType.toInt(this.face.getGlyph().getMetrics().getHeight()) : 0;
               heights[i] = height;
            }

            i = heights.length;

            while (i > 0) {
               int best = 0;
               int maxHeight = heights[0];

               for (int ix = 1; ix < i; ix++) {
                  int height = heights[ix];
                  if (height > maxHeight) {
                     maxHeight = height;
                     best = ix;
                  }
               }

               char cx = characters[best];
               BitmapFont.Glyph glyph = this.createGlyph(cx, data, parameter, stroker, baseLine, packer);
               if (glyph != null) {
                  data.setGlyph(cx, glyph);
                  if (incremental) {
                     data.glyphs.add(glyph);
                  }
               }

               heights[best] = heights[--i];
               char tmpChar = characters[best];
               characters[best] = characters[i];
               characters[i] = tmpChar;
            }

            if (stroker != null && !incremental) {
               stroker.dispose();
            }

            if (incremental) {
               data.generator = this;
               data.parameter = parameter;
               data.stroker = stroker;
               data.packer = packer;
            }

            parameter.kerning = parameter.kerning & this.face.hasKerning();
            if (parameter.kerning) {
               for (int ixx = 0; ixx < charactersLength; ixx++) {
                  char firstChar = characters[ixx];
                  BitmapFont.Glyph first = data.getGlyph(firstChar);
                  if (first != null) {
                     int firstIndex = this.face.getCharIndex(firstChar);

                     for (int ii = ixx; ii < charactersLength; ii++) {
                        char secondChar = characters[ii];
                        BitmapFont.Glyph second = data.getGlyph(secondChar);
                        if (second != null) {
                           int secondIndex = this.face.getCharIndex(secondChar);
                           int kerning = this.face.getKerning(firstIndex, secondIndex, 0);
                           if (kerning != 0) {
                              first.setKerning(secondChar, FreeType.toInt(kerning));
                           }

                           kerning = this.face.getKerning(secondIndex, firstIndex, 0);
                           if (kerning != 0) {
                              second.setKerning(firstChar, FreeType.toInt(kerning));
                           }
                        }
                     }
                  }
               }
            }

            if (ownsAtlas) {
               data.regions = new Array<>();
               packer.updateTextureRegions(data.regions, parameter.minFilter, parameter.magFilter, parameter.genMipMaps);
            }

            BitmapFont.Glyph spaceGlyph = data.getGlyph(' ');
            if (spaceGlyph == null) {
               spaceGlyph = new BitmapFont.Glyph();
               spaceGlyph.xadvance = (int)data.spaceWidth + parameter.spaceX;
               spaceGlyph.id = 32;
               data.setGlyph(32, spaceGlyph);
            }

            if (spaceGlyph.width == 0) {
               spaceGlyph.width = (int)(spaceGlyph.xadvance + data.padRight);
            }

            return data;
         }
      }
   }

   BitmapFont.Glyph createGlyph(
      char c,
      FreeTypeFontGenerator.FreeTypeBitmapFontData data,
      FreeTypeFontGenerator.FreeTypeFontParameter parameter,
      FreeType.Stroker stroker,
      float baseLine,
      PixmapPacker packer
   ) {
      boolean missing = this.face.getCharIndex(c) == 0 && c != 0;
      if (missing) {
         return null;
      } else if (!this.loadChar(c, this.getLoadingFlags(parameter))) {
         return null;
      } else {
         FreeType.GlyphSlot slot = this.face.getGlyph();
         FreeType.Glyph mainGlyph = slot.getGlyph();

         try {
            mainGlyph.toBitmap(parameter.mono ? FreeType.FT_RENDER_MODE_MONO : FreeType.FT_RENDER_MODE_NORMAL);
         } catch (GdxRuntimeException var34) {
            mainGlyph.dispose();
            Gdx.app.log("FreeTypeFontGenerator", "Couldn't render char: " + c);
            return null;
         }

         FreeType.Bitmap mainBitmap = mainGlyph.getBitmap();
         Pixmap mainPixmap = mainBitmap.getPixmap(Pixmap.Format.RGBA8888, parameter.color, parameter.gamma);
         if (mainBitmap.getWidth() != 0 && mainBitmap.getRows() != 0) {
            int offsetX = 0;
            int offsetY = 0;
            if (parameter.borderWidth > 0.0F) {
               int top = mainGlyph.getTop();
               int left = mainGlyph.getLeft();
               FreeType.Glyph borderGlyph = slot.getGlyph();
               borderGlyph.strokeBorder(stroker, false);
               borderGlyph.toBitmap(parameter.mono ? FreeType.FT_RENDER_MODE_MONO : FreeType.FT_RENDER_MODE_NORMAL);
               offsetX = left - borderGlyph.getLeft();
               offsetY = -(top - borderGlyph.getTop());
               FreeType.Bitmap borderBitmap = borderGlyph.getBitmap();
               Pixmap borderPixmap = borderBitmap.getPixmap(Pixmap.Format.RGBA8888, parameter.borderColor, parameter.borderGamma);
               int i = 0;

               for (int n = parameter.renderCount; i < n; i++) {
                  borderPixmap.drawPixmap(mainPixmap, offsetX, offsetY);
               }

               mainPixmap.dispose();
               mainGlyph.dispose();
               mainPixmap = borderPixmap;
               mainGlyph = borderGlyph;
            }

            if (parameter.shadowOffsetX != 0 || parameter.shadowOffsetY != 0) {
               int mainW = mainPixmap.getWidth();
               int mainH = mainPixmap.getHeight();
               int shadowOffsetX = Math.max(parameter.shadowOffsetX, 0);
               int shadowOffsetY = Math.max(parameter.shadowOffsetY, 0);
               int shadowW = mainW + Math.abs(parameter.shadowOffsetX);
               int shadowH = mainH + Math.abs(parameter.shadowOffsetY);
               Pixmap shadowPixmap = new Pixmap(shadowW, shadowH, mainPixmap.getFormat());
               Color shadowColor = parameter.shadowColor;
               byte r = (byte)(shadowColor.r * 255.0F);
               byte g = (byte)(shadowColor.g * 255.0F);
               byte b = (byte)(shadowColor.b * 255.0F);
               float a = shadowColor.a;
               ByteBuffer mainPixels = mainPixmap.getPixels();
               ByteBuffer shadowPixels = shadowPixmap.getPixels();

               for (int y = 0; y < mainH; y++) {
                  int shadowRow = shadowW * (y + shadowOffsetY) + shadowOffsetX;

                  for (int x = 0; x < mainW; x++) {
                     int mainPixel = (mainW * y + x) * 4;
                     byte mainA = mainPixels.get(mainPixel + 3);
                     if (mainA != 0) {
                        int shadowPixel = (shadowRow + x) * 4;
                        shadowPixels.put(shadowPixel, r);
                        shadowPixels.put(shadowPixel + 1, g);
                        shadowPixels.put(shadowPixel + 2, b);
                        shadowPixels.put(shadowPixel + 3, (byte)((mainA & 255) * a));
                     }
                  }
               }

               int i = 0;

               for (int n = parameter.renderCount; i < n; i++) {
                  shadowPixmap.drawPixmap(mainPixmap, Math.max(-parameter.shadowOffsetX, 0), Math.max(-parameter.shadowOffsetY, 0));
               }

               mainPixmap.dispose();
               mainPixmap = shadowPixmap;
            } else if (parameter.borderWidth == 0.0F) {
               int i = 0;

               for (int n = parameter.renderCount - 1; i < n; i++) {
                  mainPixmap.drawPixmap(mainPixmap, 0, 0);
               }
            }
         }

         FreeType.GlyphMetrics metrics = slot.getMetrics();
         BitmapFont.Glyph glyph = new BitmapFont.Glyph();
         glyph.id = c;
         glyph.width = mainPixmap.getWidth();
         glyph.height = mainPixmap.getHeight();
         glyph.xoffset = mainGlyph.getLeft();
         glyph.yoffset = parameter.flip ? -mainGlyph.getTop() + (int)baseLine : -(glyph.height - mainGlyph.getTop()) - (int)baseLine;
         glyph.xadvance = FreeType.toInt(metrics.getHoriAdvance()) + (int)parameter.borderWidth + parameter.spaceX;
         if (this.bitmapped) {
            mainPixmap.setColor(Color.CLEAR);
            mainPixmap.fill();
            ByteBuffer buf = mainBitmap.getBuffer();
            int whiteIntBits = Color.WHITE.toIntBits();
            int clearIntBits = Color.CLEAR.toIntBits();

            for (int h = 0; h < glyph.height; h++) {
               int idx = h * mainBitmap.getPitch();

               for (int w = 0; w < glyph.width + glyph.xoffset; w++) {
                  int bit = buf.get(idx + w / 8) >>> 7 - w % 8 & 1;
                  mainPixmap.drawPixel(w, h, bit == 1 ? whiteIntBits : clearIntBits);
               }
            }
         }

         Rectangle rect = packer.pack(mainPixmap);
         glyph.page = packer.getPages().size - 1;
         glyph.srcX = (int)rect.x;
         glyph.srcY = (int)rect.y;
         if (parameter.incremental && data.regions != null && data.regions.size <= glyph.page) {
            packer.updateTextureRegions(data.regions, parameter.minFilter, parameter.magFilter, parameter.genMipMaps);
         }

         mainPixmap.dispose();
         mainGlyph.dispose();
         return glyph;
      }
   }

   @Override
   public void dispose() {
      this.face.dispose();
      this.library.dispose();
   }

   public static void setMaxTextureSize(int texSize) {
      maxTextureSize = texSize;
   }

   public static int getMaxTextureSize() {
      return maxTextureSize;
   }

   public static class FreeTypeBitmapFontData extends BitmapFont.BitmapFontData implements Disposable {
      Array<TextureRegion> regions;
      FreeTypeFontGenerator generator;
      FreeTypeFontGenerator.FreeTypeFontParameter parameter;
      FreeType.Stroker stroker;
      PixmapPacker packer;
      Array<BitmapFont.Glyph> glyphs;
      private boolean dirty;

      @Override
      public BitmapFont.Glyph getGlyph(char ch) {
         BitmapFont.Glyph glyph = super.getGlyph(ch);
         if (glyph == null && this.generator != null) {
            this.generator.setPixelSizes(0, this.parameter.size);
            float baseline = ((this.flipped ? -this.ascent : this.ascent) + this.capHeight) / this.scaleY;
            glyph = this.generator.createGlyph(ch, this, this.parameter, this.stroker, baseline, this.packer);
            if (glyph == null) {
               return this.missingGlyph;
            }

            this.setGlyphRegion(glyph, this.regions.get(glyph.page));
            this.setGlyph(ch, glyph);
            this.glyphs.add(glyph);
            this.dirty = true;
            FreeType.Face face = this.generator.face;
            if (this.parameter.kerning) {
               int glyphIndex = face.getCharIndex(ch);
               int i = 0;

               for (int n = this.glyphs.size; i < n; i++) {
                  BitmapFont.Glyph other = this.glyphs.get(i);
                  int otherIndex = face.getCharIndex(other.id);
                  int kerning = face.getKerning(glyphIndex, otherIndex, 0);
                  if (kerning != 0) {
                     glyph.setKerning(other.id, FreeType.toInt(kerning));
                  }

                  kerning = face.getKerning(otherIndex, glyphIndex, 0);
                  if (kerning != 0) {
                     other.setKerning(ch, FreeType.toInt(kerning));
                  }
               }
            }
         }

         return glyph;
      }

      @Override
      public void getGlyphs(GlyphLayout.GlyphRun run, CharSequence str, int start, int end, boolean tightBounds) {
         if (this.packer != null) {
            this.packer.setPackToTexture(true);
         }

         super.getGlyphs(run, str, start, end, tightBounds);
         if (this.dirty) {
            this.dirty = false;
            this.packer.updateTextureRegions(this.regions, this.parameter.minFilter, this.parameter.magFilter, this.parameter.genMipMaps);
         }
      }

      @Override
      public void dispose() {
         if (this.stroker != null) {
            this.stroker.dispose();
         }

         if (this.packer != null) {
            this.packer.dispose();
         }
      }
   }

   public static class FreeTypeFontParameter {
      public int size = 16;
      public boolean mono;
      public FreeTypeFontGenerator.Hinting hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
      public Color color = Color.WHITE;
      public float gamma = 1.8F;
      public int renderCount = 2;
      public float borderWidth = 0.0F;
      public Color borderColor = Color.BLACK;
      public boolean borderStraight = false;
      public float borderGamma = 1.8F;
      public int shadowOffsetX = 0;
      public int shadowOffsetY = 0;
      public Color shadowColor = new Color(0.0F, 0.0F, 0.0F, 0.75F);
      public int spaceX;
      public int spaceY;
      public String characters = "\u0000ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890\"!`?'.,;:()[]{}<>|/@\\^$€-%+=#_&~*\u007f\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087\u0088\u0089\u008a\u008b\u008c\u008d\u008e\u008f\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097\u0098\u0099\u009a\u009b\u009c\u009d\u009e\u009f ¡¢£¤¥¦§¨©ª«¬\u00ad®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ";
      public boolean kerning = true;
      public PixmapPacker packer = null;
      public boolean flip = false;
      public boolean genMipMaps = false;
      public Texture.TextureFilter minFilter = Texture.TextureFilter.Nearest;
      public Texture.TextureFilter magFilter = Texture.TextureFilter.Nearest;
      public boolean incremental;
   }

   public class GlyphAndBitmap {
      public BitmapFont.Glyph glyph;
      public FreeType.Bitmap bitmap;
   }

   public static enum Hinting {
      None,
      Slight,
      Medium,
      Full,
      AutoSlight,
      AutoMedium,
      AutoFull;
   }
}
