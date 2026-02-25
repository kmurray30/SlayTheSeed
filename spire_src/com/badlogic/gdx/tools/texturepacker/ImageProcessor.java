package com.badlogic.gdx.tools.texturepacker;

import com.badlogic.gdx.utils.Array;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public class ImageProcessor {
   private static final BufferedImage emptyImage = new BufferedImage(1, 1, 6);
   private static Pattern indexPattern = Pattern.compile("(.+)_(\\d+)$");
   private String rootPath;
   private final TexturePacker.Settings settings;
   private final HashMap<String, TexturePacker.Rect> crcs = new HashMap<>();
   private final Array<TexturePacker.Rect> rects = new Array<>();
   private float scale = 1.0F;

   public ImageProcessor(File rootDir, TexturePacker.Settings settings) {
      this.settings = settings;
      if (rootDir != null) {
         this.rootPath = rootDir.getAbsolutePath().replace('\\', '/');
         if (!this.rootPath.endsWith("/")) {
            this.rootPath = this.rootPath + "/";
         }
      }
   }

   public ImageProcessor(TexturePacker.Settings settings) {
      this(null, settings);
   }

   public void addImage(File file) {
      BufferedImage image;
      try {
         image = ImageIO.read(file);
      } catch (IOException var6) {
         throw new RuntimeException("Error reading image: " + file, var6);
      }

      if (image == null) {
         throw new RuntimeException("Unable to read image: " + file);
      } else {
         String name = file.getAbsolutePath().replace('\\', '/');
         if (this.rootPath != null) {
            if (!name.startsWith(this.rootPath)) {
               throw new RuntimeException("Path '" + name + "' does not start with root: " + this.rootPath);
            }

            name = name.substring(this.rootPath.length());
         }

         int dotIndex = name.lastIndexOf(46);
         if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
         }

         TexturePacker.Rect rect = this.addImage(image, name);
         if (rect != null && this.settings.limitMemory) {
            rect.unloadImage(file);
         }
      }
   }

   public TexturePacker.Rect addImage(BufferedImage image, String name) {
      TexturePacker.Rect rect = this.processImage(image, name);
      if (rect == null) {
         if (!this.settings.silent) {
            System.out.println("Ignoring blank input image: " + name);
         }

         return null;
      } else {
         if (this.settings.alias) {
            String crc = hash(rect.getImage(this));
            TexturePacker.Rect existing = this.crcs.get(crc);
            if (existing != null) {
               if (!this.settings.silent) {
                  System.out.println(rect.name + " (alias of " + existing.name + ")");
               }

               existing.aliases.add(new TexturePacker.Alias(rect));
               return null;
            }

            this.crcs.put(crc, rect);
         }

         this.rects.add(rect);
         return rect;
      }
   }

   public void setScale(float scale) {
      this.scale = scale;
   }

   public Array<TexturePacker.Rect> getImages() {
      return this.rects;
   }

   public void clear() {
      this.rects.clear();
      this.crcs.clear();
   }

   TexturePacker.Rect processImage(BufferedImage image, String name) {
      if (this.scale <= 0.0F) {
         throw new IllegalArgumentException("scale cannot be <= 0: " + this.scale);
      } else {
         int width = image.getWidth();
         int height = image.getHeight();
         if (image.getType() != 6) {
            BufferedImage newImage = new BufferedImage(width, height, 6);
            newImage.getGraphics().drawImage(image, 0, 0, null);
            image = newImage;
         }

         boolean isPatch = name.endsWith(".9");
         int[] splits = null;
         int[] pads = null;
         TexturePacker.Rect rect = null;
         if (isPatch) {
            name = name.substring(0, name.length() - 2);
            splits = this.getSplits(image, name);
            pads = this.getPads(image, name, splits);
            width -= 2;
            height -= 2;
            BufferedImage newImage = new BufferedImage(width, height, 6);
            newImage.getGraphics().drawImage(image, 0, 0, width, height, 1, 1, width + 1, height + 1, null);
            image = newImage;
         }

         if (this.scale != 1.0F) {
            width = Math.max(1, Math.round(width * this.scale));
            height = Math.max(1, Math.round(height * this.scale));
            BufferedImage newImage = new BufferedImage(width, height, 6);
            if (this.scale < 1.0F) {
               newImage.getGraphics().drawImage(image.getScaledInstance(width, height, 16), 0, 0, null);
            } else {
               Graphics2D g = (Graphics2D)newImage.getGraphics();
               g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
               g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
               g.drawImage(image, 0, 0, width, height, null);
            }

            image = newImage;
         }

         if (isPatch) {
            rect = new TexturePacker.Rect(image, 0, 0, width, height, true);
            rect.splits = splits;
            rect.pads = pads;
            rect.canRotate = false;
         } else {
            rect = this.stripWhitespace(image);
            if (rect == null) {
               return null;
            }
         }

         int index = -1;
         if (this.settings.useIndexes) {
            Matcher matcher = indexPattern.matcher(name);
            if (matcher.matches()) {
               name = matcher.group(1);
               index = Integer.parseInt(matcher.group(2));
            }
         }

         rect.name = name;
         rect.index = index;
         return rect;
      }
   }

   private TexturePacker.Rect stripWhitespace(BufferedImage source) {
      WritableRaster alphaRaster = source.getAlphaRaster();
      if (alphaRaster != null && (this.settings.stripWhitespaceX || this.settings.stripWhitespaceY)) {
         byte[] a = new byte[1];
         int top = 0;
         int bottom = source.getHeight();
         if (this.settings.stripWhitespaceX) {
            label122:
            for (int y = 0; y < source.getHeight(); y++) {
               for (int x = 0; x < source.getWidth(); x++) {
                  alphaRaster.getDataElements(x, y, a);
                  int alpha = a[0];
                  if (alpha < 0) {
                     alpha += 256;
                  }

                  if (alpha > this.settings.alphaThreshold) {
                     break label122;
                  }
               }

               top++;
            }

            label108:
            for (int y = source.getHeight(); --y >= top; bottom--) {
               for (int x = 0; x < source.getWidth(); x++) {
                  alphaRaster.getDataElements(x, y, a);
                  int alphax = a[0];
                  if (alphax < 0) {
                     alphax += 256;
                  }

                  if (alphax > this.settings.alphaThreshold) {
                     break label108;
                  }
               }
            }
         }

         int left = 0;
         int right = source.getWidth();
         if (this.settings.stripWhitespaceY) {
            label91:
            for (int x = 0; x < source.getWidth(); x++) {
               for (int y = top; y < bottom; y++) {
                  alphaRaster.getDataElements(x, y, a);
                  int alphaxx = a[0];
                  if (alphaxx < 0) {
                     alphaxx += 256;
                  }

                  if (alphaxx > this.settings.alphaThreshold) {
                     break label91;
                  }
               }

               left++;
            }

            label77:
            for (int x = source.getWidth(); --x >= left; right--) {
               for (int y = top; y < bottom; y++) {
                  alphaRaster.getDataElements(x, y, a);
                  int alphaxxx = a[0];
                  if (alphaxxx < 0) {
                     alphaxxx += 256;
                  }

                  if (alphaxxx > this.settings.alphaThreshold) {
                     break label77;
                  }
               }
            }
         }

         int newWidth = right - left;
         int newHeight = bottom - top;
         if (newWidth > 0 && newHeight > 0) {
            return new TexturePacker.Rect(source, left, top, newWidth, newHeight, false);
         } else {
            return this.settings.ignoreBlankImages ? null : new TexturePacker.Rect(emptyImage, 0, 0, 1, 1, false);
         }
      } else {
         return new TexturePacker.Rect(source, 0, 0, source.getWidth(), source.getHeight(), false);
      }
   }

   private static String splitError(int x, int y, int[] rgba, String name) {
      throw new RuntimeException(
         "Invalid " + name + " ninepatch split pixel at " + x + ", " + y + ", rgba: " + rgba[0] + ", " + rgba[1] + ", " + rgba[2] + ", " + rgba[3]
      );
   }

   private int[] getSplits(BufferedImage image, String name) {
      WritableRaster raster = image.getRaster();
      int startX = getSplitPoint(raster, name, 1, 0, true, true);
      int endX = getSplitPoint(raster, name, startX, 0, false, true);
      int startY = getSplitPoint(raster, name, 0, 1, true, false);
      int endY = getSplitPoint(raster, name, 0, startY, false, false);
      getSplitPoint(raster, name, endX + 1, 0, true, true);
      getSplitPoint(raster, name, 0, endY + 1, true, false);
      if (startX == 0 && endX == 0 && startY == 0 && endY == 0) {
         return null;
      } else {
         if (startX != 0) {
            startX--;
            endX = raster.getWidth() - 2 - (endX - 1);
         } else {
            endX = raster.getWidth() - 2;
         }

         if (startY != 0) {
            startY--;
            endY = raster.getHeight() - 2 - (endY - 1);
         } else {
            endY = raster.getHeight() - 2;
         }

         if (this.scale != 1.0F) {
            startX = Math.round(startX * this.scale);
            endX = Math.round(endX * this.scale);
            startY = Math.round(startY * this.scale);
            endY = Math.round(endY * this.scale);
         }

         return new int[]{startX, endX, startY, endY};
      }
   }

   private int[] getPads(BufferedImage image, String name, int[] splits) {
      WritableRaster raster = image.getRaster();
      int bottom = raster.getHeight() - 1;
      int right = raster.getWidth() - 1;
      int startX = getSplitPoint(raster, name, 1, bottom, true, true);
      int startY = getSplitPoint(raster, name, right, 1, true, false);
      int endX = 0;
      int endY = 0;
      if (startX != 0) {
         endX = getSplitPoint(raster, name, startX + 1, bottom, false, true);
      }

      if (startY != 0) {
         endY = getSplitPoint(raster, name, right, startY + 1, false, false);
      }

      getSplitPoint(raster, name, endX + 1, bottom, true, true);
      getSplitPoint(raster, name, right, endY + 1, true, false);
      if (startX == 0 && endX == 0 && startY == 0 && endY == 0) {
         return null;
      } else {
         if (startX == 0 && endX == 0) {
            startX = -1;
            endX = -1;
         } else if (startX > 0) {
            startX--;
            endX = raster.getWidth() - 2 - (endX - 1);
         } else {
            endX = raster.getWidth() - 2;
         }

         if (startY == 0 && endY == 0) {
            startY = -1;
            endY = -1;
         } else if (startY > 0) {
            startY--;
            endY = raster.getHeight() - 2 - (endY - 1);
         } else {
            endY = raster.getHeight() - 2;
         }

         if (this.scale != 1.0F) {
            startX = Math.round(startX * this.scale);
            endX = Math.round(endX * this.scale);
            startY = Math.round(startY * this.scale);
            endY = Math.round(endY * this.scale);
         }

         int[] pads = new int[]{startX, endX, startY, endY};
         return splits != null && Arrays.equals(pads, splits) ? null : pads;
      }
   }

   private static int getSplitPoint(WritableRaster raster, String name, int startX, int startY, boolean startPoint, boolean xAxis) {
      int[] rgba = new int[4];
      int next = xAxis ? startX : startY;
      int end = xAxis ? raster.getWidth() : raster.getHeight();
      int breakA = startPoint ? 255 : 0;
      int x = startX;

      for (int y = startY; next != end; next++) {
         if (xAxis) {
            x = next;
         } else {
            y = next;
         }

         raster.getPixel(x, y, rgba);
         if (rgba[3] == breakA) {
            return next;
         }

         if (!startPoint && (rgba[0] != 0 || rgba[1] != 0 || rgba[2] != 0 || rgba[3] != 255)) {
            splitError(x, y, rgba, name);
         }
      }

      return 0;
   }

   private static String hash(BufferedImage image) {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA1");
         int width = image.getWidth();
         int height = image.getHeight();
         if (image.getType() != 2) {
            BufferedImage newImage = new BufferedImage(width, height, 2);
            newImage.getGraphics().drawImage(image, 0, 0, null);
            image = newImage;
         }

         WritableRaster raster = image.getRaster();
         int[] pixels = new int[width];

         for (int y = 0; y < height; y++) {
            raster.getDataElements(0, y, width, 1, pixels);

            for (int x = 0; x < width; x++) {
               hash(digest, pixels[x]);
            }
         }

         hash(digest, width);
         hash(digest, height);
         return new BigInteger(1, digest.digest()).toString(16);
      } catch (NoSuchAlgorithmException var8) {
         throw new RuntimeException(var8);
      }
   }

   private static void hash(MessageDigest digest, int value) {
      digest.update((byte)(value >> 24));
      digest.update((byte)(value >> 16));
      digest.update((byte)(value >> 8));
      digest.update((byte)value);
   }
}
