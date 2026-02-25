package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class EventBgParticle extends AbstractGameEffect {
   private float x;
   private float y;
   private float aV;
   private float offsetX;
   private static TextureAtlas.AtlasRegion img;
   private static TextureAtlas.AtlasRegion img2;
   private TextureAtlas.AtlasRegion useImg;

   public EventBgParticle() {
      if (img == null) {
         img = ImageMaster.vfxAtlas.findRegion("eventBgParticle1");
         img2 = ImageMaster.vfxAtlas.findRegion("eventBgParticle2");
      }

      if (MathUtils.randomBoolean()) {
         this.useImg = img;
      } else {
         this.useImg = img2;
      }

      this.duration = 20.0F;
      this.startingDuration = this.duration;
      this.x = Settings.WIDTH / 2.0F - this.useImg.packedWidth / 2;
      this.y = Settings.HEIGHT / 2.0F - this.useImg.packedHeight / 2;
      this.scale = Settings.scale * MathUtils.random(0.3F, 3.0F);
      this.rotation = MathUtils.random(360.0F);
      this.offsetX = MathUtils.random(600.0F, 670.0F);
      this.aV = MathUtils.random(0.01F, 7.0F) + this.offsetX / 300.0F;
      this.offsetX = this.offsetX * Settings.scale;
      this.scale = this.scale + this.offsetX / 900.0F;
      float g = MathUtils.random(0.05F, 0.1F);
      this.color = new Color(0.0F, g, g, 0.1F);
      this.renderBehind = true;
   }

   @Override
   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      this.rotation = this.rotation + Gdx.graphics.getDeltaTime() * this.aV;
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      if (this.duration > 16.0F) {
         this.color.a = Interpolation.fade.apply(0.3F, 0.0F, (this.duration - 16.0F) / 4.0F);
      } else if (this.duration < 4.0F) {
         this.color.a = Interpolation.fade.apply(0.0F, 0.3F, this.duration / 4.0F);
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.draw(
         this.useImg,
         this.x - this.offsetX,
         this.y,
         this.useImg.packedWidth / 2.0F + this.offsetX,
         this.useImg.packedHeight / 2.0F,
         this.useImg.packedWidth,
         this.useImg.packedHeight,
         this.scale,
         this.scale,
         this.rotation
      );
   }

   @Override
   public void dispose() {
   }
}
