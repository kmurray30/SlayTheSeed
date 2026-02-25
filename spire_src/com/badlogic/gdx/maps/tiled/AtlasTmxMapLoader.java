package com.badlogic.gdx.maps.tiled;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import java.io.IOException;

public class AtlasTmxMapLoader extends BaseTmxMapLoader<AtlasTmxMapLoader.AtlasTiledMapLoaderParameters> {
   protected Array<Texture> trackedTextures = new Array<>();

   public AtlasTmxMapLoader() {
      super(new InternalFileHandleResolver());
   }

   public AtlasTmxMapLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public TiledMap load(String fileName) {
      return this.load(fileName, new AtlasTmxMapLoader.AtlasTiledMapLoaderParameters());
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle tmxFile, AtlasTmxMapLoader.AtlasTiledMapLoaderParameters parameter) {
      Array<AssetDescriptor> dependencies = new Array<>();

      try {
         this.root = this.xml.parse(tmxFile);
         XmlReader.Element properties = this.root.getChildByName("properties");
         if (properties != null) {
            for (XmlReader.Element property : properties.getChildrenByName("property")) {
               String name = property.getAttribute("name");
               String value = property.getAttribute("value");
               if (name.startsWith("atlas")) {
                  FileHandle atlasHandle = getRelativeFileHandle(tmxFile, value);
                  dependencies.add(new AssetDescriptor<>(atlasHandle, TextureAtlas.class));
               }
            }
         }

         return dependencies;
      } catch (IOException var11) {
         throw new GdxRuntimeException("Unable to parse .tmx file.");
      }
   }

   public TiledMap load(String fileName, AtlasTmxMapLoader.AtlasTiledMapLoaderParameters parameter) {
      try {
         if (parameter != null) {
            this.convertObjectToTileSpace = parameter.convertObjectToTileSpace;
            this.flipY = parameter.flipY;
         } else {
            this.convertObjectToTileSpace = false;
            this.flipY = true;
         }

         FileHandle tmxFile = this.resolve(fileName);
         this.root = this.xml.parse(tmxFile);
         ObjectMap<String, TextureAtlas> atlases = new ObjectMap<>();
         FileHandle atlasFile = this.loadAtlas(this.root, tmxFile);
         if (atlasFile == null) {
            throw new GdxRuntimeException("Couldn't load atlas");
         } else {
            TextureAtlas atlas = new TextureAtlas(atlasFile);
            atlases.put(atlasFile.path(), atlas);
            AtlasTmxMapLoader.AtlasResolver.DirectAtlasResolver atlasResolver = new AtlasTmxMapLoader.AtlasResolver.DirectAtlasResolver(atlases);
            TiledMap map = this.loadMap(this.root, tmxFile, atlasResolver);
            map.setOwnedResources(atlases.values().toArray());
            this.setTextureFilters(parameter.textureMinFilter, parameter.textureMagFilter);
            return map;
         }
      } catch (IOException var9) {
         throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", var9);
      }
   }

   protected FileHandle loadAtlas(XmlReader.Element root, FileHandle tmxFile) throws IOException {
      XmlReader.Element e = root.getChildByName("properties");
      if (e != null) {
         for (XmlReader.Element property : e.getChildrenByName("property")) {
            String name = property.getAttribute("name", null);
            String value = property.getAttribute("value", null);
            if (name.equals("atlas")) {
               if (value == null) {
                  value = property.getText();
               }

               if (value != null && value.length() != 0) {
                  return getRelativeFileHandle(tmxFile, value);
               }
            }
         }
      }

      FileHandle atlasFile = tmxFile.sibling(tmxFile.nameWithoutExtension() + ".atlas");
      return atlasFile.exists() ? atlasFile : null;
   }

   private void setTextureFilters(Texture.TextureFilter min, Texture.TextureFilter mag) {
      for (Texture texture : this.trackedTextures) {
         texture.setFilter(min, mag);
      }

      this.trackedTextures.clear();
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle tmxFile, AtlasTmxMapLoader.AtlasTiledMapLoaderParameters parameter) {
      this.map = null;
      if (parameter != null) {
         this.convertObjectToTileSpace = parameter.convertObjectToTileSpace;
         this.flipY = parameter.flipY;
      } else {
         this.convertObjectToTileSpace = false;
         this.flipY = true;
      }

      try {
         this.map = this.loadMap(this.root, tmxFile, new AtlasTmxMapLoader.AtlasResolver.AssetManagerAtlasResolver(manager));
      } catch (Exception var6) {
         throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", var6);
      }
   }

