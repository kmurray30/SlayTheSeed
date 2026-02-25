package com.badlogic.gdx.maps.tiled;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.ImageResolver;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import java.io.IOException;

public class TmxMapLoader extends BaseTmxMapLoader<TmxMapLoader.Parameters> {
   public TmxMapLoader() {
      super(new InternalFileHandleResolver());
   }

   public TmxMapLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public TiledMap load(String fileName) {
      return this.load(fileName, new TmxMapLoader.Parameters());
   }

   public TiledMap load(String fileName, TmxMapLoader.Parameters parameters) {
      try {
         this.convertObjectToTileSpace = parameters.convertObjectToTileSpace;
         this.flipY = parameters.flipY;
         FileHandle tmxFile = this.resolve(fileName);
         this.root = this.xml.parse(tmxFile);
         ObjectMap<String, Texture> textures = new ObjectMap<>();
         Array<FileHandle> textureFiles = this.loadTilesets(this.root, tmxFile);
         textureFiles.addAll(this.loadImages(this.root, tmxFile));

         for (FileHandle textureFile : textureFiles) {
            Texture texture = new Texture(textureFile, parameters.generateMipMaps);
            texture.setFilter(parameters.textureMinFilter, parameters.textureMagFilter);
            textures.put(textureFile.path(), texture);
         }

         ImageResolver.DirectImageResolver imageResolver = new ImageResolver.DirectImageResolver(textures);
         TiledMap map = this.loadTilemap(this.root, tmxFile, imageResolver);
         map.setOwnedResources(textures.values().toArray());
         return map;
      } catch (IOException var9) {
         throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", var9);
      }
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle tmxFile, TmxMapLoader.Parameters parameter) {
      this.map = null;
      if (parameter != null) {
         this.convertObjectToTileSpace = parameter.convertObjectToTileSpace;
         this.flipY = parameter.flipY;
      } else {
         this.convertObjectToTileSpace = false;
         this.flipY = true;
      }

      try {
         this.map = this.loadTilemap(this.root, tmxFile, new ImageResolver.AssetManagerImageResolver(manager));
      } catch (Exception var6) {
         throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", var6);
      }
   }

   public TiledMap loadSync(AssetManager manager, String fileName, FileHandle file, TmxMapLoader.Parameters parameter) {
      return this.map;
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle tmxFile, TmxMapLoader.Parameters parameter) {
      Array<AssetDescriptor> dependencies = new Array<>();

      try {
         this.root = this.xml.parse(tmxFile);
         boolean generateMipMaps = parameter != null ? parameter.generateMipMaps : false;
         TextureLoader.TextureParameter texParams = new TextureLoader.TextureParameter();
         texParams.genMipMaps = generateMipMaps;
         if (parameter != null) {
            texParams.minFilter = parameter.textureMinFilter;
            texParams.magFilter = parameter.textureMagFilter;
         }

         for (FileHandle image : this.loadTilesets(this.root, tmxFile)) {
            dependencies.add(new AssetDescriptor<>(image, Texture.class, texParams));
         }

         for (FileHandle image : this.loadImages(this.root, tmxFile)) {
            dependencies.add(new AssetDescriptor<>(image, Texture.class, texParams));
         }

         return dependencies;
      } catch (IOException var9) {
         throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", var9);
      }
   }

   protected TiledMap loadTilemap(XmlReader.Element root, FileHandle tmxFile, ImageResolver imageResolver) {
      TiledMap map = new TiledMap();
      String mapOrientation = root.getAttribute("orientation", null);
      int mapWidth = root.getIntAttribute("width", 0);
      int mapHeight = root.getIntAttribute("height", 0);
      int tileWidth = root.getIntAttribute("tilewidth", 0);
      int tileHeight = root.getIntAttribute("tileheight", 0);
      int hexSideLength = root.getIntAttribute("hexsidelength", 0);
      String staggerAxis = root.getAttribute("staggeraxis", null);
      String staggerIndex = root.getAttribute("staggerindex", null);
      String mapBackgroundColor = root.getAttribute("backgroundcolor", null);
      MapProperties mapProperties = map.getProperties();
      if (mapOrientation != null) {
         mapProperties.put("orientation", mapOrientation);
      }

      mapProperties.put("width", mapWidth);
      mapProperties.put("height", mapHeight);
      mapProperties.put("tilewidth", tileWidth);
      mapProperties.put("tileheight", tileHeight);
      mapProperties.put("hexsidelength", hexSideLength);
      if (staggerAxis != null) {
         mapProperties.put("staggeraxis", staggerAxis);
      }

      if (staggerIndex != null) {
         mapProperties.put("staggerindex", staggerIndex);
      }

      if (mapBackgroundColor != null) {
         mapProperties.put("backgroundcolor", mapBackgroundColor);
      }

      this.mapTileWidth = tileWidth;
      this.mapTileHeight = tileHeight;
      this.mapWidthInPixels = mapWidth * tileWidth;
      this.mapHeightInPixels = mapHeight * tileHeight;
      if (mapOrientation != null && "staggered".equals(mapOrientation) && mapHeight > 1) {
         this.mapWidthInPixels += tileWidth / 2;
         this.mapHeightInPixels = this.mapHeightInPixels / 2 + tileHeight / 2;
      }

      XmlReader.Element properties = root.getChildByName("properties");
      if (properties != null) {
         this.loadProperties(map.getProperties(), properties);
      }

      for (XmlReader.Element element : root.getChildrenByName("tileset")) {
         this.loadTileSet(map, element, tmxFile, imageResolver);
         root.removeChild(element);
      }

      int i = 0;

      for (int j = root.getChildCount(); i < j; i++) {
         XmlReader.Element element = root.getChild(i);
         String name = element.getName();
         if (name.equals("layer")) {
            this.loadTileLayer(map, element);
         } else if (name.equals("objectgroup")) {
            this.loadObjectGroup(map, element);
         } else if (name.equals("imagelayer")) {
            this.loadImageLayer(map, element, tmxFile, imageResolver);
         }
      }

      return map;
   }

