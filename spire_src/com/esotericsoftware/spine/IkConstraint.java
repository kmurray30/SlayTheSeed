package com.esotericsoftware.spine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

public class IkConstraint implements Updatable {
   final IkConstraintData data;
   final Array<Bone> bones;
   Bone target;
   float mix = 1.0F;
   int bendDirection;
   int level;

   public IkConstraint(IkConstraintData data, Skeleton skeleton) {
      if (data == null) {
         throw new IllegalArgumentException("data cannot be null.");
      } else if (skeleton == null) {
         throw new IllegalArgumentException("skeleton cannot be null.");
      } else {
         this.data = data;
         this.mix = data.mix;
         this.bendDirection = data.bendDirection;
         this.bones = new Array<>(data.bones.size);

         for (BoneData boneData : data.bones) {
            this.bones.add(skeleton.findBone(boneData.name));
         }

         this.target = skeleton.findBone(data.target.name);
      }
   }

   public IkConstraint(IkConstraint constraint, Skeleton skeleton) {
      if (constraint == null) {
         throw new IllegalArgumentException("constraint cannot be null.");
      } else if (skeleton == null) {
         throw new IllegalArgumentException("skeleton cannot be null.");
      } else {
         this.data = constraint.data;
         this.bones = new Array<>(constraint.bones.size);

         for (Bone bone : constraint.bones) {
            this.bones.add(skeleton.bones.get(bone.data.index));
         }

         this.target = skeleton.bones.get(constraint.target.data.index);
         this.mix = constraint.mix;
         this.bendDirection = constraint.bendDirection;
      }
   }

   public void apply() {
      this.update();
   }

   @Override
   public void update() {
      Bone target = this.target;
      Array<Bone> bones = this.bones;
      switch (bones.size) {
         case 1:
            apply(bones.first(), target.worldX, target.worldY, this.mix);
            break;
         case 2:
            apply(bones.first(), bones.get(1), target.worldX, target.worldY, this.bendDirection, this.mix);
      }
   }

   public Array<Bone> getBones() {
      return this.bones;
   }

   public Bone getTarget() {
      return this.target;
   }

   public void setTarget(Bone target) {
      this.target = target;
   }

   public float getMix() {
      return this.mix;
   }

   public void setMix(float mix) {
      this.mix = mix;
   }

   public int getBendDirection() {
      return this.bendDirection;
   }

   public void setBendDirection(int bendDirection) {
      this.bendDirection = bendDirection;
   }

   public IkConstraintData getData() {
      return this.data;
   }

   @Override
   public String toString() {
      return this.data.name;
   }

   public static void apply(Bone bone, float targetX, float targetY, float alpha) {
      Bone pp = bone.parent;
      float id = 1.0F / (pp.a * pp.d - pp.b * pp.c);
      float x = targetX - pp.worldX;
      float y = targetY - pp.worldY;
      float tx = (x * pp.d - y * pp.b) * id - bone.x;
      float ty = (y * pp.a - x * pp.c) * id - bone.y;
      float rotationIK = MathUtils.atan2(ty, tx) * (180.0F / (float)Math.PI) - bone.shearX - bone.rotation;
      if (bone.scaleX < 0.0F) {
         rotationIK += 180.0F;
      }

      if (rotationIK > 180.0F) {
         rotationIK -= 360.0F;
      } else if (rotationIK < -180.0F) {
         rotationIK += 360.0F;
      }

      bone.updateWorldTransform(bone.x, bone.y, bone.rotation + rotationIK * alpha, bone.scaleX, bone.scaleY, bone.shearX, bone.shearY);
   }

