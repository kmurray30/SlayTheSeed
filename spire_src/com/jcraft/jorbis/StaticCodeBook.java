package com.jcraft.jorbis;

import com.jcraft.jogg.Buffer;

class StaticCodeBook {
   int dim;
   int entries;
   int[] lengthlist;
   int maptype;
   int q_min;
   int q_delta;
   int q_quant;
   int q_sequencep;
   int[] quantlist;
   static final int VQ_FEXP = 10;
   static final int VQ_FMAN = 21;
   static final int VQ_FEXP_BIAS = 768;

   int pack(Buffer opb) {
      boolean ordered = false;
      opb.write(5653314, 24);
      opb.write(this.dim, 16);
      opb.write(this.entries, 24);
      int i = 1;

      while (i < this.entries && this.lengthlist[i] >= this.lengthlist[i - 1]) {
         i++;
      }

      if (i == this.entries) {
         ordered = true;
      }

      if (ordered) {
         int count = 0;
         opb.write(1, 1);
         opb.write(this.lengthlist[0] - 1, 5);

         for (i = 1; i < this.entries; i++) {
            int _this = this.lengthlist[i];
            int _last = this.lengthlist[i - 1];
            if (_this > _last) {
               for (int j = _last; j < _this; j++) {
                  opb.write(i - count, Util.ilog(this.entries - count));
                  count = i;
               }
            }
         }

         opb.write(i - count, Util.ilog(this.entries - count));
      } else {
         opb.write(0, 1);
         i = 0;

         while (i < this.entries && this.lengthlist[i] != 0) {
            i++;
         }

         if (i == this.entries) {
            opb.write(0, 1);

            for (int var10 = 0; var10 < this.entries; var10++) {
               opb.write(this.lengthlist[var10] - 1, 5);
            }
         } else {
            opb.write(1, 1);

            for (int var11 = 0; var11 < this.entries; var11++) {
               if (this.lengthlist[var11] == 0) {
                  opb.write(0, 1);
               } else {
                  opb.write(1, 1);
                  opb.write(this.lengthlist[var11] - 1, 5);
               }
            }
         }
      }

      opb.write(this.maptype, 4);
      switch (this.maptype) {
         case 1:
         case 2:
            if (this.quantlist == null) {
               return -1;
            } else {
               opb.write(this.q_min, 32);
               opb.write(this.q_delta, 32);
               opb.write(this.q_quant - 1, 4);
               opb.write(this.q_sequencep, 1);
               int quantvals = 0;
               switch (this.maptype) {
                  case 1:
                     quantvals = this.maptype1_quantvals();
                     break;
                  case 2:
                     quantvals = this.entries * this.dim;
               }

               for (int var12 = 0; var12 < quantvals; var12++) {
                  opb.write(Math.abs(this.quantlist[var12]), this.q_quant);
               }
            }
         case 0:
            return 0;
         default:
            return -1;
      }
   }

