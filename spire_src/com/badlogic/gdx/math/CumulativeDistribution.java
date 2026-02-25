package com.badlogic.gdx.math;

import com.badlogic.gdx.utils.Array;

public class CumulativeDistribution<T> {
   private Array<CumulativeDistribution<T>.CumulativeValue> values = new Array<>(false, 10, CumulativeDistribution.CumulativeValue.class);

   public void add(T value, float intervalSize) {
      this.values.add(new CumulativeDistribution.CumulativeValue(value, 0.0F, intervalSize));
   }

   public void add(T value) {
      this.values.add(new CumulativeDistribution.CumulativeValue(value, 0.0F, 0.0F));
   }

   public void generate() {
      float sum = 0.0F;

      for (int i = 0; i < this.values.size; i++) {
         sum += this.values.items[i].interval;
         this.values.items[i].frequency = sum;
      }
   }

   public void generateNormalized() {
      float sum = 0.0F;

      for (int i = 0; i < this.values.size; i++) {
         sum += this.values.items[i].interval;
      }

      float intervalSum = 0.0F;

      for (int i = 0; i < this.values.size; i++) {
         intervalSum += this.values.items[i].interval / sum;
         this.values.items[i].frequency = intervalSum;
      }
   }

   public void generateUniform() {
      float freq = 1.0F / this.values.size;

      for (int i = 0; i < this.values.size; i++) {
         this.values.items[i].interval = freq;
         this.values.items[i].frequency = (i + 1) * freq;
      }
   }

   public T value(float probability) {
      CumulativeDistribution<T>.CumulativeValue value = null;
      int imax = this.values.size - 1;
      int imin = 0;

      while (imin <= imax) {
         int imid = imin + (imax - imin) / 2;
         value = this.values.items[imid];
         if (probability < value.frequency) {
            imax = imid - 1;
         } else {
            if (!(probability > value.frequency)) {
               break;
            }

            imin = imid + 1;
         }
      }

      return this.values.items[imin].value;
   }

   public T value() {
      return this.value(MathUtils.random());
   }

   public int size() {
      return this.values.size;
   }

   public float getInterval(int index) {
      return this.values.items[index].interval;
   }

   public T getValue(int index) {
      return this.values.items[index].value;
   }

   public void setInterval(T obj, float intervalSize) {
      for (CumulativeDistribution<T>.CumulativeValue value : this.values) {
         if (value.value == obj) {
            value.interval = intervalSize;
            return;
         }
      }
   }

   public void setInterval(int index, float intervalSize) {
      this.values.items[index].interval = intervalSize;
   }

   public void clear() {
      this.values.clear();
   }

   public class CumulativeValue {
      public T value;
      public float frequency;
      public float interval;

      public CumulativeValue(T value, float frequency, float interval) {
         this.value = value;
         this.frequency = frequency;
         this.interval = interval;
      }
   }
}