   public static void apply(Bone parent, Bone child, float targetX, float targetY, int bendDir, float alpha) {
      if (alpha == 0.0F) {
         child.updateWorldTransform();
      } else {
         float px = parent.x;
         float py = parent.y;
         float psx = parent.scaleX;
         float psy = parent.scaleY;
         float csx = child.scaleX;
         int os1;
         int s2;
         if (psx < 0.0F) {
            psx = -psx;
            os1 = 180;
            s2 = -1;
         } else {
            os1 = 0;
            s2 = 1;
         }

         if (psy < 0.0F) {
            psy = -psy;
            s2 = -s2;
         }

         int os2;
         if (csx < 0.0F) {
            csx = -csx;
            os2 = 180;
         } else {
            os2 = 0;
         }

         float cx = child.x;
         float a = parent.a;
         float b = parent.b;
         float c = parent.c;
         float d = parent.d;
         boolean u = Math.abs(psx - psy) <= 1.0E-4F;
         float cy;
         float cwx;
         float cwy;
         if (!u) {
            cy = 0.0F;
            cwx = a * cx + parent.worldX;
            cwy = c * cx + parent.worldY;
         } else {
            cy = child.y;
            cwx = a * cx + b * cy + parent.worldX;
            cwy = c * cx + d * cy + parent.worldY;
         }

         Bone pp = parent.parent;
         a = pp.a;
         b = pp.b;
         c = pp.c;
         d = pp.d;
         float id = 1.0F / (a * d - b * c);
         float x = targetX - pp.worldX;
         float y = targetY - pp.worldY;
         float tx = (x * d - y * b) * id - px;
         float ty = (y * a - x * c) * id - py;
         x = cwx - pp.worldX;
         y = cwy - pp.worldY;
         float dx = (x * d - y * b) * id - px;
         float dy = (y * a - x * c) * id - py;
         float l1 = (float)Math.sqrt(dx * dx + dy * dy);
         float l2 = child.data.length * csx;
         float a1;
         float a2;
         if (u) {
            l2 *= psx;
            float cos = (tx * tx + ty * ty - l1 * l1 - l2 * l2) / (2.0F * l1 * l2);
            if (cos < -1.0F) {
               cos = -1.0F;
            } else if (cos > 1.0F) {
               cos = 1.0F;
            }

            a2 = (float)Math.acos(cos) * bendDir;
            a = l1 + l2 * cos;
            b = l2 * MathUtils.sin(a2);
            a1 = MathUtils.atan2(ty * a - tx * b, tx * a + ty * b);
         } else {
            label95: {
               a = psx * l2;
               b = psy * l2;
               float aa = a * a;
               float bb = b * b;
               float dd = tx * tx + ty * ty;
               float ta = MathUtils.atan2(ty, tx);
               c = bb * l1 * l1 + aa * dd - aa * bb;
               float c1 = -2.0F * bb * l1;
               float c2 = bb - aa;
               d = c1 * c1 - 4.0F * c2 * c;
               if (d >= 0.0F) {
                  float q = (float)Math.sqrt(d);
                  if (c1 < 0.0F) {
                     q = -q;
                  }

                  q = -(c1 + q) / 2.0F;
                  float r0 = q / c2;
                  float r1 = c / q;
                  float r = Math.abs(r0) < Math.abs(r1) ? r0 : r1;
                  if (r * r <= dd) {
                     y = (float)Math.sqrt(dd - r * r) * bendDir;
                     a1 = ta - MathUtils.atan2(y, r);
                     a2 = MathUtils.atan2(y / psy, (r - l1) / psx);
                     break label95;
                  }
               }

               float minAngle = 0.0F;
               float minDist = Float.MAX_VALUE;
               float minX = 0.0F;
               float minY = 0.0F;
               float maxAngle = 0.0F;
               float maxDist = 0.0F;
               float maxX = 0.0F;
               float maxY = 0.0F;
               x = l1 + a;
               d = x * x;
               if (d > maxDist) {
                  maxAngle = 0.0F;
                  maxDist = d;
                  maxX = x;
               }

               x = l1 - a;
               d = x * x;
               if (d < minDist) {
                  minAngle = (float) Math.PI;
                  minDist = d;
                  minX = x;
               }

               float angle = (float)Math.acos(-a * l1 / (aa - bb));
               x = a * MathUtils.cos(angle) + l1;
               y = b * MathUtils.sin(angle);
               d = x * x + y * y;
               if (d < minDist) {
                  minAngle = angle;
                  minDist = d;
                  minX = x;
                  minY = y;
               }

               if (d > maxDist) {
                  maxAngle = angle;
                  maxDist = d;
                  maxX = x;
                  maxY = y;
               }

               if (dd <= (minDist + maxDist) / 2.0F) {
                  a1 = ta - MathUtils.atan2(minY * bendDir, minX);
                  a2 = minAngle * bendDir;
               } else {
                  a1 = ta - MathUtils.atan2(maxY * bendDir, maxX);
                  a2 = maxAngle * bendDir;
               }
            }
         }

         float os = MathUtils.atan2(cy, cx) * s2;
         float rotation = parent.rotation;
         a1 = (a1 - os) * (180.0F / (float)Math.PI) + os1 - rotation;
         if (a1 > 180.0F) {
            a1 -= 360.0F;
         } else if (a1 < -180.0F) {
            a1 += 360.0F;
         }

         parent.updateWorldTransform(px, py, rotation + a1 * alpha, parent.scaleX, parent.scaleY, 0.0F, 0.0F);
         rotation = child.rotation;
         a2 = ((a2 + os) * (180.0F / (float)Math.PI) - child.shearX) * s2 + os2 - rotation;
         if (a2 > 180.0F) {
            a2 -= 360.0F;
         } else if (a2 < -180.0F) {
            a2 += 360.0F;
         }

         child.updateWorldTransform(cx, cy, rotation + a2 * alpha, child.scaleX, child.scaleY, child.shearX, child.shearY);
      }
   }
}