   protected Array<FileHandle> loadTilesets(XmlReader.Element root, FileHandle tmxFile) throws IOException {
      Array<FileHandle> images = new Array<>();

      for (XmlReader.Element tileset : root.getChildrenByName("tileset")) {
         String source = tileset.getAttribute("source", null);
         if (source != null) {
            FileHandle tsxFile = getRelativeFileHandle(tmxFile, source);
            tileset = this.xml.parse(tsxFile);
            XmlReader.Element imageElement = tileset.getChildByName("image");
            if (imageElement != null) {
               String imageSource = tileset.getChildByName("image").getAttribute("source");
               FileHandle image = getRelativeFileHandle(tsxFile, imageSource);
               images.add(image);
            } else {
               for (XmlReader.Element tile : tileset.getChildrenByName("tile")) {
                  String imageSource = tile.getChildByName("image").getAttribute("source");
                  FileHandle image = getRelativeFileHandle(tsxFile, imageSource);
                  images.add(image);
               }
            }
         } else {
            XmlReader.Element imageElement = tileset.getChildByName("image");
            if (imageElement != null) {
               String imageSource = tileset.getChildByName("image").getAttribute("source");
               FileHandle image = getRelativeFileHandle(tmxFile, imageSource);
               images.add(image);
            } else {
               for (XmlReader.Element tile : tileset.getChildrenByName("tile")) {
                  String imageSource = tile.getChildByName("image").getAttribute("source");
                  FileHandle image = getRelativeFileHandle(tmxFile, imageSource);
                  images.add(image);
               }
            }
         }
      }

      return images;
   }

   protected Array<FileHandle> loadImages(XmlReader.Element root, FileHandle tmxFile) throws IOException {
      Array<FileHandle> images = new Array<>();

      for (XmlReader.Element imageLayer : root.getChildrenByName("imagelayer")) {
         XmlReader.Element image = imageLayer.getChildByName("image");
         String source = image.getAttribute("source", null);
         if (source != null) {
            FileHandle handle = getRelativeFileHandle(tmxFile, source);
            if (!images.contains(handle, false)) {
               images.add(handle);
            }
         }
      }

      return images;
   }

