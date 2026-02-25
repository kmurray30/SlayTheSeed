package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.StreamUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;

public class TextureAtlas implements Disposable {
   static final String[] tuple = new String[4];
   private final ObjectSet<Texture> textures = new ObjectSet<>(4);
   private final Array<TextureAtlas.AtlasRegion> regions = new Array<>();
   static final Comparator<TextureAtlas.TextureAtlasData.Region> indexComparator = new Comparator<TextureAtlas.TextureAtlasData.Region>() {
      public int compare(TextureAtlas.TextureAtlasData.Region region1, TextureAtlas.TextureAtlasData.Region region2) {
         int i1 = region1.index;
         if (i1 == -1) {
            i1 = Integer.MAX_VALUE;
         }

         int i2 = region2.index;
         if (i2 == -1) {
            i2 = Integer.MAX_VALUE;
         }

         return i1 - i2;
      }
   };

   public TextureAtlas() {
   }

   public TextureAtlas(String internalPackFile) {
      this(Gdx.files.internal(internalPackFile));
   }

   public TextureAtlas(FileHandle packFile) {
      this(packFile, packFile.parent());
   }

   public TextureAtlas(FileHandle packFile, boolean flip) {
      this(packFile, packFile.parent(), flip);
   }

   public TextureAtlas(FileHandle packFile, FileHandle imagesDir) {
      this(packFile, imagesDir, false);
   }

   public TextureAtlas(FileHandle packFile, FileHandle imagesDir, boolean flip) {
      this(new TextureAtlas.TextureAtlasData(packFile, imagesDir, flip));
   }

   public TextureAtlas(TextureAtlas.TextureAtlasData data) {
      if (data != null) {
         this.load(data);
      }
   }

   private void load(TextureAtlas.TextureAtlasData data) {
      ObjectMap<TextureAtlas.TextureAtlasData.Page, Texture> pageToTexture = new ObjectMap<>();

      for (TextureAtlas.TextureAtlasData.Page page : data.pages) {
         Texture texture = null;
         if (page.texture == null) {
            texture = new Texture(page.textureFile, page.format, page.useMipMaps);
            texture.setFilter(page.minFilter, page.magFilter);
            texture.setWrap(page.uWrap, page.vWrap);
         } else {
            texture = page.texture;
            texture.setFilter(page.minFilter, page.magFilter);
            texture.setWrap(page.uWrap, page.vWrap);
         }

         this.textures.add(texture);
         pageToTexture.put(page, texture);
      }

      for (TextureAtlas.TextureAtlasData.Region region : data.regions) {
         int width = region.width;
         int height = region.height;
         TextureAtlas.AtlasRegion atlasRegion = new TextureAtlas.AtlasRegion(
            pageToTexture.get(region.page), region.left, region.top, region.rotate ? height : width, region.rotate ? width : height
         );
         atlasRegion.index = region.index;
         atlasRegion.name = region.name;
         atlasRegion.offsetX = region.offsetX;
         atlasRegion.offsetY = region.offsetY;
         atlasRegion.originalHeight = region.originalHeight;
         atlasRegion.originalWidth = region.originalWidth;
         atlasRegion.rotate = region.rotate;
         atlasRegion.splits = region.splits;
         atlasRegion.pads = region.pads;
         if (region.flip) {
            atlasRegion.flip(false, true);
         }

         this.regions.add(atlasRegion);
      }
   }

   public TextureAtlas.AtlasRegion addRegion(String name, Texture texture, int x, int y, int width, int height) {
      this.textures.add(texture);
      TextureAtlas.AtlasRegion region = new TextureAtlas.AtlasRegion(texture, x, y, width, height);
      region.name = name;
      region.originalWidth = width;
      region.originalHeight = height;
      region.index = -1;
      this.regions.add(region);
      return region;
   }

   public TextureAtlas.AtlasRegion addRegion(String name, TextureRegion textureRegion) {
      return this.addRegion(
         name, textureRegion.texture, textureRegion.getRegionX(), textureRegion.getRegionY(), textureRegion.getRegionWidth(), textureRegion.getRegionHeight()
      );
   }

   public Array<TextureAtlas.AtlasRegion> getRegions() {
      return this.regions;
   }

   public TextureAtlas.AtlasRegion findRegion(String name) {
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         if (this.regions.get(i).name.equals(name)) {
            return this.regions.get(i);
         }
      }

