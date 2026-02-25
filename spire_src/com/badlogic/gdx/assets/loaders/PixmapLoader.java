package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;

public class PixmapLoader extends AsynchronousAssetLoader<Pixmap, PixmapLoader.PixmapParameter> {
   Pixmap pixmap;

   public PixmapLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle file, PixmapLoader.PixmapParameter parameter) {
      this.pixmap = null;
      this.pixmap = new Pixmap(file);
   }

   public Pixmap loadSync(AssetManager manager, String fileName, FileHandle file, PixmapLoader.PixmapParameter parameter) {
      Pixmap pixmap = this.pixmap;
      this.pixmap = null;
      return pixmap;
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, PixmapLoader.PixmapParameter parameter) {
      return null;
   }

   public static class PixmapParameter extends AssetLoaderParameters<Pixmap> {
   }
}
