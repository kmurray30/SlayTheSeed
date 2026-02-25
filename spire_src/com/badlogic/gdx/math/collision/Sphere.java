package com.badlogic.gdx.math.collision;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.NumberUtils;
import java.io.Serializable;

public class Sphere implements Serializable {
   private static final long serialVersionUID = -6487336868908521596L;
   public float radius;
   public final Vector3 center;
   private static final float PI_4_3 = (float) (Math.PI * 4.0 / 3.0);

   public Sphere(Vector3 center, float radius) {
      this.center = new Vector3(center);
      this.radius = radius;
   }

   public boolean overlaps(Sphere sphere) {
      return this.center.dst2(sphere.center) < (this.radius + sphere.radius) * (this.radius + sphere.radius);
   }

   @Override
   public int hashCode() {
      int prime = 71;
      int result = 1;
      result = 71 * result + this.center.hashCode();
      return 71 * result + NumberUtils.floatToRawIntBits(this.radius);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && o.getClass() == this.getClass()) {
         Sphere s = (Sphere)o;
         return this.radius == s.radius && this.center.equals(s.center);
      } else {
         return false;
      }
   }

   public float volume() {
      return (float) (Math.PI * 4.0 / 3.0) * this.radius * this.radius * this.radius;
   }

   public float surfaceArea() {
      return (float) (Math.PI * 4) * this.radius * this.radius;
   }
}