   protected void loadTileSet(TiledMap map, XmlReader.Element element, FileHandle tmxFile, ImageResolver imageResolver) {
      if (element.getName().equals("tileset")) {
         String name = element.get("name", null);
         int firstgid = element.getIntAttribute("firstgid", 1);
         int tilewidth = element.getIntAttribute("tilewidth", 0);
         int tileheight = element.getIntAttribute("tileheight", 0);
         int spacing = element.getIntAttribute("spacing", 0);
         int margin = element.getIntAttribute("margin", 0);
         String source = element.getAttribute("source", null);
         int offsetX = 0;
         int offsetY = 0;
         String imageSource = "";
         int imageWidth = 0;
         int imageHeight = 0;
         FileHandle image = null;
         if (source != null) {
            FileHandle tsx = getRelativeFileHandle(tmxFile, source);

            try {
               element = this.xml.parse(tsx);
               name = element.get("name", null);
               tilewidth = element.getIntAttribute("tilewidth", 0);
               tileheight = element.getIntAttribute("tileheight", 0);
               spacing = element.getIntAttribute("spacing", 0);
               margin = element.getIntAttribute("margin", 0);
               XmlReader.Element offset = element.getChildByName("tileoffset");
               if (offset != null) {
                  offsetX = offset.getIntAttribute("x", 0);
                  offsetY = offset.getIntAttribute("y", 0);
               }

               XmlReader.Element imageElement = element.getChildByName("image");
               if (imageElement != null) {
                  imageSource = imageElement.getAttribute("source");
                  imageWidth = imageElement.getIntAttribute("width", 0);
                  imageHeight = imageElement.getIntAttribute("height", 0);
                  image = getRelativeFileHandle(tsx, imageSource);
               }
            } catch (IOException var30) {
               throw new GdxRuntimeException("Error parsing external tileset.");
            }
         } else {
            XmlReader.Element offsetx = element.getChildByName("tileoffset");
            if (offsetx != null) {
               offsetX = offsetx.getIntAttribute("x", 0);
               offsetY = offsetx.getIntAttribute("y", 0);
            }

            XmlReader.Element imageElement = element.getChildByName("image");
            if (imageElement != null) {
               imageSource = imageElement.getAttribute("source");
               imageWidth = imageElement.getIntAttribute("width", 0);
               imageHeight = imageElement.getIntAttribute("height", 0);
               image = getRelativeFileHandle(tmxFile, imageSource);
            }
         }

         TiledMapTileSet tileset = new TiledMapTileSet();
         tileset.setName(name);
         tileset.getProperties().put("firstgid", firstgid);
         if (image == null) {
            for (XmlReader.Element tileElement : element.getChildrenByName("tile")) {
               XmlReader.Element imageElement = tileElement.getChildByName("image");
               if (imageElement != null) {
                  imageSource = imageElement.getAttribute("source");
                  imageWidth = imageElement.getIntAttribute("width", 0);
                  imageHeight = imageElement.getIntAttribute("height", 0);
                  image = getRelativeFileHandle(tmxFile, imageSource);
               }

               TextureRegion texture = imageResolver.getImage(image.path());
               TiledMapTile tile = new StaticTiledMapTile(texture);
               tile.setId(firstgid + tileElement.getIntAttribute("id"));
               tile.setOffsetX(offsetX);
               tile.setOffsetY(this.flipY ? -offsetY : offsetY);
               tileset.putTile(tile.getId(), tile);
            }
         } else {
            TextureRegion texture = imageResolver.getImage(image.path());
            MapProperties props = tileset.getProperties();
            props.put("imagesource", imageSource);
            props.put("imagewidth", imageWidth);
            props.put("imageheight", imageHeight);
            props.put("tilewidth", tilewidth);
            props.put("tileheight", tileheight);
            props.put("margin", margin);
            props.put("spacing", spacing);
            int stopWidth = texture.getRegionWidth() - tilewidth;
            int stopHeight = texture.getRegionHeight() - tileheight;
            int id = firstgid;

            for (int y = margin; y <= stopHeight; y += tileheight + spacing) {
               for (int x = margin; x <= stopWidth; x += tilewidth + spacing) {
                  TextureRegion tileRegion = new TextureRegion(texture, x, y, tilewidth, tileheight);
                  TiledMapTile tile = new StaticTiledMapTile(tileRegion);
                  tile.setId(id);
                  tile.setOffsetX(offsetX);
                  tile.setOffsetY(this.flipY ? -offsetY : offsetY);
                  tileset.putTile(id++, tile);
               }
            }
         }

         Array<XmlReader.Element> tileElements = element.getChildrenByName("tile");
         Array<AnimatedTiledMapTile> animatedTiles = new Array<>();

         for (XmlReader.Element tileElement : tileElements) {
            int localtid = tileElement.getIntAttribute("id", 0);
            TiledMapTile tile = tileset.getTile(firstgid + localtid);
            if (tile != null) {
               XmlReader.Element animationElement = tileElement.getChildByName("animation");
               if (animationElement != null) {
                  Array<StaticTiledMapTile> staticTiles = new Array<>();
                  IntArray intervals = new IntArray();

                  for (XmlReader.Element frameElement : animationElement.getChildrenByName("frame")) {
                     staticTiles.add((StaticTiledMapTile)tileset.getTile(firstgid + frameElement.getIntAttribute("tileid")));
                     intervals.add(frameElement.getIntAttribute("duration"));
                  }

                  AnimatedTiledMapTile animatedTile = new AnimatedTiledMapTile(intervals, staticTiles);
                  animatedTile.setId(tile.getId());
                  animatedTiles.add(animatedTile);
                  tile = animatedTile;
               }

               String terrain = tileElement.getAttribute("terrain", null);
               if (terrain != null) {
                  tile.getProperties().put("terrain", terrain);
               }

               String probability = tileElement.getAttribute("probability", null);
               if (probability != null) {
                  tile.getProperties().put("probability", probability);
               }

               XmlReader.Element properties = tileElement.getChildByName("properties");
               if (properties != null) {
                  this.loadProperties(tile.getProperties(), properties);
               }
            }
         }

         for (AnimatedTiledMapTile tile : animatedTiles) {
            tileset.putTile(tile.getId(), tile);
         }

         XmlReader.Element properties = element.getChildByName("properties");
         if (properties != null) {
            this.loadProperties(tileset.getProperties(), properties);
         }

         map.getTileSets().addTileSet(tileset);
      }
   }

   public static class Parameters extends BaseTmxMapLoader.Parameters {
   }
}
