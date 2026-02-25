package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.utils.Array;

public class TextureLoader extends AsynchronousAssetLoader<Texture, TextureLoader.TextureParameter> {
   TextureLoader.TextureLoaderInfo info = new TextureLoader.TextureLoaderInfo();

   public TextureLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle file, TextureLoader.TextureParameter parameter) {
      this.info.filename = fileName;
      if (parameter != null && parameter.textureData != null) {
         this.info.data = parameter.textureData;
         this.info.texture = parameter.texture;
      } else {
         Pixmap pixmap = null;
         Pixmap.Format format = null;
         boolean genMipMaps = false;
         this.info.texture = null;
         if (parameter != null) {
            format = parameter.format;
            genMipMaps = parameter.genMipMaps;
            this.info.texture = parameter.texture;
         }

         this.info.data = TextureData.Factory.loadFromFile(file, format, genMipMaps);
      }

      if (!this.info.data.isPrepared()) {
         this.info.data.prepare();
      }
   }

   public Texture loadSync(AssetManager manager, String fileName, FileHandle file, TextureLoader.TextureParameter parameter) {
      if (this.info == null) {
         return null;
      } else {
         Texture texture = this.info.texture;
         if (texture != null) {
            texture.load(this.info.data);
         } else {
            texture = new Texture(this.info.data);
         }

         if (parameter != null) {
            texture.setFilter(parameter.minFilter, parameter.magFilter);
            texture.setWrap(parameter.wrapU, parameter.wrapV);
         }

         return texture;
      }
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, TextureLoader.TextureParameter parameter) {
      return null;
   }

   public static class TextureLoaderInfo {
      String filename;
      TextureData data;
      Texture texture;
   }

   public static class TextureParameter extends AssetLoaderParameters<Texture> {
      public Pixmap.Format format = null;
      public boolean genMipMaps = false;
      public Texture texture = null;
      public TextureData textureData = null;
      public Texture.TextureFilter minFilter = Texture.TextureFilter.Nearest;
      public Texture.TextureFilter magFilter = Texture.TextureFilter.Nearest;
      public Texture.TextureWrap wrapU = Texture.TextureWrap.ClampToEdge;
      public Texture.TextureWrap wrapV = Texture.TextureWrap.ClampToEdge;
   }
}
