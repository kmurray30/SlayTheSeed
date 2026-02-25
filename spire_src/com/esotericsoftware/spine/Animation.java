package com.esotericsoftware.spine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.VertexAttachment;

public class Animation {
   final String name;
   private final Array<Animation.Timeline> timelines;
   private float duration;

   public Animation(String name, Array<Animation.Timeline> timelines, float duration) {
      if (name == null) {
         throw new IllegalArgumentException("name cannot be null.");
      } else if (timelines == null) {
         throw new IllegalArgumentException("timelines cannot be null.");
      } else {
         this.name = name;
         this.timelines = timelines;
         this.duration = duration;
      }
   }

   public Array<Animation.Timeline> getTimelines() {
      return this.timelines;
   }

   public float getDuration() {
      return this.duration;
   }

   public void setDuration(float duration) {
      this.duration = duration;
   }

   public void apply(Skeleton skeleton, float lastTime, float time, boolean loop, Array<Event> events) {
      if (skeleton == null) {
         throw new IllegalArgumentException("skeleton cannot be null.");
      } else {
         if (loop && this.duration != 0.0F) {
            time %= this.duration;
            if (lastTime > 0.0F) {
               lastTime %= this.duration;
            }
         }

         Array<Animation.Timeline> timelines = this.timelines;
         int i = 0;

         for (int n = timelines.size; i < n; i++) {
            timelines.get(i).apply(skeleton, lastTime, time, events, 1.0F);
         }
      }
   }

   public void mix(Skeleton skeleton, float lastTime, float time, boolean loop, Array<Event> events, float alpha) {
      if (skeleton == null) {
         throw new IllegalArgumentException("skeleton cannot be null.");
      } else {
         if (loop && this.duration != 0.0F) {
            time %= this.duration;
            if (lastTime > 0.0F) {
               lastTime %= this.duration;
            }
         }

         Array<Animation.Timeline> timelines = this.timelines;
         int i = 0;

         for (int n = timelines.size; i < n; i++) {
            timelines.get(i).apply(skeleton, lastTime, time, events, alpha);
         }
      }
   }

   public String getName() {
      return this.name;
   }

   @Override
   public String toString() {
      return this.name;
   }

   static int binarySearch(float[] values, float target, int step) {
      int low = 0;
      int high = values.length / step - 2;
      if (high == 0) {
         return step;
      } else {
         int current = high >>> 1;

         while (true) {
            if (values[(current + 1) * step] <= target) {
               low = current + 1;
            } else {
               high = current;
            }

            if (low == high) {
               return (low + 1) * step;
            }

            current = low + high >>> 1;
         }
      }
   }

   static int binarySearch(float[] values, float target) {
      int low = 0;
      int high = values.length - 2;
      if (high == 0) {
         return 1;
      } else {
         int current = high >>> 1;

         while (true) {
            if (values[current + 1] <= target) {
               low = current + 1;
            } else {
               high = current;
            }

            if (low == high) {
               return low + 1;
            }

            current = low + high >>> 1;
         }
      }
   }

   static int linearSearch(float[] values, float target, int step) {
      int i = 0;

      for (int last = values.length - step; i <= last; i += step) {
         if (values[i] > target) {
            return i;
         }
      }

      return -1;
   }

   public static class AttachmentTimeline implements Animation.Timeline {
      int slotIndex;
      final float[] frames;
      final String[] attachmentNames;

      public AttachmentTimeline(int frameCount) {
         this.frames = new float[frameCount];
         this.attachmentNames = new String[frameCount];
      }

      public int getFrameCount() {
         return this.frames.length;
      }

