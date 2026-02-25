package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import java.util.Iterator;

public abstract class ModelLoader<P extends ModelLoader.ModelParameters> extends AsynchronousAssetLoader<Model, P> {
   protected Array<ObjectMap.Entry<String, ModelData>> items = new Array<>();
   protected ModelLoader.ModelParameters defaultParameters = new ModelLoader.ModelParameters();

   public ModelLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public abstract ModelData loadModelData(FileHandle var1, P var2);

   public ModelData loadModelData(FileHandle fileHandle) {
      return this.loadModelData(fileHandle, null);
   }

   public Model loadModel(FileHandle fileHandle, TextureProvider textureProvider, P parameters) {
      ModelData data = this.loadModelData(fileHandle, parameters);
      return data == null ? null : new Model(data, textureProvider);
   }

   public Model loadModel(FileHandle fileHandle, P parameters) {
      return this.loadModel(fileHandle, new TextureProvider.FileTextureProvider(), parameters);
   }

   public Model loadModel(FileHandle fileHandle, TextureProvider textureProvider) {
      return this.loadModel(fileHandle, textureProvider, null);
   }

   public Model loadModel(FileHandle fileHandle) {
      return this.loadModel(fileHandle, new TextureProvider.FileTextureProvider(), null);
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, P parameters) {
      Array<AssetDescriptor> deps = new Array<>();
      ModelData data = this.loadModelData(file, parameters);
      if (data == null) {
         return deps;
      } else {
         ObjectMap.Entry<String, ModelData> item = new ObjectMap.Entry<>();
         item.key = fileName;
         item.value = data;
         synchronized (this.items) {
            this.items.add(item);
         }

         TextureLoader.TextureParameter textureParameter = parameters != null ? parameters.textureParameter : this.defaultParameters.textureParameter;

         for (ModelMaterial modelMaterial : data.materials) {
            if (modelMaterial.textures != null) {
               for (ModelTexture modelTexture : modelMaterial.textures) {
                  deps.add(new AssetDescriptor<>(modelTexture.fileName, Texture.class, textureParameter));
               }
            }
         }

         return deps;
      }
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle file, P parameters) {
   }

   public Model loadSync(AssetManager manager, String fileName, FileHandle file, P parameters) {
      ModelData data = null;
      synchronized (this.items) {
         for (int i = 0; i < this.items.size; i++) {
            if (((String)this.items.get(i).key).equals(fileName)) {
               data = (ModelData)this.items.get(i).value;
               this.items.removeIndex(i);
            }
         }
      }

      if (data == null) {
         return null;
      } else {
         Model result = new Model(data, new TextureProvider.AssetTextureProvider(manager));
         Iterator<Disposable> disposables = result.getManagedDisposables().iterator();

         while (disposables.hasNext()) {
            Disposable disposable = disposables.next();
            if (disposable instanceof Texture) {
               disposables.remove();
            }
         }

         data = null;
         return result;
      }
   }

   public static class ModelParameters extends AssetLoaderParameters<Model> {
      public TextureLoader.TextureParameter textureParameter = new TextureLoader.TextureParameter();

      public ModelParameters() {
         this.textureParameter.minFilter = this.textureParameter.magFilter = Texture.TextureFilter.Linear;
         this.textureParameter.wrapU = this.textureParameter.wrapV = Texture.TextureWrap.Repeat;
      }
   }
}