      return null;
   }

   public TextureAtlas.AtlasRegion findRegion(String name, int index) {
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         TextureAtlas.AtlasRegion region = this.regions.get(i);
         if (region.name.equals(name) && region.index == index) {
            return region;
         }
      }

      return null;
   }

   public Array<TextureAtlas.AtlasRegion> findRegions(String name) {
      Array<TextureAtlas.AtlasRegion> matched = new Array<>();
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         TextureAtlas.AtlasRegion region = this.regions.get(i);
         if (region.name.equals(name)) {
            matched.add(new TextureAtlas.AtlasRegion(region));
         }
      }

      return matched;
   }

   public Array<Sprite> createSprites() {
      Array sprites = new Array(this.regions.size);
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         sprites.add(this.newSprite(this.regions.get(i)));
      }

      return sprites;
   }

   public Sprite createSprite(String name) {
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         if (this.regions.get(i).name.equals(name)) {
            return this.newSprite(this.regions.get(i));
         }
      }

      return null;
   }

   public Sprite createSprite(String name, int index) {
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         TextureAtlas.AtlasRegion region = this.regions.get(i);
         if (region.name.equals(name) && region.index == index) {
            return this.newSprite(this.regions.get(i));
         }
      }

      return null;
   }

   public Array<Sprite> createSprites(String name) {
      Array<Sprite> matched = new Array<>();
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         TextureAtlas.AtlasRegion region = this.regions.get(i);
         if (region.name.equals(name)) {
            matched.add(this.newSprite(region));
         }
      }

      return matched;
   }

   private Sprite newSprite(TextureAtlas.AtlasRegion region) {
      if (region.packedWidth != region.originalWidth || region.packedHeight != region.originalHeight) {
         return new TextureAtlas.AtlasSprite(region);
      } else if (region.rotate) {
         Sprite sprite = new Sprite(region);
         sprite.setBounds(0.0F, 0.0F, region.getRegionHeight(), region.getRegionWidth());
         sprite.rotate90(true);
         return sprite;
      } else {
         return new Sprite(region);
      }
   }

   public NinePatch createPatch(String name) {
      int i = 0;

      for (int n = this.regions.size; i < n; i++) {
         TextureAtlas.AtlasRegion region = this.regions.get(i);
         if (region.name.equals(name)) {
            int[] splits = region.splits;
            if (splits == null) {
               throw new IllegalArgumentException("Region does not have ninepatch splits: " + name);
            }

            NinePatch patch = new NinePatch(region, splits[0], splits[1], splits[2], splits[3]);
            if (region.pads != null) {
               patch.setPadding(region.pads[0], region.pads[1], region.pads[2], region.pads[3]);
            }

            return patch;
         }
      }

      return null;
   }

   public ObjectSet<Texture> getTextures() {
      return this.textures;
   }

   @Override
   public void dispose() {
      for (Texture texture : this.textures) {
         texture.dispose();
      }

      this.textures.clear();
   }

   static String readValue(BufferedReader reader) throws IOException {
      String line = reader.readLine();
      int colon = line.indexOf(58);
      if (colon == -1) {
         throw new GdxRuntimeException("Invalid line: " + line);
      } else {
         return line.substring(colon + 1).trim();
      }
   }

   static int readTuple(BufferedReader reader) throws IOException {
      String line = reader.readLine();
      int colon = line.indexOf(58);
      if (colon == -1) {
         throw new GdxRuntimeException("Invalid line: " + line);
      } else {
         int i = 0;
         int lastMatch = colon + 1;

         for (i = 0; i < 3; i++) {
            int comma = line.indexOf(44, lastMatch);
            if (comma == -1) {
               break;
            }

            tuple[i] = line.substring(lastMatch, comma).trim();
            lastMatch = comma + 1;
         }

         tuple[i] = line.substring(lastMatch).trim();
         return i + 1;
      }
   }

   public static class AtlasRegion extends TextureRegion {
      public int index;
      public String name;
      public float offsetX;
      public float offsetY;
      public int packedWidth;
      public int packedHeight;
      public int originalWidth;
      public int originalHeight;
      public boolean rotate;
      public int[] splits;
      public int[] pads;

      public AtlasRegion(Texture texture, int x, int y, int width, int height) {
         super(texture, x, y, width, height);
         this.originalWidth = width;
         this.originalHeight = height;
         this.packedWidth = width;
         this.packedHeight = height;
      }

      public AtlasRegion(TextureAtlas.AtlasRegion region) {
         this.setRegion(region);
         this.index = region.index;
         this.name = region.name;
         this.offsetX = region.offsetX;
         this.offsetY = region.offsetY;
         this.packedWidth = region.packedWidth;
         this.packedHeight = region.packedHeight;
         this.originalWidth = region.originalWidth;
         this.originalHeight = region.originalHeight;
         this.rotate = region.rotate;
         this.splits = region.splits;
      }

      @Override
      public void flip(boolean x, boolean y) {
         super.flip(x, y);
         if (x) {
            this.offsetX = this.originalWidth - this.offsetX - this.getRotatedPackedWidth();
         }

         if (y) {
            this.offsetY = this.originalHeight - this.offsetY - this.getRotatedPackedHeight();
         }
      }

      public float getRotatedPackedWidth() {
         return this.rotate ? this.packedHeight : this.packedWidth;
      }

      public float getRotatedPackedHeight() {
         return this.rotate ? this.packedWidth : this.packedHeight;
      }

      @Override
      public String toString() {
         return this.name;
      }
   }

   public static class AtlasSprite extends Sprite {
      final TextureAtlas.AtlasRegion region;
      float originalOffsetX;
      float originalOffsetY;

      public AtlasSprite(TextureAtlas.AtlasRegion region) {
         this.region = new TextureAtlas.AtlasRegion(region);
         this.originalOffsetX = region.offsetX;
         this.originalOffsetY = region.offsetY;
         this.setRegion(region);
         this.setOrigin(region.originalWidth / 2.0F, region.originalHeight / 2.0F);
         int width = region.getRegionWidth();
         int height = region.getRegionHeight();
         if (region.rotate) {
            super.rotate90(true);
            super.setBounds(region.offsetX, region.offsetY, height, width);
         } else {
            super.setBounds(region.offsetX, region.offsetY, width, height);
         }

         this.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

      public AtlasSprite(TextureAtlas.AtlasSprite sprite) {
         this.region = sprite.region;
         this.originalOffsetX = sprite.originalOffsetX;
         this.originalOffsetY = sprite.originalOffsetY;
         this.set(sprite);
      }

      @Override
      public void setPosition(float x, float y) {
         super.setPosition(x + this.region.offsetX, y + this.region.offsetY);
      }

      @Override
      public void setX(float x) {
         super.setX(x + this.region.offsetX);
      }

      @Override
      public void setY(float y) {
         super.setY(y + this.region.offsetY);
      }

      @Override
      public void setBounds(float x, float y, float width, float height) {
         float widthRatio = width / this.region.originalWidth;
         float heightRatio = height / this.region.originalHeight;
         this.region.offsetX = this.originalOffsetX * widthRatio;
         this.region.offsetY = this.originalOffsetY * heightRatio;
         int packedWidth = this.region.rotate ? this.region.packedHeight : this.region.packedWidth;
         int packedHeight = this.region.rotate ? this.region.packedWidth : this.region.packedHeight;
         super.setBounds(x + this.region.offsetX, y + this.region.offsetY, packedWidth * widthRatio, packedHeight * heightRatio);
      }

      @Override
      public void setSize(float width, float height) {
         this.setBounds(this.getX(), this.getY(), width, height);
      }

      @Override
      public void setOrigin(float originX, float originY) {
         super.setOrigin(originX - this.region.offsetX, originY - this.region.offsetY);
      }

      @Override
      public void setOriginCenter() {
         super.setOrigin(this.width / 2.0F - this.region.offsetX, this.height / 2.0F - this.region.offsetY);
      }

      @Override
      public void flip(boolean x, boolean y) {
         if (this.region.rotate) {
            super.flip(y, x);
         } else {
            super.flip(x, y);
         }

         float oldOriginX = this.getOriginX();
         float oldOriginY = this.getOriginY();
         float oldOffsetX = this.region.offsetX;
         float oldOffsetY = this.region.offsetY;
         float widthRatio = this.getWidthRatio();
         float heightRatio = this.getHeightRatio();
         this.region.offsetX = this.originalOffsetX;
         this.region.offsetY = this.originalOffsetY;
         this.region.flip(x, y);
         this.originalOffsetX = this.region.offsetX;
         this.originalOffsetY = this.region.offsetY;
         this.region.offsetX *= widthRatio;
         this.region.offsetY *= heightRatio;
         this.translate(this.region.offsetX - oldOffsetX, this.region.offsetY - oldOffsetY);
         this.setOrigin(oldOriginX, oldOriginY);
      }

      @Override
      public void rotate90(boolean clockwise) {
         super.rotate90(clockwise);
         float oldOriginX = this.getOriginX();
         float oldOriginY = this.getOriginY();
         float oldOffsetX = this.region.offsetX;
         float oldOffsetY = this.region.offsetY;
         float widthRatio = this.getWidthRatio();
         float heightRatio = this.getHeightRatio();
         if (clockwise) {
            this.region.offsetX = oldOffsetY;
            this.region.offsetY = this.region.originalHeight * heightRatio - oldOffsetX - this.region.packedWidth * widthRatio;
         } else {
            this.region.offsetX = this.region.originalWidth * widthRatio - oldOffsetY - this.region.packedHeight * heightRatio;
            this.region.offsetY = oldOffsetX;
         }

         this.translate(this.region.offsetX - oldOffsetX, this.region.offsetY - oldOffsetY);
         this.setOrigin(oldOriginX, oldOriginY);
      }

      @Override
      public float getX() {
         return super.getX() - this.region.offsetX;
      }

      @Override
      public float getY() {
         return super.getY() - this.region.offsetY;
      }

      @Override
      public float getOriginX() {
         return super.getOriginX() + this.region.offsetX;
      }

      @Override
      public float getOriginY() {
         return super.getOriginY() + this.region.offsetY;
      }

      @Override
      public float getWidth() {
         return super.getWidth() / this.region.getRotatedPackedWidth() * this.region.originalWidth;
      }

      @Override
      public float getHeight() {
         return super.getHeight() / this.region.getRotatedPackedHeight() * this.region.originalHeight;
      }

      public float getWidthRatio() {
         return super.getWidth() / this.region.getRotatedPackedWidth();
      }

      public float getHeightRatio() {
         return super.getHeight() / this.region.getRotatedPackedHeight();
      }

      public TextureAtlas.AtlasRegion getAtlasRegion() {
         return this.region;
      }

      @Override
      public String toString() {
         return this.region.toString();
      }
   }

   public static class TextureAtlasData {
      final Array<TextureAtlas.TextureAtlasData.Page> pages = new Array<>();
      final Array<TextureAtlas.TextureAtlasData.Region> regions = new Array<>();

      public TextureAtlasData(FileHandle packFile, FileHandle imagesDir, boolean flip) {
         BufferedReader reader = new BufferedReader(new InputStreamReader(packFile.read()), 64);

         try {
            TextureAtlas.TextureAtlasData.Page pageImage = null;

            while (true) {
               String line = reader.readLine();
               if (line == null) {
                  break;
               }

               if (line.trim().length() == 0) {
                  pageImage = null;
               } else if (pageImage == null) {
                  FileHandle file = imagesDir.child(line);
                  float width = 0.0F;
                  float height = 0.0F;
                  if (TextureAtlas.readTuple(reader) == 2) {
                     width = Integer.parseInt(TextureAtlas.tuple[0]);
                     height = Integer.parseInt(TextureAtlas.tuple[1]);
                     TextureAtlas.readTuple(reader);
                  }

                  Pixmap.Format format = Pixmap.Format.valueOf(TextureAtlas.tuple[0]);
                  TextureAtlas.readTuple(reader);
                  Texture.TextureFilter min = Texture.TextureFilter.valueOf(TextureAtlas.tuple[0]);
                  Texture.TextureFilter max = Texture.TextureFilter.valueOf(TextureAtlas.tuple[1]);
                  String direction = TextureAtlas.readValue(reader);
                  Texture.TextureWrap repeatX = Texture.TextureWrap.ClampToEdge;
                  Texture.TextureWrap repeatY = Texture.TextureWrap.ClampToEdge;
                  if (direction.equals("x")) {
                     repeatX = Texture.TextureWrap.Repeat;
                  } else if (direction.equals("y")) {
                     repeatY = Texture.TextureWrap.Repeat;
                  } else if (direction.equals("xy")) {
                     repeatX = Texture.TextureWrap.Repeat;
                     repeatY = Texture.TextureWrap.Repeat;
                  }

                  pageImage = new TextureAtlas.TextureAtlasData.Page(file, width, height, min.isMipMap(), format, min, max, repeatX, repeatY);
                  this.pages.add(pageImage);
               } else {
                  boolean rotate = Boolean.valueOf(TextureAtlas.readValue(reader));
                  TextureAtlas.readTuple(reader);
                  int left = Integer.parseInt(TextureAtlas.tuple[0]);
                  int top = Integer.parseInt(TextureAtlas.tuple[1]);
                  TextureAtlas.readTuple(reader);
                  int widthx = Integer.parseInt(TextureAtlas.tuple[0]);
                  int heightx = Integer.parseInt(TextureAtlas.tuple[1]);
                  TextureAtlas.TextureAtlasData.Region region = new TextureAtlas.TextureAtlasData.Region();
                  region.page = pageImage;
                  region.left = left;
                  region.top = top;
                  region.width = widthx;
                  region.height = heightx;
                  region.name = line;
                  region.rotate = rotate;
                  if (TextureAtlas.readTuple(reader) == 4) {
                     region.splits = new int[]{
                        Integer.parseInt(TextureAtlas.tuple[0]),
                        Integer.parseInt(TextureAtlas.tuple[1]),
                        Integer.parseInt(TextureAtlas.tuple[2]),
                        Integer.parseInt(TextureAtlas.tuple[3])
                     };
                     if (TextureAtlas.readTuple(reader) == 4) {
                        region.pads = new int[]{
                           Integer.parseInt(TextureAtlas.tuple[0]),
                           Integer.parseInt(TextureAtlas.tuple[1]),
                           Integer.parseInt(TextureAtlas.tuple[2]),
                           Integer.parseInt(TextureAtlas.tuple[3])
                        };
                        TextureAtlas.readTuple(reader);
                     }
                  }

                  region.originalWidth = Integer.parseInt(TextureAtlas.tuple[0]);
                  region.originalHeight = Integer.parseInt(TextureAtlas.tuple[1]);
                  TextureAtlas.readTuple(reader);
                  region.offsetX = Integer.parseInt(TextureAtlas.tuple[0]);
                  region.offsetY = Integer.parseInt(TextureAtlas.tuple[1]);
                  region.index = Integer.parseInt(TextureAtlas.readValue(reader));
                  if (flip) {
                     region.flip = true;
                  }

                  this.regions.add(region);
               }
            }
         } catch (Exception var19) {
            throw new GdxRuntimeException("Error reading pack file: " + packFile, var19);
         } finally {
            StreamUtils.closeQuietly(reader);
         }

         this.regions.sort(TextureAtlas.indexComparator);
      }

      public Array<TextureAtlas.TextureAtlasData.Page> getPages() {
         return this.pages;
      }

      public Array<TextureAtlas.TextureAtlasData.Region> getRegions() {
         return this.regions;
      }

      public static class Page {
         public final FileHandle textureFile;
         public Texture texture;
         public final float width;
         public final float height;
         public final boolean useMipMaps;
         public final Pixmap.Format format;
         public final Texture.TextureFilter minFilter;
         public final Texture.TextureFilter magFilter;
         public final Texture.TextureWrap uWrap;
         public final Texture.TextureWrap vWrap;

         public Page(
            FileHandle handle,
            float width,
            float height,
            boolean useMipMaps,
            Pixmap.Format format,
            Texture.TextureFilter minFilter,
            Texture.TextureFilter magFilter,
            Texture.TextureWrap uWrap,
            Texture.TextureWrap vWrap
         ) {
            this.width = width;
            this.height = height;
            this.textureFile = handle;
            this.useMipMaps = useMipMaps;
            this.format = format;
            this.minFilter = minFilter;
            this.magFilter = magFilter;
            this.uWrap = uWrap;
            this.vWrap = vWrap;
         }
      }

      public static class Region {
         public TextureAtlas.TextureAtlasData.Page page;
         public int index;
         public String name;
         public float offsetX;
         public float offsetY;
         public int originalWidth;
         public int originalHeight;
         public boolean rotate;
         public int left;
         public int top;
         public int width;
         public int height;
         public boolean flip;
         public int[] splits;
         public int[] pads;
      }
   }
}
