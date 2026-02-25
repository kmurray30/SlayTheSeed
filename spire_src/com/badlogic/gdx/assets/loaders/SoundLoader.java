package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

public class SoundLoader extends AsynchronousAssetLoader<Sound, SoundLoader.SoundParameter> {
   private Sound sound;

   public SoundLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   protected Sound getLoadedSound() {
      return this.sound;
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle file, SoundLoader.SoundParameter parameter) {
      this.sound = Gdx.audio.newSound(file);
   }

   public Sound loadSync(AssetManager manager, String fileName, FileHandle file, SoundLoader.SoundParameter parameter) {
      Sound sound = this.sound;
      this.sound = null;
      return sound;
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, SoundLoader.SoundParameter parameter) {
      return null;
   }

   public static class SoundParameter extends AssetLoaderParameters<Sound> {
   }
}
