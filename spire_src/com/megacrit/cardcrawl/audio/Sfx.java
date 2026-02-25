package com.megacrit.cardcrawl.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Sfx {
   private static final Logger logger = LogManager.getLogger(Sfx.class.getName());
   private String url;
   private Sound sound;

   public Sfx(String url, boolean preload) {
      if (preload) {
         this.sound = this.initSound(Gdx.files.internal(url));
      } else {
         this.url = url;
      }
   }

   public Sfx(String url) {
      this(url, false);
   }

   public long play(float volume) {
      this.sound = this.initSound(Gdx.files.internal(this.url));
      return this.sound != null ? this.sound.play(volume) : 0L;
   }

   public long play(float volume, float y, float z) {
      this.sound = this.initSound(Gdx.files.internal(this.url));
      return this.sound != null ? this.sound.play(volume, y, z) : 0L;
   }

   public long loop(float volume) {
      this.sound = this.initSound(Gdx.files.internal(this.url));
      return this.sound != null ? this.sound.loop(volume) : 0L;
   }

   public void setVolume(long id, float volume) {
      if (this.sound != null) {
         this.sound.setVolume(id, volume);
      }
   }

   public void stop() {
      logger.info("stopping");
      if (this.sound != null) {
         this.sound.stop();
      }
   }

   public void stop(long id) {
      if (this.sound != null) {
         this.sound.stop(id);
      }
   }

   private Sound initSound(FileHandle file) {
      if (this.sound == null) {
         if (file != null) {
            if (Gdx.audio != null) {
               return Gdx.audio.newSound(file);
            } else {
               logger.info("WARNING: Gdx.audio is null");
               return null;
            }
         } else {
            logger.info("File: " + this.url + " was not found.");
            return null;
         }
      } else {
         return this.sound;
      }
   }
}
