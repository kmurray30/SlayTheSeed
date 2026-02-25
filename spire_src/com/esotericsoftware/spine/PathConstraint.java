package com.esotericsoftware.spine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.PathAttachment;

public class PathConstraint implements Updatable {
   private static final int NONE = -1;
   private static final int BEFORE = -2;
   private static final int AFTER = -3;
   final PathConstraintData data;
   final Array<Bone> bones;
   Slot target;
   float position;
   float spacing;
   float rotateMix;
   float translateMix;
   private final FloatArray spaces = new FloatArray();
   private final FloatArray positions = new FloatArray();
   private final FloatArray world = new FloatArray();
   private final FloatArray curves = new FloatArray();
   private final FloatArray lengths = new FloatArray();
   private final float[] segments = new float[10];

   public PathConstraint(PathConstraintData data, Skeleton skeleton) {
      if (data == null) {
         throw new IllegalArgumentException("data cannot be null.");
      } else if (skeleton == null) {
         throw new IllegalArgumentException("skeleton cannot be null.");
      } else {
         this.data = data;
         this.bones = new Array<>(data.bones.size);

         for (BoneData boneData : data.bones) {
            this.bones.add(skeleton.findBone(boneData.name));
         }

         this.target = skeleton.findSlot(data.target.name);
         this.position = data.position;
         this.spacing = data.spacing;
         this.rotateMix = data.rotateMix;
         this.translateMix = data.translateMix;
      }
   }

