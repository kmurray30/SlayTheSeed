package com.badlogic.gdx.math;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.NumberUtils;
import java.io.Serializable;

public class Vector2 implements Serializable, Vector<Vector2> {
   private static final long serialVersionUID = 913902788239530931L;
   public static final Vector2 X = new Vector2(1.0F, 0.0F);
   public static final Vector2 Y = new Vector2(0.0F, 1.0F);
   public static final Vector2 Zero = new Vector2(0.0F, 0.0F);
   public float x;
   public float y;

   public Vector2() {
   }

   public Vector2(float x, float y) {
      this.x = x;
      this.y = y;
   }

   public Vector2(Vector2 v) {
      this.set(v);
   }

   public Vector2 cpy() {
      return new Vector2(this);
   }

   public static float len(float x, float y) {
      return (float)Math.sqrt(x * x + y * y);
   }

   @Override
   public float len() {
      return (float)Math.sqrt(this.x * this.x + this.y * this.y);
   }

   public static float len2(float x, float y) {
      return x * x + y * y;
   }

   @Override
   public float len2() {
      return this.x * this.x + this.y * this.y;
   }

   public Vector2 set(Vector2 v) {
      this.x = v.x;
      this.y = v.y;
      return this;
   }

   public Vector2 set(float x, float y) {
      this.x = x;
      this.y = y;
      return this;
   }

   public Vector2 sub(Vector2 v) {
      this.x = this.x - v.x;
      this.y = this.y - v.y;
      return this;
   }

   public Vector2 sub(float x, float y) {
      this.x -= x;
      this.y -= y;
      return this;
   }

   public Vector2 nor() {
      float len = this.len();
      if (len != 0.0F) {
         this.x /= len;
         this.y /= len;
      }

      return this;
   }

   public Vector2 add(Vector2 v) {
      this.x = this.x + v.x;
      this.y = this.y + v.y;
      return this;
   }

   public Vector2 add(float x, float y) {
      this.x += x;
      this.y += y;
      return this;
   }

   public static float dot(float x1, float y1, float x2, float y2) {
      return x1 * x2 + y1 * y2;
   }

   public float dot(Vector2 v) {
      return this.x * v.x + this.y * v.y;
   }

   public float dot(float ox, float oy) {
      return this.x * ox + this.y * oy;
   }

   public Vector2 scl(float scalar) {
      this.x *= scalar;
      this.y *= scalar;
      return this;
   }

   public Vector2 scl(float x, float y) {
      this.x *= x;
      this.y *= y;
      return this;
   }

   public Vector2 scl(Vector2 v) {
      this.x = this.x * v.x;
      this.y = this.y * v.y;
      return this;
   }

   public Vector2 mulAdd(Vector2 vec, float scalar) {
      this.x = this.x + vec.x * scalar;
      this.y = this.y + vec.y * scalar;
      return this;
   }

   public Vector2 mulAdd(Vector2 vec, Vector2 mulVec) {
      this.x = this.x + vec.x * mulVec.x;
      this.y = this.y + vec.y * mulVec.y;
      return this;
   }

   public static float dst(float x1, float y1, float x2, float y2) {
      float x_d = x2 - x1;
      float y_d = y2 - y1;
      return (float)Math.sqrt(x_d * x_d + y_d * y_d);
   }

   public float dst(Vector2 v) {
      float x_d = v.x - this.x;
      float y_d = v.y - this.y;
      return (float)Math.sqrt(x_d * x_d + y_d * y_d);
   }

   public float dst(float x, float y) {
      float x_d = x - this.x;
      float y_d = y - this.y;
      return (float)Math.sqrt(x_d * x_d + y_d * y_d);
   }

   public static float dst2(float x1, float y1, float x2, float y2) {
      float x_d = x2 - x1;
      float y_d = y2 - y1;
      return x_d * x_d + y_d * y_d;
   }

   public float dst2(Vector2 v) {
      float x_d = v.x - this.x;
      float y_d = v.y - this.y;
      return x_d * x_d + y_d * y_d;
   }

   public float dst2(float x, float y) {
      float x_d = x - this.x;
      float y_d = y - this.y;
      return x_d * x_d + y_d * y_d;
   }

   public Vector2 limit(float limit) {
      return this.limit2(limit * limit);
   }

   public Vector2 limit2(float limit2) {
      float len2 = this.len2();
      return len2 > limit2 ? this.scl((float)Math.sqrt(limit2 / len2)) : this;
   }

   public Vector2 clamp(float min, float max) {
      float len2 = this.len2();
      if (len2 == 0.0F) {
         return this;
      } else {
         float max2 = max * max;
         if (len2 > max2) {
            return this.scl((float)Math.sqrt(max2 / len2));
         } else {
            float min2 = min * min;
            return len2 < min2 ? this.scl((float)Math.sqrt(min2 / len2)) : this;
         }
      }
   }

   public Vector2 setLength(float len) {
      return this.setLength2(len * len);
   }

   public Vector2 setLength2(float len2) {
      float oldLen2 = this.len2();
      return oldLen2 != 0.0F && oldLen2 != len2 ? this.scl((float)Math.sqrt(len2 / oldLen2)) : this;
   }

   @Override
   public String toString() {
      return "(" + this.x + "," + this.y + ")";
   }

   public Vector2 fromString(String v) {
      int s = v.indexOf(44, 1);
      if (s != -1 && v.charAt(0) == '(' && v.charAt(v.length() - 1) == ')') {
         try {
            float x = Float.parseFloat(v.substring(1, s));
            float y = Float.parseFloat(v.substring(s + 1, v.length() - 1));
            return this.set(x, y);
         } catch (NumberFormatException var5) {
         }
      }

      throw new GdxRuntimeException("Malformed Vector2: " + v);
   }

