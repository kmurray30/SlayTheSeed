package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.OrderedMap;
import java.util.Comparator;

public class PixmapPacker implements Disposable {
   boolean packToTexture;
   boolean disposed;
   int pageWidth;
   int pageHeight;
   Pixmap.Format pageFormat;
   int padding;
   boolean duplicateBorder;
   Color transparentColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
   final Array<PixmapPacker.Page> pages = new Array<>();
   PixmapPacker.PackStrategy packStrategy;

   public PixmapPacker(int pageWidth, int pageHeight, Pixmap.Format pageFormat, int padding, boolean duplicateBorder) {
      this(pageWidth, pageHeight, pageFormat, padding, duplicateBorder, new PixmapPacker.GuillotineStrategy());
   }

   public PixmapPacker(int pageWidth, int pageHeight, Pixmap.Format pageFormat, int padding, boolean duplicateBorder, PixmapPacker.PackStrategy packStrategy) {
      this.pageWidth = pageWidth;
      this.pageHeight = pageHeight;
      this.pageFormat = pageFormat;
      this.padding = padding;
      this.duplicateBorder = duplicateBorder;
      this.packStrategy = packStrategy;
   }

   public void sort(Array<Pixmap> images) {
      this.packStrategy.sort(images);
   }

   public synchronized Rectangle pack(Pixmap image) {
      return this.pack(null, image);
   }

   public synchronized Rectangle pack(String name, Pixmap image) {
      if (this.disposed) {
         return null;
      } else if (name != null && this.getRect(name) != null) {
         throw new GdxRuntimeException("Pixmap has already been packed with name: " + name);
      } else {
         Rectangle rect = new Rectangle(0.0F, 0.0F, image.getWidth(), image.getHeight());
         if (!(rect.getWidth() > this.pageWidth) && !(rect.getHeight() > this.pageHeight)) {
            PixmapPacker.Page page = this.packStrategy.pack(this, name, rect);
            if (name != null) {
               page.rects.put(name, rect);
               page.addedRects.add(name);
            }

            int rectX = (int)rect.x;
            int rectY = (int)rect.y;
            int rectWidth = (int)rect.width;
            int rectHeight = (int)rect.height;
            if (this.packToTexture && !this.duplicateBorder && page.texture != null && !page.dirty) {
               page.texture.bind();
               Gdx.gl.glTexSubImage2D(page.texture.glTarget, 0, rectX, rectY, rectWidth, rectHeight, image.getGLFormat(), image.getGLType(), image.getPixels());
            } else {
               page.dirty = true;
            }

            Pixmap.Blending blending = Pixmap.getBlending();
            Pixmap.setBlending(Pixmap.Blending.None);
            page.image.drawPixmap(image, rectX, rectY);
            if (this.duplicateBorder) {
               int imageWidth = image.getWidth();
               int imageHeight = image.getHeight();
               page.image.drawPixmap(image, 0, 0, 1, 1, rectX - 1, rectY - 1, 1, 1);
               page.image.drawPixmap(image, imageWidth - 1, 0, 1, 1, rectX + rectWidth, rectY - 1, 1, 1);
               page.image.drawPixmap(image, 0, imageHeight - 1, 1, 1, rectX - 1, rectY + rectHeight, 1, 1);
               page.image.drawPixmap(image, imageWidth - 1, imageHeight - 1, 1, 1, rectX + rectWidth, rectY + rectHeight, 1, 1);
               page.image.drawPixmap(image, 0, 0, imageWidth, 1, rectX, rectY - 1, rectWidth, 1);
               page.image.drawPixmap(image, 0, imageHeight - 1, imageWidth, 1, rectX, rectY + rectHeight, rectWidth, 1);
               page.image.drawPixmap(image, 0, 0, 1, imageHeight, rectX - 1, rectY, 1, rectHeight);
               page.image.drawPixmap(image, imageWidth - 1, 0, 1, imageHeight, rectX + rectWidth, rectY, 1, rectHeight);
            }

            Pixmap.setBlending(blending);
            return rect;
         } else if (name == null) {
            throw new GdxRuntimeException("Page size too small for pixmap.");
         } else {
            throw new GdxRuntimeException("Page size too small for pixmap: " + name);
         }
      }
   }

