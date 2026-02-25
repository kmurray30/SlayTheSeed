package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.CubemapData;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.KTXTextureData;
import com.badlogic.gdx.utils.Array;

public class CubemapLoader extends AsynchronousAssetLoader<Cubemap, CubemapLoader.CubemapParameter> {
   CubemapLoader.CubemapLoaderInfo info = new CubemapLoader.CubemapLoaderInfo();

   public CubemapLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle file, CubemapLoader.CubemapParameter parameter) {
      this.info.filename = fileName;
      if (parameter != null && parameter.cubemapData != null) {
         this.info.data = parameter.cubemapData;
         this.info.cubemap = parameter.cubemap;
      } else {
         Pixmap pixmap = null;
         Pixmap.Format format = null;
         boolean genMipMaps = false;
         this.info.cubemap = null;
         if (parameter != null) {
            format = parameter.format;
            this.info.cubemap = parameter.cubemap;
         }

         if (fileName.contains(".ktx") || fileName.contains(".zktx")) {
            this.info.data = new KTXTextureData(file, genMipMaps);
         }
      }

      if (!this.info.data.isPrepared()) {
         this.info.data.prepare();
      }
   }

   public Cubemap loadSync(AssetManager manager, String fileName, FileHandle file, CubemapLoader.CubemapParameter parameter) {
      if (this.info == null) {
         return null;
      } else {
         Cubemap cubemap = this.info.cubemap;
         if (cubemap != null) {
            cubemap.load(this.info.data);
         } else {
            cubemap = new Cubemap(this.info.data);
         }

         if (parameter != null) {
            cubemap.setFilter(parameter.minFilter, parameter.magFilter);
            cubemap.setWrap(parameter.wrapU, parameter.wrapV);
         }

         return cubemap;
      }
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, CubemapLoader.CubemapParameter parameter) {
      return null;
   }

   public static class CubemapLoaderInfo {
      String filename;
      CubemapData data;
      Cubemap cubemap;
   }

   public static class CubemapParameter extends AssetLoaderParameters<Cubemap> {
      public Pixmap.Format format = null;
      public Cubemap cubemap = null;
      public CubemapData cubemapData = null;
      public Texture.TextureFilter minFilter = Texture.TextureFilter.Nearest;
      public Texture.TextureFilter magFilter = Texture.TextureFilter.Nearest;
      public Texture.TextureWrap wrapU = Texture.TextureWrap.ClampToEdge;
      public Texture.TextureWrap wrapV = Texture.TextureWrap.ClampToEdge;
   }
}