   public Vector2 mul(Matrix3 mat) {
      float x = this.x * mat.val[0] + this.y * mat.val[3] + mat.val[6];
      float y = this.x * mat.val[1] + this.y * mat.val[4] + mat.val[7];
      this.x = x;
      this.y = y;
      return this;
   }

   public float crs(Vector2 v) {
      return this.x * v.y - this.y * v.x;
   }

   public float crs(float x, float y) {
      return this.x * y - this.y * x;
   }

   public float angle() {
      float angle = (float)Math.atan2(this.y, this.x) * (180.0F / (float)Math.PI);
      if (angle < 0.0F) {
         angle += 360.0F;
      }

      return angle;
   }

   public float angle(Vector2 reference) {
      return (float)Math.atan2(this.crs(reference), this.dot(reference)) * (180.0F / (float)Math.PI);
   }

   public float angleRad() {
      return (float)Math.atan2(this.y, this.x);
   }

   public float angleRad(Vector2 reference) {
      return (float)Math.atan2(this.crs(reference), this.dot(reference));
   }

   public Vector2 setAngle(float degrees) {
      return this.setAngleRad(degrees * (float) (Math.PI / 180.0));
   }

   public Vector2 setAngleRad(float radians) {
      this.set(this.len(), 0.0F);
      this.rotateRad(radians);
      return this;
   }

   public Vector2 rotate(float degrees) {
      return this.rotateRad(degrees * (float) (Math.PI / 180.0));
   }

   public Vector2 rotateRad(float radians) {
      float cos = (float)Math.cos(radians);
      float sin = (float)Math.sin(radians);
      float newX = this.x * cos - this.y * sin;
      float newY = this.x * sin + this.y * cos;
      this.x = newX;
      this.y = newY;
      return this;
   }

   public Vector2 rotate90(int dir) {
      float x = this.x;
      if (dir >= 0) {
         this.x = -this.y;
         this.y = x;
      } else {
         this.x = this.y;
         this.y = -x;
      }

      return this;
   }

   public Vector2 lerp(Vector2 target, float alpha) {
      float invAlpha = 1.0F - alpha;
      this.x = this.x * invAlpha + target.x * alpha;
      this.y = this.y * invAlpha + target.y * alpha;
      return this;
   }

   public Vector2 interpolate(Vector2 target, float alpha, Interpolation interpolation) {
      return this.lerp(target, interpolation.apply(alpha));
   }

   public Vector2 setToRandomDirection() {
      float theta = MathUtils.random(0.0F, (float) (Math.PI * 2));
      return this.set(MathUtils.cos(theta), MathUtils.sin(theta));
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = 1;
      result = 31 * result + NumberUtils.floatToIntBits(this.x);
      return 31 * result + NumberUtils.floatToIntBits(this.y);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         Vector2 other = (Vector2)obj;
         return NumberUtils.floatToIntBits(this.x) != NumberUtils.floatToIntBits(other.x)
            ? false
            : NumberUtils.floatToIntBits(this.y) == NumberUtils.floatToIntBits(other.y);
      }
   }

   public boolean epsilonEquals(Vector2 other, float epsilon) {
      if (other == null) {
         return false;
      } else {
         return Math.abs(other.x - this.x) > epsilon ? false : !(Math.abs(other.y - this.y) > epsilon);
      }
   }

   public boolean epsilonEquals(float x, float y, float epsilon) {
      return Math.abs(x - this.x) > epsilon ? false : !(Math.abs(y - this.y) > epsilon);
   }

   @Override
   public boolean isUnit() {
      return this.isUnit(1.0E-9F);
   }

   @Override
   public boolean isUnit(float margin) {
      return Math.abs(this.len2() - 1.0F) < margin;
   }

   @Override
   public boolean isZero() {
      return this.x == 0.0F && this.y == 0.0F;
   }

   @Override
   public boolean isZero(float margin) {
      return this.len2() < margin;
   }

   public boolean isOnLine(Vector2 other) {
      return MathUtils.isZero(this.x * other.y - this.y * other.x);
   }

   public boolean isOnLine(Vector2 other, float epsilon) {
      return MathUtils.isZero(this.x * other.y - this.y * other.x, epsilon);
   }

   public boolean isCollinear(Vector2 other, float epsilon) {
      return this.isOnLine(other, epsilon) && this.dot(other) > 0.0F;
   }

   public boolean isCollinear(Vector2 other) {
      return this.isOnLine(other) && this.dot(other) > 0.0F;
   }

   public boolean isCollinearOpposite(Vector2 other, float epsilon) {
      return this.isOnLine(other, epsilon) && this.dot(other) < 0.0F;
   }

   public boolean isCollinearOpposite(Vector2 other) {
      return this.isOnLine(other) && this.dot(other) < 0.0F;
   }

   public boolean isPerpendicular(Vector2 vector) {
      return MathUtils.isZero(this.dot(vector));
   }

   public boolean isPerpendicular(Vector2 vector, float epsilon) {
      return MathUtils.isZero(this.dot(vector), epsilon);
   }

   public boolean hasSameDirection(Vector2 vector) {
      return this.dot(vector) > 0.0F;
   }

   public boolean hasOppositeDirection(Vector2 vector) {
      return this.dot(vector) < 0.0F;
   }

   public Vector2 setZero() {
      this.x = 0.0F;
      this.y = 0.0F;
      return this;
   }
}
