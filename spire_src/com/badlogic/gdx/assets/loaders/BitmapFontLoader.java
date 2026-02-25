package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class BitmapFontLoader extends AsynchronousAssetLoader<BitmapFont, BitmapFontLoader.BitmapFontParameter> {
   BitmapFont.BitmapFontData data;

   public BitmapFontLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, BitmapFontLoader.BitmapFontParameter parameter) {
      Array<AssetDescriptor> deps = new Array<>();
      if (parameter != null && parameter.bitmapFontData != null) {
         this.data = parameter.bitmapFontData;
         return deps;
      } else {
         this.data = new BitmapFont.BitmapFontData(file, parameter != null ? parameter.flip : false);
         if (parameter != null && parameter.atlasName != null) {
            deps.add(new AssetDescriptor<>(parameter.atlasName, TextureAtlas.class));
         } else {
            for (int i = 0; i < this.data.getImagePaths().length; i++) {
               String path = this.data.getImagePath(i);
               FileHandle resolved = this.resolve(path);
               TextureLoader.TextureParameter textureParams = new TextureLoader.TextureParameter();
               if (parameter != null) {
                  textureParams.genMipMaps = parameter.genMipMaps;
                  textureParams.minFilter = parameter.minFilter;
                  textureParams.magFilter = parameter.magFilter;
               }

               AssetDescriptor descriptor = new AssetDescriptor<>(resolved, Texture.class, textureParams);
               deps.add(descriptor);
            }
         }

         return deps;
      }
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle file, BitmapFontLoader.BitmapFontParameter parameter) {
   }

   public BitmapFont loadSync(AssetManager manager, String fileName, FileHandle file, BitmapFontLoader.BitmapFontParameter parameter) {
      if (parameter != null && parameter.atlasName != null) {
         TextureAtlas atlas = manager.get(parameter.atlasName, TextureAtlas.class);
         String name = file.sibling(this.data.imagePaths[0]).nameWithoutExtension().toString();
         TextureAtlas.AtlasRegion region = atlas.findRegion(name);
         if (region == null) {
            throw new GdxRuntimeException("Could not find font region " + name + " in atlas " + parameter.atlasName);
         } else {
            return new BitmapFont(file, region);
         }
      } else {
         int n = this.data.getImagePaths().length;
         Array<TextureRegion> regs = new Array<>(n);

         for (int i = 0; i < n; i++) {
            regs.add(new TextureRegion(manager.get(this.data.getImagePath(i), Texture.class)));
         }

         return new BitmapFont(this.data, regs, true);
      }
   }

   public static class BitmapFontParameter extends AssetLoaderParameters<BitmapFont> {
      public boolean flip = false;
      public boolean genMipMaps = false;
      public Texture.TextureFilter minFilter = Texture.TextureFilter.Nearest;
      public Texture.TextureFilter magFilter = Texture.TextureFilter.Nearest;
      public BitmapFont.BitmapFontData bitmapFontData = null;
      public String atlasName = null;
   }
}