   int unpack(Buffer opb) {
      if (opb.read(24) != 5653314) {
         this.clear();
         return -1;
      } else {
         this.dim = opb.read(16);
         this.entries = opb.read(24);
         if (this.entries == -1) {
            this.clear();
            return -1;
         } else {
            switch (opb.read(1)) {
               case 0:
                  this.lengthlist = new int[this.entries];
                  if (opb.read(1) != 0) {
                     for (int i = 0; i < this.entries; i++) {
                        if (opb.read(1) != 0) {
                           int num = opb.read(5);
                           if (num == -1) {
                              this.clear();
                              return -1;
                           }

                           this.lengthlist[i] = num + 1;
                        } else {
                           this.lengthlist[i] = 0;
                        }
                     }
                  } else {
                     for (int ix = 0; ix < this.entries; ix++) {
                        int num = opb.read(5);
                        if (num == -1) {
                           this.clear();
                           return -1;
                        }

                        this.lengthlist[ix] = num + 1;
                     }
                  }
                  break;
               case 1:
                  int length = opb.read(5) + 1;
                  this.lengthlist = new int[this.entries];

                  for (int i = 0; i < this.entries; length++) {
                     int num = opb.read(Util.ilog(this.entries - i));
                     if (num == -1) {
                        this.clear();
                        return -1;
                     }

                     for (int j = 0; j < num; i++) {
                        this.lengthlist[i] = length;
                        j++;
                     }
                  }
                  break;
               default:
                  return -1;
            }

            switch (this.maptype = opb.read(4)) {
               case 1:
               case 2:
                  this.q_min = opb.read(32);
                  this.q_delta = opb.read(32);
                  this.q_quant = opb.read(4) + 1;
                  this.q_sequencep = opb.read(1);
                  int quantvals = 0;
                  switch (this.maptype) {
                     case 1:
                        quantvals = this.maptype1_quantvals();
                        break;
                     case 2:
                        quantvals = this.entries * this.dim;
                  }

                  this.quantlist = new int[quantvals];

                  for (int var8 = 0; var8 < quantvals; var8++) {
                     this.quantlist[var8] = opb.read(this.q_quant);
                  }

                  if (this.quantlist[quantvals - 1] == -1) {
                     this.clear();
                     return -1;
                  }
               case 0:
                  return 0;
               default:
                  this.clear();
                  return -1;
            }
         }
      }
   }

   private int maptype1_quantvals() {
      int vals = (int)Math.floor(Math.pow(this.entries, 1.0 / this.dim));

      while (true) {
         int acc = 1;
         int acc1 = 1;

         for (int i = 0; i < this.dim; i++) {
            acc *= vals;
            acc1 *= vals + 1;
         }

         if (acc <= this.entries && acc1 > this.entries) {
            return vals;
         }

         if (acc > this.entries) {
            vals--;
         } else {
            vals++;
         }
      }
   }

   void clear() {
   }

   float[] unquantize() {
      if (this.maptype != 1 && this.maptype != 2) {
         return null;
      } else {
         float mindel = float32_unpack(this.q_min);
         float delta = float32_unpack(this.q_delta);
         float[] r = new float[this.entries * this.dim];
         switch (this.maptype) {
            case 1:
               int quantvals = this.maptype1_quantvals();

               for (int j = 0; j < this.entries; j++) {
                  float last = 0.0F;
                  int indexdiv = 1;

                  for (int k = 0; k < this.dim; k++) {
                     int index = j / indexdiv % quantvals;
                     float val = this.quantlist[index];
                     val = Math.abs(val) * delta + mindel + last;
                     if (this.q_sequencep != 0) {
                        last = val;
                     }

                     r[j * this.dim + k] = val;
                     indexdiv *= quantvals;
                  }
               }
               break;
            case 2:
               for (int j = 0; j < this.entries; j++) {
                  float last = 0.0F;

                  for (int k = 0; k < this.dim; k++) {
                     float val = this.quantlist[j * this.dim + k];
                     val = Math.abs(val) * delta + mindel + last;
                     if (this.q_sequencep != 0) {
                        last = val;
                     }

                     r[j * this.dim + k] = val;
                  }
               }
         }

         return r;
      }
   }

   static long float32_pack(float val) {
      int sign = 0;
      if (val < 0.0F) {
         sign = Integer.MIN_VALUE;
         val = -val;
      }

      int exp = (int)Math.floor(Math.log(val) / Math.log(2.0));
      int mant = (int)Math.rint(Math.pow(val, 20 - exp));
      exp = exp + 768 << 21;
      return sign | exp | mant;
   }

   static float float32_unpack(int val) {
      float mant = val & 2097151;
      float exp = (val & 2145386496) >>> 21;
      if ((val & -2147483648) != 0) {
         mant = -mant;
      }

      return ldexp(mant, (int)exp - 20 - 768);
   }

   static float ldexp(float foo, int e) {
      return (float)(foo * Math.pow(2.0, e));
   }
}
