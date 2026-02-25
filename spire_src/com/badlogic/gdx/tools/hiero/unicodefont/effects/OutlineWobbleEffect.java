package com.badlogic.gdx.tools.hiero.unicodefont.effects;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.util.List;

public class OutlineWobbleEffect extends OutlineEffect {
   float detail = 1.0F;
   float amplitude = 1.0F;

   public OutlineWobbleEffect() {
      this.setStroke(new OutlineWobbleEffect.WobbleStroke());
   }

   public OutlineWobbleEffect(int width, Color color) {
      super(width, color);
   }

   @Override
   public String toString() {
      return "Outline (Wobble)";
   }

   @Override
   public List getValues() {
      List values = super.getValues();
      values.remove(2);
      values.add(
         EffectUtil.floatValue(
            "Detail",
            this.detail,
            1.0F,
            50.0F,
            "This setting controls how detailed the outline will be. Smaller numbers cause the outline to have more detail."
         )
      );
      values.add(EffectUtil.floatValue("Amplitude", this.amplitude, 0.5F, 50.0F, "This setting controls the amplitude of the outline."));
      return values;
   }

   @Override
   public void setValues(List values) {
      super.setValues(values);

      for (ConfigurableEffect.Value value : values) {
         if (value.getName().equals("Detail")) {
            this.detail = (Float)value.getObject();
         } else if (value.getName().equals("Amplitude")) {
            this.amplitude = (Float)value.getObject();
         }
      }
   }

   class WobbleStroke implements Stroke {
      private static final float FLATNESS = 1.0F;

      @Override
      public Shape createStrokedShape(Shape shape) {
         GeneralPath result = new GeneralPath();
         shape = new BasicStroke(OutlineWobbleEffect.this.getWidth(), 2, OutlineWobbleEffect.this.getJoin()).createStrokedShape(shape);
         PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), 1.0);
         float[] points = new float[6];
         float moveX = 0.0F;
         float moveY = 0.0F;
         float lastX = 0.0F;
         float lastY = 0.0F;
         float thisX = 0.0F;
         float thisY = 0.0F;
         int type = 0;

         for (float next = 0.0F; !it.isDone(); it.next()) {
            type = it.currentSegment(points);
            switch (type) {
               case 0:
                  moveX = lastX = this.randomize(points[0]);
                  moveY = lastY = this.randomize(points[1]);
                  result.moveTo(moveX, moveY);
                  next = 0.0F;
                  continue;
               case 1:
                  break;
               case 2:
               case 3:
               default:
                  continue;
               case 4:
                  points[0] = moveX;
                  points[1] = moveY;
            }

            thisX = this.randomize(points[0]);
            thisY = this.randomize(points[1]);
            float dx = thisX - lastX;
            float dy = thisY - lastY;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            if (distance >= next) {
               for (float r = 1.0F / distance; distance >= next; next += OutlineWobbleEffect.this.detail) {
                  float x = lastX + next * dx * r;
                  float y = lastY + next * dy * r;
                  result.lineTo(this.randomize(x), this.randomize(y));
               }
            }

            next -= distance;
            lastX = thisX;
            lastY = thisY;
         }

         return result;
      }

      private float randomize(float x) {
         return x + (float)Math.random() * OutlineWobbleEffect.this.amplitude * 2.0F - 1.0F;
      }
   }
}
