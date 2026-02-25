package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;

public class TextureAtlasLoader extends SynchronousAssetLoader<TextureAtlas, TextureAtlasLoader.TextureAtlasParameter> {
   TextureAtlas.TextureAtlasData data;

   public TextureAtlasLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public TextureAtlas load(AssetManager assetManager, String fileName, FileHandle file, TextureAtlasLoader.TextureAtlasParameter parameter) {
      for (TextureAtlas.TextureAtlasData.Page page : this.data.getPages()) {
         Texture texture = assetManager.get(page.textureFile.path().replaceAll("\\\\", "/"), Texture.class);
         page.texture = texture;
      }

      return new TextureAtlas(this.data);
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle atlasFile, TextureAtlasLoader.TextureAtlasParameter parameter) {
      FileHandle imgDir = atlasFile.parent();
      if (parameter != null) {
         this.data = new TextureAtlas.TextureAtlasData(atlasFile, imgDir, parameter.flip);
      } else {
         this.data = new TextureAtlas.TextureAtlasData(atlasFile, imgDir, false);
      }

      Array<AssetDescriptor> dependencies = new Array<>();

      for (TextureAtlas.TextureAtlasData.Page page : this.data.getPages()) {
         TextureLoader.TextureParameter params = new TextureLoader.TextureParameter();
         params.format = page.format;
         params.genMipMaps = page.useMipMaps;
         params.minFilter = page.minFilter;
         params.magFilter = page.magFilter;
         dependencies.add(new AssetDescriptor<>(page.textureFile, Texture.class, params));
      }

      return dependencies;
   }

   public static class TextureAtlasParameter extends AssetLoaderParameters<TextureAtlas> {
      public boolean flip = false;

      public TextureAtlasParameter() {
      }

      public TextureAtlasParameter(boolean flip) {
         this.flip = flip;
      }
   }
}
