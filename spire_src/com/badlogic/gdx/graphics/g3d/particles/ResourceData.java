package com.badlogic.gdx.graphics.g3d.particles;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;

public class ResourceData<T> implements Json.Serializable {
   private ObjectMap<String, ResourceData.SaveData> uniqueData = new ObjectMap<>();
   private Array<ResourceData.SaveData> data = new Array<>(true, 3, ResourceData.SaveData.class);
   Array<ResourceData.AssetData> sharedAssets = new Array<>();
   private int currentLoadIndex = 0;
   public T resource;

   public ResourceData() {
   }

   public ResourceData(T resource) {
      this();
      this.resource = resource;
   }

   <K> int getAssetData(String filename, Class<K> type) {
      int i = 0;

      for (ResourceData.AssetData data : this.sharedAssets) {
         if (data.filename.equals(filename) && data.type.equals(type)) {
            return i;
         }

         i++;
      }

      return -1;
   }

   public Array<AssetDescriptor> getAssetDescriptors() {
      Array<AssetDescriptor> descriptors = new Array<>();

      for (ResourceData.AssetData data : this.sharedAssets) {
         descriptors.add(new AssetDescriptor<>(data.filename, data.type));
      }

      return descriptors;
   }

   public Array<ResourceData.AssetData> getAssets() {
      return this.sharedAssets;
   }

   public ResourceData.SaveData createSaveData() {
      ResourceData.SaveData saveData = new ResourceData.SaveData(this);
      this.data.add(saveData);
      return saveData;
   }

   public ResourceData.SaveData createSaveData(String key) {
      ResourceData.SaveData saveData = new ResourceData.SaveData(this);
      if (this.uniqueData.containsKey(key)) {
         throw new RuntimeException("Key already used, data must be unique, use a different key");
      } else {
         this.uniqueData.put(key, saveData);
         return saveData;
      }
   }

   public ResourceData.SaveData getSaveData() {
      return this.data.get(this.currentLoadIndex++);
   }

   public ResourceData.SaveData getSaveData(String key) {
      return this.uniqueData.get(key);
   }

   @Override
   public void write(Json json) {
      json.writeValue("unique", this.uniqueData, ObjectMap.class);
      json.writeValue("data", this.data, Array.class, ResourceData.SaveData.class);
      json.writeValue("assets", this.sharedAssets.toArray(ResourceData.AssetData.class), ResourceData.AssetData[].class);
      json.writeValue("resource", this.resource, null);
   }

   @Override
   public void read(Json json, JsonValue jsonData) {
      this.uniqueData = json.readValue("unique", ObjectMap.class, jsonData);

      for (ObjectMap.Entry<String, ResourceData.SaveData> entry : this.uniqueData.entries()) {
         entry.value.resources = this;
      }

      this.data = json.readValue("data", Array.class, ResourceData.SaveData.class, jsonData);

      for (ResourceData.SaveData saveData : this.data) {
         saveData.resources = this;
      }

      this.sharedAssets.addAll(json.readValue("assets", Array.class, ResourceData.AssetData.class, jsonData));
      this.resource = json.readValue("resource", null, jsonData);
   }

   public static class AssetData<T> implements Json.Serializable {
      public String filename;
      public Class<T> type;

      public AssetData() {
      }

      public AssetData(String filename, Class<T> type) {
         this.filename = filename;
         this.type = type;
      }

      @Override
      public void write(Json json) {
         json.writeValue("filename", this.filename);
         json.writeValue("type", this.type.getName());
      }

      @Override
      public void read(Json json, JsonValue jsonData) {
         this.filename = json.readValue("filename", String.class, jsonData);
         String className = json.readValue("type", String.class, jsonData);

         try {
            this.type = ClassReflection.forName(className);
         } catch (ReflectionException var5) {
            throw new GdxRuntimeException("Class not found: " + className, var5);
         }
      }
   }

   public interface Configurable<T> {
      void save(AssetManager var1, ResourceData<T> var2);

      void load(AssetManager var1, ResourceData<T> var2);
   }

   public static class SaveData implements Json.Serializable {
      ObjectMap<String, Object> data = new ObjectMap<>();
      IntArray assets = new IntArray();
      private int loadIndex = 0;
      protected ResourceData resources;

      public SaveData() {
      }

      public SaveData(ResourceData resources) {
         this.resources = resources;
      }

      public <K> void saveAsset(String filename, Class<K> type) {
         int i = this.resources.getAssetData(filename, type);
         if (i == -1) {
            this.resources.sharedAssets.add(new ResourceData.AssetData<>(filename, type));
            i = this.resources.sharedAssets.size - 1;
         }

         this.assets.add(i);
      }

      public void save(String key, Object value) {
         this.data.put(key, value);
      }

      public AssetDescriptor loadAsset() {
         if (this.loadIndex == this.assets.size) {
            return null;
         } else {
            ResourceData.AssetData data = this.resources.sharedAssets.get(this.assets.get(this.loadIndex++));
            return new AssetDescriptor<>(data.filename, data.type);
         }
      }

      public <K> K load(String key) {
         return (K)this.data.get(key);
      }

      @Override
      public void write(Json json) {
         json.writeValue("data", this.data, ObjectMap.class);
         json.writeValue("indices", this.assets.toArray(), int[].class);
      }

      @Override
      public void read(Json json, JsonValue jsonData) {
         this.data = json.readValue("data", ObjectMap.class, jsonData);
         this.assets.addAll(json.readValue("indices", int[].class, jsonData));
      }
   }
}