      public void setSlotIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.slotIndex = index;
         }
      }

      public int getSlotIndex() {
         return this.slotIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public String[] getAttachmentNames() {
         return this.attachmentNames;
      }

      public void setFrame(int frameIndex, float time, String attachmentName) {
         this.frames[frameIndex] = time;
         this.attachmentNames[frameIndex] = attachmentName;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            int frameIndex;
            if (time >= frames[frames.length - 1]) {
               frameIndex = frames.length - 1;
            } else {
               frameIndex = Animation.binarySearch(frames, time, 1) - 1;
            }

            String attachmentName = this.attachmentNames[frameIndex];
            skeleton.slots.get(this.slotIndex).setAttachment(attachmentName == null ? null : skeleton.getAttachment(this.slotIndex, attachmentName));
         }
      }
   }

   public static class ColorTimeline extends Animation.CurveTimeline {
      public static final int ENTRIES = 5;
      private static final int PREV_TIME = -5;
      private static final int PREV_R = -4;
      private static final int PREV_G = -3;
      private static final int PREV_B = -2;
      private static final int PREV_A = -1;
      private static final int R = 1;
      private static final int G = 2;
      private static final int B = 3;
      private static final int A = 4;
      int slotIndex;
      private final float[] frames;

      public ColorTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount * 5];
      }

      public void setSlotIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.slotIndex = index;
         }
      }

      public int getSlotIndex() {
         return this.slotIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public void setFrame(int frameIndex, float time, float r, float g, float b, float a) {
         frameIndex *= 5;
         this.frames[frameIndex] = time;
         this.frames[frameIndex + 1] = r;
         this.frames[frameIndex + 2] = g;
         this.frames[frameIndex + 3] = b;
         this.frames[frameIndex + 4] = a;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            float r;
            float g;
            float b;
            float a;
            if (time >= frames[frames.length - 5]) {
               int i = frames.length;
               r = frames[i + -4];
               g = frames[i + -3];
               b = frames[i + -2];
               a = frames[i + -1];
            } else {
               int frame = Animation.binarySearch(frames, time, 5);
               r = frames[frame + -4];
               g = frames[frame + -3];
               b = frames[frame + -2];
               a = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 5 - 1, 1.0F - (time - frameTime) / (frames[frame + -5] - frameTime));
               r += (frames[frame + 1] - r) * percent;
               g += (frames[frame + 2] - g) * percent;
               b += (frames[frame + 3] - b) * percent;
               a += (frames[frame + 4] - a) * percent;
            }

            Color color = skeleton.slots.get(this.slotIndex).color;
            if (alpha < 1.0F) {
               color.add((r - color.r) * alpha, (g - color.g) * alpha, (b - color.b) * alpha, (a - color.a) * alpha);
            } else {
               color.set(r, g, b, a);
            }
         }
      }
   }

   public abstract static class CurveTimeline implements Animation.Timeline {
      public static final float LINEAR = 0.0F;
      public static final float STEPPED = 1.0F;
      public static final float BEZIER = 2.0F;
      private static final int BEZIER_SIZE = 19;
      private final float[] curves;

      public CurveTimeline(int frameCount) {
         if (frameCount <= 0) {
            throw new IllegalArgumentException("frameCount must be > 0: " + frameCount);
         } else {
            this.curves = new float[(frameCount - 1) * 19];
         }
      }

      public int getFrameCount() {
         return this.curves.length / 19 + 1;
      }

      public void setLinear(int frameIndex) {
         this.curves[frameIndex * 19] = 0.0F;
      }

      public void setStepped(int frameIndex) {
         this.curves[frameIndex * 19] = 1.0F;
      }

      public float getCurveType(int frameIndex) {
         int index = frameIndex * 19;
         if (index == this.curves.length) {
            return 0.0F;
         } else {
            float type = this.curves[index];
            if (type == 0.0F) {
               return 0.0F;
            } else {
               return type == 1.0F ? 1.0F : 2.0F;
            }
         }
      }

      public void setCurve(int frameIndex, float cx1, float cy1, float cx2, float cy2) {
         float tmpx = (-cx1 * 2.0F + cx2) * 0.03F;
         float tmpy = (-cy1 * 2.0F + cy2) * 0.03F;
         float dddfx = ((cx1 - cx2) * 3.0F + 1.0F) * 0.006F;
         float dddfy = ((cy1 - cy2) * 3.0F + 1.0F) * 0.006F;
         float ddfx = tmpx * 2.0F + dddfx;
         float ddfy = tmpy * 2.0F + dddfy;
         float dfx = cx1 * 0.3F + tmpx + dddfx * 0.16666667F;
         float dfy = cy1 * 0.3F + tmpy + dddfy * 0.16666667F;
         int i = frameIndex * 19;
         float[] curves = this.curves;
         curves[i++] = 2.0F;
         float x = dfx;
         float y = dfy;

         for (int n = i + 19 - 1; i < n; i += 2) {
            curves[i] = x;
            curves[i + 1] = y;
            dfx += ddfx;
            dfy += ddfy;
            ddfx += dddfx;
            ddfy += dddfy;
            x += dfx;
            y += dfy;
         }
      }

      public float getCurvePercent(int frameIndex, float percent) {
         percent = MathUtils.clamp(percent, 0.0F, 1.0F);
         float[] curves = this.curves;
         int i = frameIndex * 19;
         float type = curves[i];
         if (type == 0.0F) {
            return percent;
         } else if (type == 1.0F) {
            return 0.0F;
         } else {
            i++;
            float x = 0.0F;
            int start = i;

            for (int n = i + 19 - 1; i < n; i += 2) {
               x = curves[i];
               if (x >= percent) {
                  float prevX;
                  float prevY;
                  if (i == start) {
                     prevX = 0.0F;
                     prevY = 0.0F;
                  } else {
                     prevX = curves[i - 2];
                     prevY = curves[i - 1];
                  }

                  return prevY + (curves[i + 1] - prevY) * (percent - prevX) / (x - prevX);
               }
            }

            float y = curves[i - 1];
            return y + (1.0F - y) * (percent - x) / (1.0F - x);
         }
      }
   }

   public static class DeformTimeline extends Animation.CurveTimeline {
      private final float[] frames;
      private final float[][] frameVertices;
      int slotIndex;
      VertexAttachment attachment;

      public DeformTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount];
         this.frameVertices = new float[frameCount][];
      }

      public void setSlotIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.slotIndex = index;
         }
      }

      public int getSlotIndex() {
         return this.slotIndex;
      }

      public void setAttachment(VertexAttachment attachment) {
         this.attachment = attachment;
      }

      public Attachment getAttachment() {
         return this.attachment;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public float[][] getVertices() {
         return this.frameVertices;
      }

      public void setFrame(int frameIndex, float time, float[] vertices) {
         this.frames[frameIndex] = time;
         this.frameVertices[frameIndex] = vertices;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> firedEvents, float alpha) {
         Slot slot = skeleton.slots.get(this.slotIndex);
         Attachment slotAttachment = slot.attachment;
         if (slotAttachment instanceof VertexAttachment && ((VertexAttachment)slotAttachment).applyDeform(this.attachment)) {
            float[] frames = this.frames;
            if (!(time < frames[0])) {
               float[][] frameVertices = this.frameVertices;
               int vertexCount = frameVertices[0].length;
               FloatArray verticesArray = slot.getAttachmentVertices();
               if (verticesArray.size != vertexCount) {
                  alpha = 1.0F;
               }

               float[] vertices = verticesArray.setSize(vertexCount);
               if (time >= frames[frames.length - 1]) {
                  float[] lastVertices = frameVertices[frames.length - 1];
                  if (alpha < 1.0F) {
                     for (int i = 0; i < vertexCount; i++) {
                        vertices[i] += (lastVertices[i] - vertices[i]) * alpha;
                     }
                  } else {
                     System.arraycopy(lastVertices, 0, vertices, 0, vertexCount);
                  }
               } else {
                  int frame = Animation.binarySearch(frames, time);
                  float[] prevVertices = frameVertices[frame - 1];
                  float[] nextVertices = frameVertices[frame];
                  float frameTime = frames[frame];
                  float percent = this.getCurvePercent(frame - 1, 1.0F - (time - frameTime) / (frames[frame - 1] - frameTime));
                  if (alpha < 1.0F) {
                     for (int i = 0; i < vertexCount; i++) {
                        float prev = prevVertices[i];
                        vertices[i] += (prev + (nextVertices[i] - prev) * percent - vertices[i]) * alpha;
                     }
                  } else {
                     for (int i = 0; i < vertexCount; i++) {
                        float prev = prevVertices[i];
                        vertices[i] = prev + (nextVertices[i] - prev) * percent;
                     }
                  }
               }
            }
         }
      }
   }

   public static class DrawOrderTimeline implements Animation.Timeline {
      private final float[] frames;
      private final int[][] drawOrders;

      public DrawOrderTimeline(int frameCount) {
         this.frames = new float[frameCount];
         this.drawOrders = new int[frameCount][];
      }

      public int getFrameCount() {
         return this.frames.length;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public int[][] getDrawOrders() {
         return this.drawOrders;
      }

      public void setFrame(int frameIndex, float time, int[] drawOrder) {
         this.frames[frameIndex] = time;
         this.drawOrders[frameIndex] = drawOrder;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> firedEvents, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            int frame;
            if (time >= frames[frames.length - 1]) {
               frame = frames.length - 1;
            } else {
               frame = Animation.binarySearch(frames, time) - 1;
            }

            Array<Slot> drawOrder = skeleton.drawOrder;
            Array<Slot> slots = skeleton.slots;
            int[] drawOrderToSetupIndex = this.drawOrders[frame];
            if (drawOrderToSetupIndex == null) {
               System.arraycopy(slots.items, 0, drawOrder.items, 0, slots.size);
            } else {
               int i = 0;

               for (int n = drawOrderToSetupIndex.length; i < n; i++) {
                  drawOrder.set(i, slots.get(drawOrderToSetupIndex[i]));
               }
            }
         }
      }
   }

   public static class EventTimeline implements Animation.Timeline {
      private final float[] frames;
      private final Event[] events;

      public EventTimeline(int frameCount) {
         this.frames = new float[frameCount];
         this.events = new Event[frameCount];
      }

      public int getFrameCount() {
         return this.frames.length;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public Event[] getEvents() {
         return this.events;
      }

      public void setFrame(int frameIndex, Event event) {
         this.frames[frameIndex] = event.time;
         this.events[frameIndex] = event;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> firedEvents, float alpha) {
         if (firedEvents != null) {
            float[] frames = this.frames;
            int frameCount = frames.length;
            if (lastTime > time) {
               this.apply(skeleton, lastTime, 2.1474836E9F, firedEvents, alpha);
               lastTime = -1.0F;
            } else if (lastTime >= frames[frameCount - 1]) {
               return;
            }

            if (!(time < frames[0])) {
               int frame;
               if (lastTime < frames[0]) {
                  frame = 0;
               } else {
                  frame = Animation.binarySearch(frames, lastTime);
                  float frameTime = frames[frame];

                  while (frame > 0 && frames[frame - 1] == frameTime) {
                     frame--;
                  }
               }

               while (frame < frameCount && time >= frames[frame]) {
                  firedEvents.add(this.events[frame]);
                  frame++;
               }
            }
         }
      }
   }

   public static class IkConstraintTimeline extends Animation.CurveTimeline {
      public static final int ENTRIES = 3;
      private static final int PREV_TIME = -3;
      private static final int PREV_MIX = -2;
      private static final int PREV_BEND_DIRECTION = -1;
      private static final int MIX = 1;
      private static final int BEND_DIRECTION = 2;
      int ikConstraintIndex;
      private final float[] frames;

      public IkConstraintTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount * 3];
      }

      public void setIkConstraintIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.ikConstraintIndex = index;
         }
      }

      public int getIkConstraintIndex() {
         return this.ikConstraintIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public void setFrame(int frameIndex, float time, float mix, int bendDirection) {
         frameIndex *= 3;
         this.frames[frameIndex] = time;
         this.frames[frameIndex + 1] = mix;
         this.frames[frameIndex + 2] = bendDirection;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            IkConstraint constraint = skeleton.ikConstraints.get(this.ikConstraintIndex);
            if (time >= frames[frames.length - 3]) {
               constraint.mix = constraint.mix + (frames[frames.length + -2] - constraint.mix) * alpha;
               constraint.bendDirection = (int)frames[frames.length + -1];
            } else {
               int frame = Animation.binarySearch(frames, time, 3);
               float mix = frames[frame + -2];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 3 - 1, 1.0F - (time - frameTime) / (frames[frame + -3] - frameTime));
               constraint.mix = constraint.mix + (mix + (frames[frame + 1] - mix) * percent - constraint.mix) * alpha;
               constraint.bendDirection = (int)frames[frame + -1];
            }
         }
      }
   }

   public static class PathConstraintMixTimeline extends Animation.CurveTimeline {
      public static final int ENTRIES = 3;
      private static final int PREV_TIME = -3;
      private static final int PREV_ROTATE = -2;
      private static final int PREV_TRANSLATE = -1;
      private static final int ROTATE = 1;
      private static final int TRANSLATE = 2;
      int pathConstraintIndex;
      private final float[] frames;

      public PathConstraintMixTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount * 3];
      }

      public void setPathConstraintIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.pathConstraintIndex = index;
         }
      }

      public int getPathConstraintIndex() {
         return this.pathConstraintIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public void setFrame(int frameIndex, float time, float rotateMix, float translateMix) {
         frameIndex *= 3;
         this.frames[frameIndex] = time;
         this.frames[frameIndex + 1] = rotateMix;
         this.frames[frameIndex + 2] = translateMix;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            PathConstraint constraint = skeleton.pathConstraints.get(this.pathConstraintIndex);
            if (time >= frames[frames.length - 3]) {
               int i = frames.length;
               constraint.rotateMix = constraint.rotateMix + (frames[i + -2] - constraint.rotateMix) * alpha;
               constraint.translateMix = constraint.translateMix + (frames[i + -1] - constraint.translateMix) * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 3);
               float rotate = frames[frame + -2];
               float translate = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 3 - 1, 1.0F - (time - frameTime) / (frames[frame + -3] - frameTime));
               constraint.rotateMix = constraint.rotateMix + (rotate + (frames[frame + 1] - rotate) * percent - constraint.rotateMix) * alpha;
               constraint.translateMix = constraint.translateMix + (translate + (frames[frame + 2] - translate) * percent - constraint.translateMix) * alpha;
            }
         }
      }
   }

   public static class PathConstraintPositionTimeline extends Animation.CurveTimeline {
      public static final int ENTRIES = 2;
      static final int PREV_TIME = -2;
      static final int PREV_VALUE = -1;
      static final int VALUE = 1;
      int pathConstraintIndex;
      final float[] frames;

      public PathConstraintPositionTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount * 2];
      }

      public void setPathConstraintIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.pathConstraintIndex = index;
         }
      }

      public int getPathConstraintIndex() {
         return this.pathConstraintIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public void setFrame(int frameIndex, float time, float value) {
         frameIndex *= 2;
         this.frames[frameIndex] = time;
         this.frames[frameIndex + 1] = value;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            PathConstraint constraint = skeleton.pathConstraints.get(this.pathConstraintIndex);
            if (time >= frames[frames.length - 2]) {
               int i = frames.length;
               constraint.position = constraint.position + (frames[i + -1] - constraint.position) * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 2);
               float position = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 2 - 1, 1.0F - (time - frameTime) / (frames[frame + -2] - frameTime));
               constraint.position = constraint.position + (position + (frames[frame + 1] - position) * percent - constraint.position) * alpha;
            }
         }
      }
   }

   public static class PathConstraintSpacingTimeline extends Animation.PathConstraintPositionTimeline {
      public PathConstraintSpacingTimeline(int frameCount) {
         super(frameCount);
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            PathConstraint constraint = skeleton.pathConstraints.get(this.pathConstraintIndex);
            if (time >= frames[frames.length - 2]) {
               int i = frames.length;
               constraint.spacing = constraint.spacing + (frames[i + -1] - constraint.spacing) * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 2);
               float spacing = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 2 - 1, 1.0F - (time - frameTime) / (frames[frame + -2] - frameTime));
               constraint.spacing = constraint.spacing + (spacing + (frames[frame + 1] - spacing) * percent - constraint.spacing) * alpha;
            }
         }
      }
   }

   public static class RotateTimeline extends Animation.CurveTimeline {
      public static final int ENTRIES = 2;
      private static final int PREV_TIME = -2;
      private static final int PREV_ROTATION = -1;
      private static final int ROTATION = 1;
      int boneIndex;
      final float[] frames;

      public RotateTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount << 1];
      }

      public void setBoneIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.boneIndex = index;
         }
      }

      public int getBoneIndex() {
         return this.boneIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public void setFrame(int frameIndex, float time, float degrees) {
         frameIndex <<= 1;
         this.frames[frameIndex] = time;
         this.frames[frameIndex + 1] = degrees;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            Bone bone = skeleton.bones.get(this.boneIndex);
            if (time >= frames[frames.length - 2]) {
               float amount = bone.data.rotation + frames[frames.length + -1] - bone.rotation;

               while (amount > 180.0F) {
                  amount -= 360.0F;
               }

               while (amount < -180.0F) {
                  amount += 360.0F;
               }

               bone.rotation += amount * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 2);
               float prevRotation = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent((frame >> 1) - 1, 1.0F - (time - frameTime) / (frames[frame + -2] - frameTime));
               float amount = frames[frame + 1] - prevRotation;

               while (amount > 180.0F) {
                  amount -= 360.0F;
               }

               while (amount < -180.0F) {
                  amount += 360.0F;
               }

               amount = bone.data.rotation + (prevRotation + amount * percent) - bone.rotation;

               while (amount > 180.0F) {
                  amount -= 360.0F;
               }

               while (amount < -180.0F) {
                  amount += 360.0F;
               }

               bone.rotation += amount * alpha;
            }
         }
      }
   }

   public static class ScaleTimeline extends Animation.TranslateTimeline {
      public ScaleTimeline(int frameCount) {
         super(frameCount);
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            Bone bone = skeleton.bones.get(this.boneIndex);
            if (time >= frames[frames.length - 3]) {
               bone.scaleX = bone.scaleX + (bone.data.scaleX * frames[frames.length + -2] - bone.scaleX) * alpha;
               bone.scaleY = bone.scaleY + (bone.data.scaleY * frames[frames.length + -1] - bone.scaleY) * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 3);
               float prevX = frames[frame + -2];
               float prevY = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 3 - 1, 1.0F - (time - frameTime) / (frames[frame + -3] - frameTime));
               bone.scaleX = bone.scaleX + (bone.data.scaleX * (prevX + (frames[frame + 1] - prevX) * percent) - bone.scaleX) * alpha;
               bone.scaleY = bone.scaleY + (bone.data.scaleY * (prevY + (frames[frame + 2] - prevY) * percent) - bone.scaleY) * alpha;
            }
         }
      }
   }

   public static class ShearTimeline extends Animation.TranslateTimeline {
      public ShearTimeline(int frameCount) {
         super(frameCount);
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            Bone bone = skeleton.bones.get(this.boneIndex);
            if (time >= frames[frames.length - 3]) {
               bone.shearX = bone.shearX + (bone.data.shearX + frames[frames.length + -2] - bone.shearX) * alpha;
               bone.shearY = bone.shearY + (bone.data.shearY + frames[frames.length + -1] - bone.shearY) * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 3);
               float prevX = frames[frame + -2];
               float prevY = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 3 - 1, 1.0F - (time - frameTime) / (frames[frame + -3] - frameTime));
               bone.shearX = bone.shearX + (bone.data.shearX + (prevX + (frames[frame + 1] - prevX) * percent) - bone.shearX) * alpha;
               bone.shearY = bone.shearY + (bone.data.shearY + (prevY + (frames[frame + 2] - prevY) * percent) - bone.shearY) * alpha;
            }
         }
      }
   }

   public interface Timeline {
      void apply(Skeleton var1, float var2, float var3, Array<Event> var4, float var5);
   }

   public static class TransformConstraintTimeline extends Animation.CurveTimeline {
      public static final int ENTRIES = 5;
      private static final int PREV_TIME = -5;
      private static final int PREV_ROTATE = -4;
      private static final int PREV_TRANSLATE = -3;
      private static final int PREV_SCALE = -2;
      private static final int PREV_SHEAR = -1;
      private static final int ROTATE = 1;
      private static final int TRANSLATE = 2;
      private static final int SCALE = 3;
      private static final int SHEAR = 4;
      int transformConstraintIndex;
      private final float[] frames;

      public TransformConstraintTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount * 5];
      }

      public void setTransformConstraintIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.transformConstraintIndex = index;
         }
      }

      public int getTransformConstraintIndex() {
         return this.transformConstraintIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public void setFrame(int frameIndex, float time, float rotateMix, float translateMix, float scaleMix, float shearMix) {
         frameIndex *= 5;
         this.frames[frameIndex] = time;
         this.frames[frameIndex + 1] = rotateMix;
         this.frames[frameIndex + 2] = translateMix;
         this.frames[frameIndex + 3] = scaleMix;
         this.frames[frameIndex + 4] = shearMix;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            TransformConstraint constraint = skeleton.transformConstraints.get(this.transformConstraintIndex);
            if (time >= frames[frames.length - 5]) {
               int i = frames.length;
               constraint.rotateMix = constraint.rotateMix + (frames[i + -4] - constraint.rotateMix) * alpha;
               constraint.translateMix = constraint.translateMix + (frames[i + -3] - constraint.translateMix) * alpha;
               constraint.scaleMix = constraint.scaleMix + (frames[i + -2] - constraint.scaleMix) * alpha;
               constraint.shearMix = constraint.shearMix + (frames[i + -1] - constraint.shearMix) * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 5);
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 5 - 1, 1.0F - (time - frameTime) / (frames[frame + -5] - frameTime));
               float rotate = frames[frame + -4];
               float translate = frames[frame + -3];
               float scale = frames[frame + -2];
               float shear = frames[frame + -1];
               constraint.rotateMix = constraint.rotateMix + (rotate + (frames[frame + 1] - rotate) * percent - constraint.rotateMix) * alpha;
               constraint.translateMix = constraint.translateMix + (translate + (frames[frame + 2] - translate) * percent - constraint.translateMix) * alpha;
               constraint.scaleMix = constraint.scaleMix + (scale + (frames[frame + 3] - scale) * percent - constraint.scaleMix) * alpha;
               constraint.shearMix = constraint.shearMix + (shear + (frames[frame + 4] - shear) * percent - constraint.shearMix) * alpha;
            }
         }
      }
   }

   public static class TranslateTimeline extends Animation.CurveTimeline {
      public static final int ENTRIES = 3;
      static final int PREV_TIME = -3;
      static final int PREV_X = -2;
      static final int PREV_Y = -1;
      static final int X = 1;
      static final int Y = 2;
      int boneIndex;
      final float[] frames;

      public TranslateTimeline(int frameCount) {
         super(frameCount);
         this.frames = new float[frameCount * 3];
      }

      public void setBoneIndex(int index) {
         if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0.");
         } else {
            this.boneIndex = index;
         }
      }

      public int getBoneIndex() {
         return this.boneIndex;
      }

      public float[] getFrames() {
         return this.frames;
      }

      public void setFrame(int frameIndex, float time, float x, float y) {
         frameIndex *= 3;
         this.frames[frameIndex] = time;
         this.frames[frameIndex + 1] = x;
         this.frames[frameIndex + 2] = y;
      }

      @Override
      public void apply(Skeleton skeleton, float lastTime, float time, Array<Event> events, float alpha) {
         float[] frames = this.frames;
         if (!(time < frames[0])) {
            Bone bone = skeleton.bones.get(this.boneIndex);
            if (time >= frames[frames.length - 3]) {
               bone.x = bone.x + (bone.data.x + frames[frames.length + -2] - bone.x) * alpha;
               bone.y = bone.y + (bone.data.y + frames[frames.length + -1] - bone.y) * alpha;
            } else {
               int frame = Animation.binarySearch(frames, time, 3);
               float prevX = frames[frame + -2];
               float prevY = frames[frame + -1];
               float frameTime = frames[frame];
               float percent = this.getCurvePercent(frame / 3 - 1, 1.0F - (time - frameTime) / (frames[frame + -3] - frameTime));
               bone.x = bone.x + (bone.data.x + prevX + (frames[frame + 1] - prevX) * percent - bone.x) * alpha;
               bone.y = bone.y + (bone.data.y + prevY + (frames[frame + 2] - prevY) * percent - bone.y) * alpha;
            }
         }
      }
   }
}
