package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class EmpowerCircleEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float vX;
   private float vY;
   private TextureAtlas.AtlasRegion img;

   public EmpowerCircleEffect(float x, float y) {
      this.duration = MathUtils.random(0.8F, 3.2F);
      this.startingDuration = this.duration;
      if (MathUtils.randomBoolean()) {
         this.img = ImageMaster.POWER_UP_1;
      } else {
         this.img = ImageMaster.POWER_UP_2;
      }

      this.x = x - this.img.packedWidth / 2.0F;
      this.y = y - this.img.packedHeight / 2.0F;
      this.vX = MathUtils.random(-6000.0F * Settings.scale, 6000.0F * Settings.scale);
      this.vY = MathUtils.random(-6000.0F * Settings.scale, 6000.0F * Settings.scale);
      this.rotation = new Vector2(this.vX, this.vY).angle();
      if (MathUtils.randomBoolean()) {
         this.color = Settings.CREAM_COLOR.cpy();
      } else {
         this.color = Color.SLATE.cpy();
      }

      this.renderBehind = true;
   }

   @Override
   public void update() {
      this.x = this.x + this.vX * Gdx.graphics.getDeltaTime();
      this.y = this.y + this.vY * Gdx.graphics.getDeltaTime();
      this.vX = MathHelper.fadeLerpSnap(this.vX, 0.0F);
      this.vY = MathHelper.fadeLerpSnap(this.vY, 0.0F);
      this.scale = Settings.scale * this.duration / this.startingDuration;
      super.update();
   }

   @Override
   public void render(SpriteBatch sb) {
      if (!this.isDone) {
         sb.setColor(this.color);
         sb.draw(
            this.img,
            this.x,
            this.y,
            this.img.packedWidth / 2.0F,
            this.img.packedHeight / 2.0F,
            this.img.packedWidth,
            this.img.packedHeight,
            this.scale * MathUtils.random(0.9F, 1.1F),
            this.scale * MathUtils.random(0.9F, 1.1F),
            this.rotation
         );
      }
   }

   @Override
   public void dispose() {
   }
}