   public Array<PixmapPacker.Page> getPages() {
      return this.pages;
   }

   public synchronized Rectangle getRect(String name) {
      for (PixmapPacker.Page page : this.pages) {
         Rectangle rect = page.rects.get(name);
         if (rect != null) {
            return rect;
         }
      }

      return null;
   }

   public synchronized PixmapPacker.Page getPage(String name) {
      for (PixmapPacker.Page page : this.pages) {
         Rectangle rect = page.rects.get(name);
         if (rect != null) {
            return page;
         }
      }

      return null;
   }

   public synchronized int getPageIndex(String name) {
      for (int i = 0; i < this.pages.size; i++) {
         Rectangle rect = this.pages.get(i).rects.get(name);
         if (rect != null) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public synchronized void dispose() {
      for (PixmapPacker.Page page : this.pages) {
         if (page.texture == null) {
            page.image.dispose();
         }
      }

      this.disposed = true;
   }

   public synchronized TextureAtlas generateTextureAtlas(Texture.TextureFilter minFilter, Texture.TextureFilter magFilter, boolean useMipMaps) {
      TextureAtlas atlas = new TextureAtlas();
      this.updateTextureAtlas(atlas, minFilter, magFilter, useMipMaps);
      return atlas;
   }

   public synchronized void updateTextureAtlas(TextureAtlas atlas, Texture.TextureFilter minFilter, Texture.TextureFilter magFilter, boolean useMipMaps) {
      this.updatePageTextures(minFilter, magFilter, useMipMaps);

      for (PixmapPacker.Page page : this.pages) {
         if (page.addedRects.size > 0) {
            for (String name : page.addedRects) {
               Rectangle rect = page.rects.get(name);
               TextureRegion region = new TextureRegion(page.texture, (int)rect.x, (int)rect.y, (int)rect.width, (int)rect.height);
               atlas.addRegion(name, region);
            }

            page.addedRects.clear();
            atlas.getTextures().add(page.texture);
         }
      }
   }

   public synchronized void updateTextureRegions(
      Array<TextureRegion> regions, Texture.TextureFilter minFilter, Texture.TextureFilter magFilter, boolean useMipMaps
   ) {
      this.updatePageTextures(minFilter, magFilter, useMipMaps);

      while (regions.size < this.pages.size) {
         regions.add(new TextureRegion(this.pages.get(regions.size).texture));
      }
   }

   public synchronized void updatePageTextures(Texture.TextureFilter minFilter, Texture.TextureFilter magFilter, boolean useMipMaps) {
      for (PixmapPacker.Page page : this.pages) {
         page.updateTexture(minFilter, magFilter, useMipMaps);
      }
   }

   public int getPageWidth() {
      return this.pageWidth;
   }

   public void setPageWidth(int pageWidth) {
      this.pageWidth = pageWidth;
   }

   public int getPageHeight() {
      return this.pageHeight;
   }

   public void setPageHeight(int pageHeight) {
      this.pageHeight = pageHeight;
   }

   public Pixmap.Format getPageFormat() {
      return this.pageFormat;
   }

   public void setPageFormat(Pixmap.Format pageFormat) {
      this.pageFormat = pageFormat;
   }

   public int getPadding() {
      return this.padding;
   }

   public void setPadding(int padding) {
      this.padding = padding;
   }

   public boolean getDuplicateBorder() {
      return this.duplicateBorder;
   }

   public void setDuplicateBorder(boolean duplicateBorder) {
      this.duplicateBorder = duplicateBorder;
   }

   public boolean getPackToTexture() {
      return this.packToTexture;
   }

   public void setPackToTexture(boolean packToTexture) {
      this.packToTexture = packToTexture;
   }

   public Color getTransparentColor() {
      return this.transparentColor;
   }

   public void setTransparentColor(Color color) {
      this.transparentColor.set(color);
   }

   public static class GuillotineStrategy implements PixmapPacker.PackStrategy {
      Comparator<Pixmap> comparator;

      @Override
      public void sort(Array<Pixmap> pixmaps) {
         if (this.comparator == null) {
            this.comparator = new Comparator<Pixmap>() {
               public int compare(Pixmap o1, Pixmap o2) {
                  return Math.max(o1.getWidth(), o1.getHeight()) - Math.max(o2.getWidth(), o2.getHeight());
               }
            };
         }

         pixmaps.sort(this.comparator);
      }

      @Override
      public PixmapPacker.Page pack(PixmapPacker packer, String name, Rectangle rect) {
         PixmapPacker.GuillotineStrategy.GuillotinePage page;
         if (packer.pages.size == 0) {
            page = new PixmapPacker.GuillotineStrategy.GuillotinePage(packer);
            packer.pages.add(page);
         } else {
            page = (PixmapPacker.GuillotineStrategy.GuillotinePage)packer.pages.peek();
         }

         int padding = packer.padding;
         rect.width += padding;
         rect.height += padding;
         PixmapPacker.GuillotineStrategy.Node node = this.insert(page.root, rect);
         if (node == null) {
            page = new PixmapPacker.GuillotineStrategy.GuillotinePage(packer);
            packer.pages.add(page);
            node = this.insert(page.root, rect);
         }

         node.full = true;
         rect.set(node.rect.x, node.rect.y, node.rect.width - padding, node.rect.height - padding);
         return page;
      }

      private PixmapPacker.GuillotineStrategy.Node insert(PixmapPacker.GuillotineStrategy.Node node, Rectangle rect) {
         if (!node.full && node.leftChild != null && node.rightChild != null) {
            PixmapPacker.GuillotineStrategy.Node newNode = this.insert(node.leftChild, rect);
            if (newNode == null) {
               newNode = this.insert(node.rightChild, rect);
            }

            return newNode;
         } else if (node.full) {
            return null;
         } else if (node.rect.width == rect.width && node.rect.height == rect.height) {
            return node;
         } else if (!(node.rect.width < rect.width) && !(node.rect.height < rect.height)) {
            node.leftChild = new PixmapPacker.GuillotineStrategy.Node();
            node.rightChild = new PixmapPacker.GuillotineStrategy.Node();
            int deltaWidth = (int)node.rect.width - (int)rect.width;
            int deltaHeight = (int)node.rect.height - (int)rect.height;
            if (deltaWidth > deltaHeight) {
               node.leftChild.rect.x = node.rect.x;
               node.leftChild.rect.y = node.rect.y;
               node.leftChild.rect.width = rect.width;
               node.leftChild.rect.height = node.rect.height;
               node.rightChild.rect.x = node.rect.x + rect.width;
               node.rightChild.rect.y = node.rect.y;
               node.rightChild.rect.width = node.rect.width - rect.width;
               node.rightChild.rect.height = node.rect.height;
            } else {
               node.leftChild.rect.x = node.rect.x;
               node.leftChild.rect.y = node.rect.y;
               node.leftChild.rect.width = node.rect.width;
               node.leftChild.rect.height = rect.height;
               node.rightChild.rect.x = node.rect.x;
               node.rightChild.rect.y = node.rect.y + rect.height;
               node.rightChild.rect.width = node.rect.width;
               node.rightChild.rect.height = node.rect.height - rect.height;
            }

            return this.insert(node.leftChild, rect);
         } else {
            return null;
         }
      }

      static class GuillotinePage extends PixmapPacker.Page {
         PixmapPacker.GuillotineStrategy.Node root = new PixmapPacker.GuillotineStrategy.Node();

         public GuillotinePage(PixmapPacker packer) {
            super(packer);
            this.root.rect.x = packer.padding;
            this.root.rect.y = packer.padding;
            this.root.rect.width = packer.pageWidth - packer.padding * 2;
            this.root.rect.height = packer.pageHeight - packer.padding * 2;
         }
      }

      static final class Node {
         public PixmapPacker.GuillotineStrategy.Node leftChild;
         public PixmapPacker.GuillotineStrategy.Node rightChild;
         public final Rectangle rect = new Rectangle();
         public boolean full;
      }
   }

   public interface PackStrategy {
      void sort(Array<Pixmap> var1);

      PixmapPacker.Page pack(PixmapPacker var1, String var2, Rectangle var3);
   }

   public static class Page {
      OrderedMap<String, Rectangle> rects = new OrderedMap<>();
      Pixmap image;
      Texture texture;
      final Array<String> addedRects = new Array<>();
      boolean dirty;

      public Page(PixmapPacker packer) {
         this.image = new Pixmap(packer.pageWidth, packer.pageHeight, packer.pageFormat);
         Color transparentColor = packer.getTransparentColor();
         this.image.setColor(transparentColor);
         this.image.fill();
      }

      public Pixmap getPixmap() {
         return this.image;
      }

      public OrderedMap<String, Rectangle> getRects() {
         return this.rects;
      }

      public Texture getTexture() {
         return this.texture;
      }

      public boolean updateTexture(Texture.TextureFilter minFilter, Texture.TextureFilter magFilter, boolean useMipMaps) {
         if (this.texture != null) {
            if (!this.dirty) {
               return false;
            }

            this.texture.load(this.texture.getTextureData());
         } else {
            this.texture = new Texture(new PixmapTextureData(this.image, this.image.getFormat(), useMipMaps, false, true)) {
               @Override
               public void dispose() {
                  super.dispose();
                  Page.this.image.dispose();
               }
            };
            this.texture.setFilter(minFilter, magFilter);
         }

         this.dirty = false;
         return true;
      }
   }

   public static class SkylineStrategy implements PixmapPacker.PackStrategy {
      Comparator<Pixmap> comparator;

      @Override
      public void sort(Array<Pixmap> images) {
         if (this.comparator == null) {
            this.comparator = new Comparator<Pixmap>() {
               public int compare(Pixmap o1, Pixmap o2) {
                  return o1.getHeight() - o2.getHeight();
               }
            };
         }

         images.sort(this.comparator);
      }

      @Override
      public PixmapPacker.Page pack(PixmapPacker packer, String name, Rectangle rect) {
         int padding = packer.padding;
         int pageWidth = packer.pageWidth - padding * 2;
         int pageHeight = packer.pageHeight - padding * 2;
         int rectWidth = (int)rect.width + padding;
         int rectHeight = (int)rect.height + padding;
         int i = 0;

         for (int n = packer.pages.size; i < n; i++) {
            PixmapPacker.SkylineStrategy.SkylinePage page = (PixmapPacker.SkylineStrategy.SkylinePage)packer.pages.get(i);
            PixmapPacker.SkylineStrategy.SkylinePage.Row bestRow = null;
            int ii = 0;

            for (int nn = page.rows.size - 1; ii < nn; ii++) {
               PixmapPacker.SkylineStrategy.SkylinePage.Row row = page.rows.get(ii);
               if (row.x + rectWidth < pageWidth
                  && row.y + rectHeight < pageHeight
                  && rectHeight <= row.height
                  && (bestRow == null || row.height < bestRow.height)) {
                  bestRow = row;
               }
            }

            if (bestRow == null) {
               PixmapPacker.SkylineStrategy.SkylinePage.Row row = page.rows.peek();
               if (row.y + rectHeight >= pageHeight) {
                  continue;
               }

               if (row.x + rectWidth < pageWidth) {
                  row.height = Math.max(row.height, rectHeight);
                  bestRow = row;
               } else {
                  bestRow = new PixmapPacker.SkylineStrategy.SkylinePage.Row();
                  bestRow.y = row.y + row.height;
                  bestRow.height = rectHeight;
                  page.rows.add(bestRow);
               }
            }

            if (bestRow != null) {
               rect.x = bestRow.x;
               rect.y = bestRow.y;
               bestRow.x += rectWidth;
               return page;
            }
         }

         PixmapPacker.SkylineStrategy.SkylinePage page = new PixmapPacker.SkylineStrategy.SkylinePage(packer);
         packer.pages.add(page);
         PixmapPacker.SkylineStrategy.SkylinePage.Row rowx = new PixmapPacker.SkylineStrategy.SkylinePage.Row();
         rowx.x = padding + rectWidth;
         rowx.y = padding;
         rowx.height = rectHeight;
         page.rows.add(rowx);
         rect.x = padding;
         rect.y = padding;
         return page;
      }

      static class SkylinePage extends PixmapPacker.Page {
         Array<PixmapPacker.SkylineStrategy.SkylinePage.Row> rows = new Array<>();

         public SkylinePage(PixmapPacker packer) {
            super(packer);
         }

         static class Row {
            int x;
            int y;
            int height;
         }
      }
   }
}
