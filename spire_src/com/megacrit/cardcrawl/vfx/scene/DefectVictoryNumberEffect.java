package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DefectVictoryNumberEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float incrementTimer;
   private String num = "";
   private boolean dontIncrement = false;

   public DefectVictoryNumberEffect() {
      this.renderBehind = true;
      this.x = MathUtils.random(0.0F, 1870.0F) * Settings.xScale;
      this.y = MathUtils.random(50.0F, 990.0F) * Settings.yScale;
      this.duration = MathUtils.random(2.0F, 4.0F);
      this.color = new Color(MathUtils.random(0.5F, 1.0F), MathUtils.random(0.5F, 1.0F), MathUtils.random(0.5F, 1.0F), 0.0F);
      this.scale = MathUtils.random(0.7F, 1.3F);
      this.incrementTimer = MathUtils.random(0.02F, 0.1F);
      switch (MathUtils.random(100)) {
         case 0:
            this.num = "H3110";
            this.dontIncrement = true;
            break;
         case 1:
            this.num = "D00T D00T";
            this.dontIncrement = true;
            break;
         case 2:
            this.num = "<ERR0R>";
            this.dontIncrement = true;
      }
   }

   @Override
   public void update() {
      if (!this.dontIncrement) {
         this.incrementTimer = this.incrementTimer - Gdx.graphics.getDeltaTime();
         if (this.incrementTimer < 0.0F) {
            if (MathUtils.randomBoolean()) {
               this.num = this.num + "0";
            } else {
               this.num = this.num + "1";
            }

            this.incrementTimer = MathUtils.random(0.1F, 0.4F);
         }
      }

      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      } else {
         if (this.duration < 1.0F) {
            this.color.a = Interpolation.bounceOut.apply(0.0F, 0.5F, this.duration);
         } else {
            this.color.a = MathHelper.slowColorLerpSnap(this.color.a, 0.5F);
         }
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
      FontHelper.energyNumFontBlue.getData().setScale(this.scale);
      FontHelper.renderFont(sb, FontHelper.energyNumFontBlue, this.num, this.x, this.y, this.color);
      FontHelper.energyNumFontBlue.getData().setScale(1.0F);
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