   public TiledMap loadSync(AssetManager manager, String fileName, FileHandle file, AtlasTmxMapLoader.AtlasTiledMapLoaderParameters parameter) {
      if (parameter != null) {
         this.setTextureFilters(parameter.textureMinFilter, parameter.textureMagFilter);
      }

      return this.map;
   }

   protected TiledMap loadMap(XmlReader.Element root, FileHandle tmxFile, AtlasTmxMapLoader.AtlasResolver resolver) {
      TiledMap map = new TiledMap();
      String mapOrientation = root.getAttribute("orientation", null);
      int mapWidth = root.getIntAttribute("width", 0);
      int mapHeight = root.getIntAttribute("height", 0);
      int tileWidth = root.getIntAttribute("tilewidth", 0);
      int tileHeight = root.getIntAttribute("tileheight", 0);
      String mapBackgroundColor = root.getAttribute("backgroundcolor", null);
      MapProperties mapProperties = map.getProperties();
      if (mapOrientation != null) {
         mapProperties.put("orientation", mapOrientation);
      }

      mapProperties.put("width", mapWidth);
      mapProperties.put("height", mapHeight);
      mapProperties.put("tilewidth", tileWidth);
      mapProperties.put("tileheight", tileHeight);
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

      int i = 0;

      for (int j = root.getChildCount(); i < j; i++) {
         XmlReader.Element element = root.getChild(i);
         String elementName = element.getName();
         if (elementName.equals("properties")) {
            this.loadProperties(map.getProperties(), element);
         } else if (elementName.equals("tileset")) {
            this.loadTileset(map, element, tmxFile, resolver);
         } else if (elementName.equals("layer")) {
            this.loadTileLayer(map, element);
         } else if (elementName.equals("objectgroup")) {
            this.loadObjectGroup(map, element);
         }
      }

      return map;
   }