   public PathConstraint(PathConstraint constraint, Skeleton skeleton) {
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

         this.target = skeleton.slots.get(constraint.target.data.index);
         this.position = constraint.position;
         this.spacing = constraint.spacing;
         this.rotateMix = constraint.rotateMix;
         this.translateMix = constraint.translateMix;
      }
   }

   public void apply() {
      this.update();
   }

   @Override
   public void update() {
      Attachment attachment = this.target.attachment;
      if (attachment instanceof PathAttachment) {
         float rotateMix = this.rotateMix;
         float translateMix = this.translateMix;
         boolean translate = translateMix > 0.0F;
         boolean rotate = rotateMix > 0.0F;
         if (translate || rotate) {
            PathConstraintData data = this.data;
            PathConstraintData.SpacingMode spacingMode = data.spacingMode;
            boolean lengthSpacing = spacingMode == PathConstraintData.SpacingMode.length;
            PathConstraintData.RotateMode rotateMode = data.rotateMode;
            boolean tangents = rotateMode == PathConstraintData.RotateMode.tangent;
            boolean scale = rotateMode == PathConstraintData.RotateMode.chainScale;
            int boneCount = this.bones.size;
            int spacesCount = tangents ? boneCount : boneCount + 1;
            Object[] bones = this.bones.items;
            float[] spaces = this.spaces.setSize(spacesCount);
            float[] lengths = null;
            float spacing = this.spacing;
            if (!scale && !lengthSpacing) {
               for (int i = 1; i < spacesCount; i++) {
                  spaces[i] = spacing;
               }
            } else {
               if (scale) {
                  lengths = this.lengths.setSize(boneCount);
               }

               int i = 0;
               int n = spacesCount - 1;

               while (i < n) {
                  Bone bone = (Bone)bones[i];
                  float length = bone.data.length;
                  float x = length * bone.a;
                  float y = length * bone.c;
                  length = (float)Math.sqrt(x * x + y * y);
                  if (scale) {
                     lengths[i] = length;
                  }

                  spaces[++i] = lengthSpacing ? Math.max(0.0F, length + spacing) : spacing;
               }
            }

            float[] positions = this.computeWorldPositions(
               (PathAttachment)attachment,
               spacesCount,
               tangents,
               data.positionMode == PathConstraintData.PositionMode.percent,
               spacingMode == PathConstraintData.SpacingMode.percent
            );
            Skeleton skeleton = this.target.getSkeleton();
            float skeletonX = skeleton.x;
            float skeletonY = skeleton.y;
            float boneX = positions[0];
            float boneY = positions[1];
            float offsetRotation = data.offsetRotation;
            boolean tip = rotateMode == PathConstraintData.RotateMode.chain && offsetRotation == 0.0F;
            int i = 0;

            for (int p = 3; i < boneCount; p += 3) {
               Bone bone = (Bone)bones[i];
               bone.worldX = bone.worldX + (boneX - skeletonX - bone.worldX) * translateMix;
               bone.worldY = bone.worldY + (boneY - skeletonY - bone.worldY) * translateMix;
               float x = positions[p];
               float y = positions[p + 1];
               float dx = x - boneX;
               float dy = y - boneY;
               if (scale) {
                  float length = lengths[i];
                  if (length != 0.0F) {
                     float s = ((float)Math.sqrt(dx * dx + dy * dy) / length - 1.0F) * rotateMix + 1.0F;
                     bone.a *= s;
                     bone.c *= s;
                  }
               }

               boneX = x;
               boneY = y;
               if (rotate) {
                  float a = bone.a;
                  float b = bone.b;
                  float c = bone.c;
                  float d = bone.d;
                  float r;
                  if (tangents) {
                     r = positions[p - 1];
                  } else if (spaces[i + 1] == 0.0F) {
                     r = positions[p + 2];
                  } else {
                     r = MathUtils.atan2(dy, dx);
                  }

                  r -= MathUtils.atan2(c, a) - offsetRotation * (float) (Math.PI / 180.0);
                  if (tip) {
                     float cos = MathUtils.cos(r);
                     float sin = MathUtils.sin(r);
                     float length = bone.data.length;
                     boneX = x + (length * (cos * a - sin * c) - dx) * rotateMix;
                     boneY = y + (length * (sin * a + cos * c) - dy) * rotateMix;
                  }

                  if (r > (float) Math.PI) {
                     r -= (float) (Math.PI * 2);
                  } else if (r < (float) -Math.PI) {
                     r += (float) (Math.PI * 2);
                  }

                  r *= rotateMix;
                  float cos = MathUtils.cos(r);
                  float sin = MathUtils.sin(r);
                  bone.a = cos * a - sin * c;
                  bone.b = cos * b - sin * d;
                  bone.c = sin * a + cos * c;
                  bone.d = sin * b + cos * d;
               }

               i++;
            }
         }
      }
   }

   float[] computeWorldPositions(PathAttachment path, int spacesCount, boolean tangents, boolean percentPosition, boolean percentSpacing) {
      Slot target = this.target;
      float position = this.position;
      float[] spaces = this.spaces.items;
      float[] out = this.positions.setSize(spacesCount * 3 + 2);
      boolean closed = path.getClosed();
      int verticesLength = path.getWorldVerticesLength();
      int curveCount = verticesLength / 6;
      int prevCurve = -1;
      if (!path.getConstantSpeed()) {
         float[] lengths = path.getLengths();
         curveCount -= closed ? 1 : 2;
         float pathLength = lengths[curveCount];
         if (percentPosition) {
            position *= pathLength;
         }

         if (percentSpacing) {
            for (int i = 0; i < spacesCount; i++) {
               spaces[i] *= pathLength;
            }
         }

         float[] world = this.world.setSize(8);
         int i = 0;
         int o = 0;

         for (int curve = 0; i < spacesCount; o += 3) {
            label224: {
               float space = spaces[i];
               position += space;
               float p = position;
               if (closed) {
                  p = position % pathLength;
                  if (p < 0.0F) {
                     p += pathLength;
                  }

                  curve = 0;
               } else {
                  if (position < 0.0F) {
                     if (prevCurve != -2) {
                        prevCurve = -2;
                        path.computeWorldVertices(target, 2, 4, world, 0);
                     }

                     this.addBeforePosition(position, world, 0, out, o);
                     break label224;
                  }

                  if (position > pathLength) {
                     if (prevCurve != -3) {
                        prevCurve = -3;
                        path.computeWorldVertices(target, verticesLength - 6, 4, world, 0);
                     }

                     this.addAfterPosition(position - pathLength, world, 0, out, o);
                     break label224;
                  }
               }

               while (true) {
                  float length = lengths[curve];
                  if (!(p > length)) {
                     if (curve == 0) {
                        p /= length;
                     } else {
                        float prev = lengths[curve - 1];
                        p = (p - prev) / (length - prev);
                     }

                     if (curve != prevCurve) {
                        prevCurve = curve;
                        if (closed && curve == curveCount) {
                           path.computeWorldVertices(target, verticesLength - 4, 4, world, 0);
                           path.computeWorldVertices(target, 0, 4, world, 4);
                        } else {
                           path.computeWorldVertices(target, curve * 6 + 2, 8, world, 0);
                        }
                     }

                     this.addCurvePosition(
                        p, world[0], world[1], world[2], world[3], world[4], world[5], world[6], world[7], out, o, tangents || i > 0 && space == 0.0F
                     );
                     break;
                  }

                  curve++;
               }
            }

            i++;
         }

         return out;
      } else {
         float[] world;
         if (closed) {
            verticesLength += 2;
            world = this.world.setSize(verticesLength);
            path.computeWorldVertices(target, 2, verticesLength - 4, world, 0);
            path.computeWorldVertices(target, 0, 2, world, verticesLength - 4);
            world[verticesLength - 2] = world[0];
            world[verticesLength - 1] = world[1];
         } else {
            curveCount--;
            verticesLength -= 4;
            world = this.world.setSize(verticesLength);
            path.computeWorldVertices(target, 2, verticesLength, world, 0);
         }

         float[] curves = this.curves.setSize(curveCount);
         float pathLengthx = 0.0F;
         float x1 = world[0];
         float y1 = world[1];
         float cx1 = 0.0F;
         float cy1 = 0.0F;
         float cx2 = 0.0F;
         float cy2 = 0.0F;
         float x2 = 0.0F;
         float y2 = 0.0F;
         int i = 0;

         for (int w = 2; i < curveCount; w += 6) {
            cx1 = world[w];
            cy1 = world[w + 1];
            cx2 = world[w + 2];
            cy2 = world[w + 3];
            x2 = world[w + 4];
            y2 = world[w + 5];
            float tmpx = (x1 - cx1 * 2.0F + cx2) * 0.1875F;
            float tmpy = (y1 - cy1 * 2.0F + cy2) * 0.1875F;
            float dddfx = ((cx1 - cx2) * 3.0F - x1 + x2) * 0.09375F;
            float dddfy = ((cy1 - cy2) * 3.0F - y1 + y2) * 0.09375F;
            float ddfx = tmpx * 2.0F + dddfx;
            float ddfy = tmpy * 2.0F + dddfy;
            float dfx = (cx1 - x1) * 0.75F + tmpx + dddfx * 0.16666667F;
            float dfy = (cy1 - y1) * 0.75F + tmpy + dddfy * 0.16666667F;
            float var47 = pathLengthx + (float)Math.sqrt(dfx * dfx + dfy * dfy);
            dfx += ddfx;
            dfy += ddfy;
            ddfx += dddfx;
            ddfy += dddfy;
            float var48 = var47 + (float)Math.sqrt(dfx * dfx + dfy * dfy);
            dfx += ddfx;
            dfy += ddfy;
            float var49 = var48 + (float)Math.sqrt(dfx * dfx + dfy * dfy);
            dfx += ddfx + dddfx;
            dfy += ddfy + dddfy;
            pathLengthx = var49 + (float)Math.sqrt(dfx * dfx + dfy * dfy);
            curves[i] = pathLengthx;
            x1 = x2;
            y1 = y2;
            i++;
         }

         if (percentPosition) {
            position *= pathLengthx;
         }

         if (percentSpacing) {
            for (int ix = 0; ix < spacesCount; ix++) {
               spaces[ix] *= pathLengthx;
            }
         }

         float[] segments = this.segments;
         float curveLength = 0.0F;
         int ix = 0;
         int o = 0;
         int curve = 0;

         for (int segment = 0; ix < spacesCount; o += 3) {
            label225: {
               float space = spaces[ix];
               position += space;
               float p = position;
               if (closed) {
                  p = position % pathLengthx;
                  if (p < 0.0F) {
                     p += pathLengthx;
                  }

                  curve = 0;
               } else {
                  if (position < 0.0F) {
                     this.addBeforePosition(position, world, 0, out, o);
                     break label225;
                  }

                  if (position > pathLengthx) {
                     this.addAfterPosition(position - pathLengthx, world, verticesLength - 4, out, o);
                     break label225;
                  }
               }

               label189:
               while (true) {
                  float length = curves[curve];
                  if (!(p > length)) {
                     if (curve == 0) {
                        p /= length;
                     } else {
                        float prev = curves[curve - 1];
                        p = (p - prev) / (length - prev);
                     }

                     if (curve != prevCurve) {
                        prevCurve = curve;
                        int ii = curve * 6;
                        x1 = world[ii];
                        y1 = world[ii + 1];
                        cx1 = world[ii + 2];
                        cy1 = world[ii + 3];
                        cx2 = world[ii + 4];
                        cy2 = world[ii + 5];
                        x2 = world[ii + 6];
                        y2 = world[ii + 7];
                        float tmpx = (x1 - cx1 * 2.0F + cx2) * 0.03F;
                        float tmpy = (y1 - cy1 * 2.0F + cy2) * 0.03F;
                        float dddfx = ((cx1 - cx2) * 3.0F - x1 + x2) * 0.006F;
                        float dddfy = ((cy1 - cy2) * 3.0F - y1 + y2) * 0.006F;
                        float ddfx = tmpx * 2.0F + dddfx;
                        float ddfy = tmpy * 2.0F + dddfy;
                        float dfx = (cx1 - x1) * 0.3F + tmpx + dddfx * 0.16666667F;
                        float dfy = (cy1 - y1) * 0.3F + tmpy + dddfy * 0.16666667F;
                        curveLength = (float)Math.sqrt(dfx * dfx + dfy * dfy);
                        segments[0] = curveLength;

                        for (int var89 = 1; var89 < 8; var89++) {
                           dfx += ddfx;
                           dfy += ddfy;
                           ddfx += dddfx;
                           ddfy += dddfy;
                           curveLength += (float)Math.sqrt(dfx * dfx + dfy * dfy);
                           segments[var89] = curveLength;
                        }

                        dfx += ddfx;
                        dfy += ddfy;
                        float var84 = curveLength + (float)Math.sqrt(dfx * dfx + dfy * dfy);
                        segments[8] = var84;
                        dfx += ddfx + dddfx;
                        dfy += ddfy + dddfy;
                        curveLength = var84 + (float)Math.sqrt(dfx * dfx + dfy * dfy);
                        segments[9] = curveLength;
                        segment = 0;
                     }

                     p *= curveLength;

                     while (true) {
                        length = segments[segment];
                        if (!(p > length)) {
                           if (segment == 0) {
                              p /= length;
                           } else {
                              float prev = segments[segment - 1];
                              p = segment + (p - prev) / (length - prev);
                           }

                           this.addCurvePosition(p * 0.1F, x1, y1, cx1, cy1, cx2, cy2, x2, y2, out, o, tangents || ix > 0 && space == 0.0F);
                           break label189;
                        }

                        segment++;
                     }
                  }

                  curve++;
               }
            }

            ix++;
         }

         return out;
      }
   }

   private void addBeforePosition(float p, float[] temp, int i, float[] out, int o) {
      float x1 = temp[i];
      float y1 = temp[i + 1];
      float dx = temp[i + 2] - x1;
      float dy = temp[i + 3] - y1;
      float r = MathUtils.atan2(dy, dx);
      out[o] = x1 + p * MathUtils.cos(r);
      out[o + 1] = y1 + p * MathUtils.sin(r);
      out[o + 2] = r;
   }

   private void addAfterPosition(float p, float[] temp, int i, float[] out, int o) {
      float x1 = temp[i + 2];
      float y1 = temp[i + 3];
      float dx = x1 - temp[i];
      float dy = y1 - temp[i + 1];
      float r = MathUtils.atan2(dy, dx);
      out[o] = x1 + p * MathUtils.cos(r);
      out[o + 1] = y1 + p * MathUtils.sin(r);
      out[o + 2] = r;
   }

   private void addCurvePosition(
      float p, float x1, float y1, float cx1, float cy1, float cx2, float cy2, float x2, float y2, float[] out, int o, boolean tangents
   ) {
      if (p == 0.0F) {
         p = 1.0E-4F;
      }

      float tt = p * p;
      float ttt = tt * p;
      float u = 1.0F - p;
      float uu = u * u;
      float uuu = uu * u;
      float ut = u * p;
      float ut3 = ut * 3.0F;
      float uut3 = u * ut3;
      float utt3 = ut3 * p;
      float x = x1 * uuu + cx1 * uut3 + cx2 * utt3 + x2 * ttt;
      float y = y1 * uuu + cy1 * uut3 + cy2 * utt3 + y2 * ttt;
      out[o] = x;
      out[o + 1] = y;
      if (tangents) {
         out[o + 2] = MathUtils.atan2(y - (y1 * uu + cy1 * ut * 2.0F + cy2 * tt), x - (x1 * uu + cx1 * ut * 2.0F + cx2 * tt));
      }
   }

   public float getPosition() {
      return this.position;
   }

   public void setPosition(float position) {
      this.position = position;
   }

   public float getSpacing() {
      return this.spacing;
   }

   public void setSpacing(float spacing) {
      this.spacing = spacing;
   }

   public float getRotateMix() {
      return this.rotateMix;
   }

   public void setRotateMix(float rotateMix) {
      this.rotateMix = rotateMix;
   }

   public float getTranslateMix() {
      return this.translateMix;
   }

   public void setTranslateMix(float translateMix) {
      this.translateMix = translateMix;
   }

   public Array<Bone> getBones() {
      return this.bones;
   }

   public Slot getTarget() {
      return this.target;
   }

   public void setTarget(Slot target) {
      this.target = target;
   }

   public PathConstraintData getData() {
      return this.data;
   }

   @Override
   public String toString() {
      return this.data.name;
   }
}
