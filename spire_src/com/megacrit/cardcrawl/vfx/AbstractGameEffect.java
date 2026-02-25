package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.megacrit.cardcrawl.core.Settings;

public abstract class AbstractGameEffect implements Disposable {
   public float duration;
   public float startingDuration;
   protected Color color;
   public boolean isDone = false;
   protected float scale;
   protected float rotation;
   public boolean renderBehind;

   public AbstractGameEffect() {
      this.scale = Settings.scale;
      this.rotation = 0.0F;
      this.renderBehind = false;
   }

   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < this.startingDuration / 2.0F) {
         this.color.a = this.duration / (this.startingDuration / 2.0F);
      }

      if (this.duration < 0.0F) {
         this.isDone = true;
         this.color.a = 0.0F;
      }
   }

   public abstract void render(SpriteBatch var1);

   public void render(SpriteBatch sb, float x, float y) {
   }

   @Override
   public abstract void dispose();
}