   protected void loadTileset(TiledMap map, XmlReader.Element element, FileHandle tmxFile, AtlasTmxMapLoader.AtlasResolver resolver) {
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
            } catch (IOException var35) {
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

         String atlasFilePath = map.getProperties().get("atlas", String.class);
         if (atlasFilePath == null) {
            FileHandle atlasFile = tmxFile.sibling(tmxFile.nameWithoutExtension() + ".atlas");
            if (atlasFile.exists()) {
               atlasFilePath = atlasFile.name();
            }
         }

         if (atlasFilePath == null) {
            throw new GdxRuntimeException("The map is missing the 'atlas' property");
         }

         FileHandle atlasHandle = getRelativeFileHandle(tmxFile, atlasFilePath);
         atlasHandle = this.resolve(atlasHandle.path());
         TextureAtlas atlas = resolver.getAtlas(atlasHandle.path());

         for (Texture texture : atlas.getTextures()) {
            this.trackedTextures.add(texture);
         }

         TiledMapTileSet tileset = new TiledMapTileSet();
         MapProperties props = tileset.getProperties();
         tileset.setName(name);
         props.put("firstgid", firstgid);
         props.put("imagesource", imageSource);
         props.put("imagewidth", imageWidth);
         props.put("imageheight", imageHeight);
         props.put("tilewidth", tilewidth);
         props.put("tileheight", tileheight);
         props.put("margin", margin);
         props.put("spacing", spacing);
         if (imageSource != null && imageSource.length() > 0) {
            int lastgid = firstgid + imageWidth / tilewidth * (imageHeight / tileheight) - 1;

            for (TextureAtlas.AtlasRegion region : atlas.findRegions(name)) {
               if (region != null) {
                  int tileid = region.index + 1;
                  if (tileid >= firstgid && tileid <= lastgid) {
                     StaticTiledMapTile tile = new StaticTiledMapTile(region);
                     tile.setId(tileid);
                     tile.setOffsetX(offsetX);
                     tile.setOffsetY(this.flipY ? -offsetY : offsetY);
                     tileset.putTile(tileid, tile);
                  }
               }
            }
         }

         for (XmlReader.Element tileElement : element.getChildrenByName("tile")) {
            int tileid = firstgid + tileElement.getIntAttribute("id", 0);
            TiledMapTile tile = tileset.getTile(tileid);
            if (tile == null) {
               XmlReader.Element imageElement = tileElement.getChildByName("image");
               if (imageElement != null) {
                  String regionName = imageElement.getAttribute("source");
                  regionName = regionName.substring(0, regionName.lastIndexOf(46));
                  TextureAtlas.AtlasRegion regionx = atlas.findRegion(regionName);
                  if (regionx == null) {
                     throw new GdxRuntimeException("Tileset region not found: " + regionName);
                  }

                  tile = new StaticTiledMapTile(regionx);
                  tile.setId(tileid);
                  tile.setOffsetX(offsetX);
                  tile.setOffsetY(this.flipY ? -offsetY : offsetY);
                  tileset.putTile(tileid, tile);
               }
            }

            if (tile != null) {
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

         Array<XmlReader.Element> tileElements = element.getChildrenByName("tile");
         Array<AnimatedTiledMapTile> animatedTiles = new Array<>();

         for (XmlReader.Element tileElement : tileElements) {
            int localtid = tileElement.getIntAttribute("id", 0);
            TiledMapTile tilex = tileset.getTile(firstgid + localtid);
            if (tilex != null) {
               XmlReader.Element animationElement = tileElement.getChildByName("animation");
               if (animationElement != null) {
                  Array<StaticTiledMapTile> staticTiles = new Array<>();
                  IntArray intervals = new IntArray();

                  for (XmlReader.Element frameElement : animationElement.getChildrenByName("frame")) {
                     staticTiles.add((StaticTiledMapTile)tileset.getTile(firstgid + frameElement.getIntAttribute("tileid")));
                     intervals.add(frameElement.getIntAttribute("duration"));
                  }

                  AnimatedTiledMapTile animatedTile = new AnimatedTiledMapTile(intervals, staticTiles);
                  animatedTile.setId(tilex.getId());
                  animatedTiles.add(animatedTile);
                  tilex = animatedTile;
               }

               String terrainx = tileElement.getAttribute("terrain", null);
               if (terrainx != null) {
                  tilex.getProperties().put("terrain", terrainx);
               }

               String probabilityx = tileElement.getAttribute("probability", null);
               if (probabilityx != null) {
                  tilex.getProperties().put("probability", probabilityx);
               }

               XmlReader.Element properties = tileElement.getChildByName("properties");
               if (properties != null) {
                  this.loadProperties(tilex.getProperties(), properties);
               }
            }
         }

         for (AnimatedTiledMapTile tilex : animatedTiles) {
            tileset.putTile(tilex.getId(), tilex);
         }

         XmlReader.Element properties = element.getChildByName("properties");
         if (properties != null) {
            this.loadProperties(tileset.getProperties(), properties);
         }

         map.getTileSets().addTileSet(tileset);
      }
   }

   private interface AtlasResolver {
      TextureAtlas getAtlas(String var1);

      public static class AssetManagerAtlasResolver implements AtlasTmxMapLoader.AtlasResolver {
         private final AssetManager assetManager;

         public AssetManagerAtlasResolver(AssetManager assetManager) {
            this.assetManager = assetManager;
         }

         @Override
         public TextureAtlas getAtlas(String name) {
            return this.assetManager.get(name, TextureAtlas.class);
         }
      }

      public static class DirectAtlasResolver implements AtlasTmxMapLoader.AtlasResolver {
         private final ObjectMap<String, TextureAtlas> atlases;

         public DirectAtlasResolver(ObjectMap<String, TextureAtlas> atlases) {
            this.atlases = atlases;
         }

         @Override
         public TextureAtlas getAtlas(String name) {
            return this.atlases.get(name);
         }
      }
   }

   public static class AtlasTiledMapLoaderParameters extends BaseTmxMapLoader.Parameters {
      public boolean forceTextureFilters = false;
   }
}
