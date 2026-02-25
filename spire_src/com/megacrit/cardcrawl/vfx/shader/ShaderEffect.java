package com.megacrit.cardcrawl.vfx.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;

public class ShaderEffect {
   private ArrayList<TextureRegion> heatRegions;
   private ArrayList<Vector2> heatCoords;
   private ArrayList<Vector2> heatDimensions;
   private Vector3 coords;
   private float time = 0.0F;
   private ShaderProgram shader;

   public ShaderEffect(FrameBuffer frameBuffer) {
      this.coords = new Vector3(0.0F, 0.0F, 0.0F);
      this.heatRegions = new ArrayList<>();
      this.heatCoords = new ArrayList<>();
      this.heatDimensions = new ArrayList<>();
      this.shader = new ShaderProgram(Gdx.files.internal("shaders/water/vertex.vs").readString(), Gdx.files.internal("shaders/water/fragment.fs").readString());
      this.heatRegions.add(new TextureRegion(frameBuffer.getColorBufferTexture()));
      this.heatCoords.add(new Vector2(0.0F, 0.0F));
      this.heatDimensions.add(new Vector2(32.0F, 32.0F));
   }

   public void update() {
      float dt = Gdx.graphics.getDeltaTime();
      this.time += dt;
      float angle = this.time * (float) (Math.PI * 2);
      if (angle > (float) (Math.PI * 2)) {
         angle -= (float) (Math.PI * 2);
      }

      Gdx.gl20.glBlendFunc(770, 771);
      Gdx.gl20.glEnable(3042);
      this.shader.begin();
      this.shader.setUniformf("timedelta", -angle);
      this.shader.end();
   }

   public void render(SpriteBatch sb, FrameBuffer frameBuffer) {
      sb.begin();

      for (int i = 0; i < this.heatRegions.size(); i++) {
         TextureRegion region = this.heatRegions.get(i);
         this.coords.set(this.heatCoords.get(i).x, this.heatCoords.get(i).y, 0.0F);
         region.setTexture(frameBuffer.getColorBufferTexture());
         region.setRegion(this.coords.x, this.coords.y, this.heatDimensions.get(i).x * 1.0F, this.heatDimensions.get(i).y * 1.0F);
         sb.draw(region, this.coords.x, this.coords.y, this.heatDimensions.get(i).x * 1.0F, this.heatDimensions.get(i).y * 1.0F);
      }
   }
}
