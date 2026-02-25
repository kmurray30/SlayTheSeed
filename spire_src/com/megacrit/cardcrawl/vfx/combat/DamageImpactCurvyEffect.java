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
import java.util.ArrayList;

public class DamageImpactCurvyEffect extends AbstractGameEffect {
   private Vector2 pos = new Vector2();
   private float speed;
   private float speedStart;
   private float speedTarget;
   private float waveIntensity;
   private float waveSpeed;
   private TextureAtlas.AtlasRegion img;
   private ArrayList<Vector2> positions = new ArrayList<>();

   public DamageImpactCurvyEffect(float x, float y, Color color, boolean renderBehind) {
      this.img = ImageMaster.STRIKE_LINE_2;
      this.duration = MathUtils.random(0.8F, 1.1F);
      this.startingDuration = this.duration;
      this.pos.x = x - this.img.packedWidth / 2.0F;
      this.pos.y = y - this.img.packedHeight / 2.0F;
      this.speed = MathUtils.random(400.0F, 900.0F) * Settings.scale;
      this.speedStart = this.speed;
      this.speedTarget = MathUtils.random(200.0F, 300.0F) * Settings.scale;
      this.color = color;
      this.renderBehind = renderBehind;
      this.rotation = MathUtils.random(360.0F);
      this.waveIntensity = MathUtils.random(5.0F, 30.0F);
      this.waveSpeed = MathUtils.random(-20.0F, 20.0F);
      this.speedTarget = MathUtils.random(0.1F, 0.5F);
   }

   public DamageImpactCurvyEffect(float x, float y) {
      this(x, y, Color.GOLDENROD, true);
   }

   @Override
   public void update() {
      this.positions.add(this.pos);
      Vector2 tmp = new Vector2(MathUtils.cosDeg(this.rotation), MathUtils.sinDeg(this.rotation));
      tmp.x = tmp.x * (this.speed * Gdx.graphics.getDeltaTime());
      tmp.y = tmp.y * (this.speed * Gdx.graphics.getDeltaTime());
      this.speed = Interpolation.pow2OutInverse.apply(this.speedStart, this.speedTarget, 1.0F - this.duration / this.startingDuration);
      this.pos.x = this.pos.x + tmp.x;
      this.pos.y = this.pos.y + tmp.y;
      this.rotation = this.rotation + MathUtils.cos(this.duration * this.waveSpeed) * this.waveIntensity * Gdx.graphics.getDeltaTime() * 60.0F;
      this.scale = Settings.scale * this.duration / this.startingDuration * 0.75F;
      super.update();
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
      Color tmp = this.color.cpy();
      tmp.a = 0.25F;

      for (int i = this.positions.size() - 1; i > 0; i--) {
         sb.setColor(tmp);
         tmp.a *= 0.95F;
         if (tmp.a > 0.05F) {
            float var10004 = this.img.packedWidth / 2.0F;
            float var10005 = this.img.packedHeight / 2.0F;
            float var10006 = this.img.packedWidth;
            float var10007 = this.img.packedHeight;
            float var10008 = this.scale * 2.0F;
            float var10009 = this.scale * 2.0F;
            sb.draw(this.img, this.positions.get(i).x, this.positions.get(i).y, var10004, var10005, var10006, var10007, var10008, var10009, this.rotation);
         }
      }

      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
