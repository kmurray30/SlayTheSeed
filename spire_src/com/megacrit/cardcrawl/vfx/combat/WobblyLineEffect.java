package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class WobblyLineEffect extends AbstractGameEffect {
   private static final float EFFECT_DUR = 2.0F;
   private float x;
   private float y;
   private float speed;
   private float speedStart;
   private float speedTarget;
   private float flipper;
   private TextureAtlas.AtlasRegion img = ImageMaster.WOBBLY_LINE;

   public WobblyLineEffect(float x, float y, Color color) {
      this.rotation = MathUtils.random(360.0F);
      this.scale = MathUtils.random(0.2F, 0.4F);
      this.x = x - this.img.packedWidth / 2.0F;
      this.y = y - this.img.packedHeight / 2.0F;
      this.duration = 2.0F;
      this.color = color;
      this.renderBehind = MathUtils.randomBoolean();
      this.speedStart = MathUtils.random(300.0F, 1000.0F) * Settings.scale;
      this.speedTarget = 900.0F * Settings.scale;
      this.speed = this.speedStart;
      if (MathUtils.randomBoolean()) {
         this.flipper = 90.0F;
      } else {
         this.flipper = 270.0F;
      }

      color.g = color.g - MathUtils.random(0.1F);
      color.b = color.b - MathUtils.random(0.2F);
      color.a = 0.0F;
   }

   @Override
   public void update() {
      Vector2 tmp = new Vector2(MathUtils.cosDeg(this.rotation), MathUtils.sinDeg(this.rotation));
      tmp.x = tmp.x * (this.speed * Gdx.graphics.getDeltaTime());
      tmp.y = tmp.y * (this.speed * Gdx.graphics.getDeltaTime());
      this.speed = Interpolation.fade.apply(this.speedStart, this.speedTarget, 1.0F - this.duration / 2.0F);
      this.x = this.x + tmp.x;
      this.y = this.y + tmp.y;
      this.scale = this.scale + Gdx.graphics.getDeltaTime();
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      } else if (this.duration > 1.5F) {
         this.color.a = Interpolation.fade.apply(0.0F, 0.7F, (2.0F - this.duration) * 2.0F);
      } else if (this.duration < 0.5F) {
         this.color.a = Interpolation.fade.apply(0.0F, 0.7F, this.duration * 2.0F);
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
      sb.setColor(this.color);
      sb.draw(
         this.img,
         this.x,
         this.y,
         this.img.packedWidth / 2.0F,
         this.img.packedHeight / 2.0F,
         this.img.packedWidth,
         this.img.packedHeight,
         this.scale + MathUtils.random(-0.08F, 0.08F) * Settings.scale,
         this.scale + MathUtils.random(-0.08F, 0.08F) * Settings.scale,
         this.rotation + this.flipper + MathUtils.random(-5.0F, 5.0F)
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
