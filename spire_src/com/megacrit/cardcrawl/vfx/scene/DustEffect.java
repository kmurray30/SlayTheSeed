package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DustEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float vX;
   private float vY;
   private float aV;
   private float baseAlpha;
   private TextureAtlas.AtlasRegion img;

   public DustEffect() {
      this.startingDuration = MathUtils.random(5.0F, 14.0F);
      this.duration = this.startingDuration;
      this.img = this.getImg();
      this.scale = Settings.scale * MathUtils.random(0.1F, 0.8F);
      this.x = MathUtils.random(0, Settings.WIDTH);
      this.y = MathUtils.random(-100.0F, 400.0F) * Settings.scale + AbstractDungeon.floorY;
      this.vX = MathUtils.random(-12.0F, 12.0F) * Settings.scale;
      this.vY = MathUtils.random(-12.0F, 30.0F) * Settings.scale;
      float colorTmp = MathUtils.random(0.1F, 0.7F);
      this.color = new Color(colorTmp, colorTmp, colorTmp, 0.0F);
      this.baseAlpha = 1.0F - colorTmp;
      this.color.a = 0.0F;
      this.rotation = MathUtils.random(0.0F, 360.0F);
      this.aV = MathUtils.random(-120.0F, 120.0F);
   }

   private TextureAtlas.AtlasRegion getImg() {
      switch (MathUtils.random(0, 5)) {
         case 0:
            return ImageMaster.DUST_1;
         case 1:
            return ImageMaster.DUST_2;
         case 2:
            return ImageMaster.DUST_3;
         case 3:
            return ImageMaster.DUST_4;
         case 4:
            return ImageMaster.DUST_5;
         default:
            return ImageMaster.DUST_6;
      }
   }

   @Override
   public void update() {
      this.rotation = this.rotation + this.aV * Gdx.graphics.getDeltaTime();
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      this.x = this.x + this.vX * Gdx.graphics.getDeltaTime();
      this.y = this.y + this.vY * Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      if (this.duration > this.startingDuration / 2.0F) {
         float tmp = this.duration - this.startingDuration / 2.0F;
         this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.startingDuration / 2.0F - tmp) * this.baseAlpha;
      } else {
         this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration / (this.startingDuration / 2.0F)) * this.baseAlpha;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.draw(this.img, this.x, this.y, this.img.offsetX, this.img.offsetY, this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
   }

   @Override
   public void dispose() {
   }
}
