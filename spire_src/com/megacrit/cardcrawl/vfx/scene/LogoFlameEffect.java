package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class LogoFlameEffect extends AbstractGameEffect {
   private TextureAtlas.AtlasRegion img;
   private float offsetX;
   private float offsetY;
   private float vX;
   private float vY;
   private float startingDuration;

   public LogoFlameEffect() {
      int roll = MathUtils.random(2);
      switch (roll) {
         case 0:
            this.img = ImageMaster.vfxAtlas.findRegion("buffVFX1");
            break;
         case 1:
            this.img = ImageMaster.vfxAtlas.findRegion("buffVFX2");
            break;
         default:
            this.img = ImageMaster.vfxAtlas.findRegion("buffVFX3");
      }

      this.offsetX = MathUtils.random(10.0F, 30.0F) * Settings.scale;
      this.offsetY = MathUtils.random(230.0F, 240.0F) * Settings.scale;
      this.vX = MathUtils.random(-10.0F, 10.0F) * Settings.scale;
      this.vY = MathUtils.random(30.0F, 70.0F) * Settings.scale;
      this.duration = MathUtils.random(1.0F, 1.5F);
      this.startingDuration = this.duration;
      this.color = Color.WHITE.cpy();
      this.color.r = MathUtils.random(1.0F);
      this.scale = Settings.scale;
   }

   @Override
   public void update() {
      this.offsetX = this.offsetX + this.vX * Gdx.graphics.getDeltaTime();
      this.offsetY = this.offsetY + this.vY * Gdx.graphics.getDeltaTime();
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      } else if (this.duration < 0.5F) {
         this.color.a = this.duration;
         this.scale = this.duration * 2.0F * Settings.scale;
      } else if (this.startingDuration - this.duration < 0.5F) {
         this.color.a = this.startingDuration - this.duration;
      } else {
         this.color.a = 0.5F;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
   }

   @Override
   public void render(SpriteBatch sb, float x, float y) {
      sb.setColor(this.color);
      sb.draw(
         this.img,
         x + this.offsetX,
         y + this.offsetY,
         this.img.packedWidth / 2.0F,
         this.img.packedHeight / 2.0F,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale,
         this.scale,
         this.rotation
      );
   }

   @Override
   public void dispose() {
   }
}
