package com.badlogic.gdx.tools.hiero.unicodefont.effects;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.util.List;

public class OutlineZigzagEffect extends OutlineEffect {
   float amplitude = 1.0F;
   float wavelength = 3.0F;

   public OutlineZigzagEffect() {
      this.setStroke(new OutlineZigzagEffect.ZigzagStroke());
   }

   public OutlineZigzagEffect(int width, Color color) {
      super(width, color);
   }

   @Override
   public String toString() {
      return "Outline (Zigzag)";
   }

   @Override
   public List getValues() {
      List values = super.getValues();
      values.add(
         EffectUtil.floatValue(
            "Wavelength",
            this.wavelength,
            1.0F,
            100.0F,
            "This setting controls the wavelength of the outline. The smaller the value, the more segments will be used to draw the outline."
         )
      );
      values.add(
         EffectUtil.floatValue(
            "Amplitude",
            this.amplitude,
            0.5F,
            50.0F,
            "This setting controls the amplitude of the outline. The bigger the value, the more the zigzags will vary."
         )
      );
      return values;
   }

   @Override
   public void setValues(List values) {
      super.setValues(values);

      for (ConfigurableEffect.Value value : values) {
         if (value.getName().equals("Wavelength")) {
            this.wavelength = (Float)value.getObject();
         } else if (value.getName().equals("Amplitude")) {
            this.amplitude = (Float)value.getObject();
         }
      }
   }

   class ZigzagStroke implements Stroke {
      private static final float FLATNESS = 1.0F;

      @Override
      public Shape createStrokedShape(Shape shape) {
         GeneralPath result = new GeneralPath();
         PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), 1.0);
         float[] points = new float[6];
         float moveX = 0.0F;
         float moveY = 0.0F;
         float lastX = 0.0F;
         float lastY = 0.0F;
         float thisX = 0.0F;
         float thisY = 0.0F;
         int type = 0;
         float next = 0.0F;

         for (int phase = 0; !it.isDone(); it.next()) {
            type = it.currentSegment(points);
            switch (type) {
               case 0:
                  moveX = lastX = points[0];
                  moveY = lastY = points[1];
                  result.moveTo(moveX, moveY);
                  next = OutlineZigzagEffect.this.wavelength / 2.0F;
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

            thisX = points[0];
            thisY = points[1];
            float dx = thisX - lastX;
            float dy = thisY - lastY;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            if (distance >= next) {
               for (float r = 1.0F / distance; distance >= next; phase++) {
                  float x = lastX + next * dx * r;
                  float y = lastY + next * dy * r;
                  if ((phase & 1) == 0) {
                     result.lineTo(x + OutlineZigzagEffect.this.amplitude * dy * r, y - OutlineZigzagEffect.this.amplitude * dx * r);
                  } else {
                     result.lineTo(x - OutlineZigzagEffect.this.amplitude * dy * r, y + OutlineZigzagEffect.this.amplitude * dx * r);
                  }

                  next += OutlineZigzagEffect.this.wavelength;
               }
            }

            next -= distance;
            lastX = thisX;
            lastY = thisY;
            if (type == 4) {
               result.closePath();
            }
         }

         return new BasicStroke(OutlineZigzagEffect.this.getWidth(), 2, OutlineZigzagEffect.this.getJoin()).createStrokedShape(result);
      }
   }
}
