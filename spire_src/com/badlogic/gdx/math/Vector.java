package com.badlogic.gdx.math;

public interface Vector<T extends Vector<T>> {
   T cpy();

   float len();

   float len2();

   T limit(float var1);

   T limit2(float var1);

   T setLength(float var1);

   T setLength2(float var1);

   T clamp(float var1, float var2);

   T set(T var1);

   T sub(T var1);

   T nor();

   T add(T var1);

   float dot(T var1);

   T scl(float var1);

   T scl(T var1);

   float dst(T var1);

   float dst2(T var1);

   T lerp(T var1, float var2);

   T interpolate(T var1, float var2, Interpolation var3);

   T setToRandomDirection();

   boolean isUnit();

   boolean isUnit(float var1);

   boolean isZero();

   boolean isZero(float var1);

   boolean isOnLine(T var1, float var2);

   boolean isOnLine(T var1);

   boolean isCollinear(T var1, float var2);

   boolean isCollinear(T var1);

   boolean isCollinearOpposite(T var1, float var2);

   boolean isCollinearOpposite(T var1);

   boolean isPerpendicular(T var1);

   boolean isPerpendicular(T var1, float var2);

   boolean hasSameDirection(T var1);

   boolean hasOppositeDirection(T var1);

   boolean epsilonEquals(T var1, float var2);

   T mulAdd(T var1, float var2);

   T mulAdd(T var1, T var2);

   T setZero();
}
