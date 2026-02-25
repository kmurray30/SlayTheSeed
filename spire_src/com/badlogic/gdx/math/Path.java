package com.badlogic.gdx.math;

public interface Path<T> {
   T derivativeAt(T var1, float var2);

   T valueAt(T var1, float var2);

   float approximate(T var1);

   float locate(T var1);

   float approxLength(int var1);
}
