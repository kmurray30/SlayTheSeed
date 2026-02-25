package com.badlogic.gdx.graphics.g3d.utils;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool;

public class BaseAnimationController {
   private final Pool<BaseAnimationController.Transform> transformPool = new Pool<BaseAnimationController.Transform>() {
      protected BaseAnimationController.Transform newObject() {
         return new BaseAnimationController.Transform();
      }
   };
   private static final ObjectMap<Node, BaseAnimationController.Transform> transforms = new ObjectMap<>();
   private boolean applying = false;
   public final ModelInstance target;
   private static final BaseAnimationController.Transform tmpT = new BaseAnimationController.Transform();

   public BaseAnimationController(ModelInstance target) {
      this.target = target;
   }

   protected void begin() {
      if (this.applying) {
         throw new GdxRuntimeException("You must call end() after each call to being()");
      } else {
         this.applying = true;
      }
   }

   protected void apply(Animation animation, float time, float weight) {
      if (!this.applying) {
         throw new GdxRuntimeException("You must call begin() before adding an animation");
      } else {
         applyAnimation(transforms, this.transformPool, weight, animation, time);
      }
   }

   protected void end() {
      if (!this.applying) {
         throw new GdxRuntimeException("You must call begin() first");
      } else {
         for (ObjectMap.Entry<Node, BaseAnimationController.Transform> entry : transforms.entries()) {
            entry.value.toMatrix4(entry.key.localTransform);
            this.transformPool.free(entry.value);
         }

         transforms.clear();
         this.target.calculateTransforms();
         this.applying = false;
      }
   }

   protected void applyAnimation(Animation animation, float time) {
      if (this.applying) {
         throw new GdxRuntimeException("Call end() first");
      } else {
         applyAnimation(null, null, 1.0F, animation, time);
         this.target.calculateTransforms();
      }
   }

   protected void applyAnimations(Animation anim1, float time1, Animation anim2, float time2, float weight) {
      if (anim2 == null || weight == 0.0F) {
         this.applyAnimation(anim1, time1);
      } else if (anim1 != null && weight != 1.0F) {
         if (this.applying) {
            throw new GdxRuntimeException("Call end() first");
         }

         this.begin();
         this.apply(anim1, time1, 1.0F);
         this.apply(anim2, time2, weight);
         this.end();
      } else {
         this.applyAnimation(anim2, time2);
      }
   }

   private static final <T> int getFirstKeyframeIndexAtTime(Array<NodeKeyframe<T>> arr, float time) {
      int n = arr.size - 1;

      for (int i = 0; i < n; i++) {
         if (time >= arr.get(i).keytime && time <= arr.get(i + 1).keytime) {
            return i;
         }
      }

      return 0;
   }

   private static final Vector3 getTranslationAtTime(NodeAnimation nodeAnim, float time, Vector3 out) {
      if (nodeAnim.translation == null) {
         return out.set(nodeAnim.node.translation);
      } else if (nodeAnim.translation.size == 1) {
         return out.set((Vector3)nodeAnim.translation.get(0).value);
      } else {
         int index = getFirstKeyframeIndexAtTime(nodeAnim.translation, time);
         NodeKeyframe firstKeyframe = nodeAnim.translation.get(index);
         out.set((Vector3)firstKeyframe.value);
         if (++index < nodeAnim.translation.size) {
            NodeKeyframe<Vector3> secondKeyframe = nodeAnim.translation.get(index);
            float t = (time - firstKeyframe.keytime) / (secondKeyframe.keytime - firstKeyframe.keytime);
            out.lerp(secondKeyframe.value, t);
         }

         return out;
      }
   }

   private static final Quaternion getRotationAtTime(NodeAnimation nodeAnim, float time, Quaternion out) {
      if (nodeAnim.rotation == null) {
         return out.set(nodeAnim.node.rotation);
      } else if (nodeAnim.rotation.size == 1) {
         return out.set((Quaternion)nodeAnim.rotation.get(0).value);
      } else {
         int index = getFirstKeyframeIndexAtTime(nodeAnim.rotation, time);
         NodeKeyframe firstKeyframe = nodeAnim.rotation.get(index);
         out.set((Quaternion)firstKeyframe.value);
         if (++index < nodeAnim.rotation.size) {
            NodeKeyframe<Quaternion> secondKeyframe = nodeAnim.rotation.get(index);
            float t = (time - firstKeyframe.keytime) / (secondKeyframe.keytime - firstKeyframe.keytime);
            out.slerp(secondKeyframe.value, t);
         }

         return out;
      }
   }

   private static final Vector3 getScalingAtTime(NodeAnimation nodeAnim, float time, Vector3 out) {
      if (nodeAnim.scaling == null) {
         return out.set(nodeAnim.node.scale);
      } else if (nodeAnim.scaling.size == 1) {
         return out.set((Vector3)nodeAnim.scaling.get(0).value);
      } else {
         int index = getFirstKeyframeIndexAtTime(nodeAnim.scaling, time);
         NodeKeyframe firstKeyframe = nodeAnim.scaling.get(index);
         out.set((Vector3)firstKeyframe.value);
         if (++index < nodeAnim.scaling.size) {
            NodeKeyframe<Vector3> secondKeyframe = nodeAnim.scaling.get(index);
            float t = (time - firstKeyframe.keytime) / (secondKeyframe.keytime - firstKeyframe.keytime);
            out.lerp(secondKeyframe.value, t);
         }

         return out;
      }
   }

   private static final BaseAnimationController.Transform getNodeAnimationTransform(NodeAnimation nodeAnim, float time) {
      BaseAnimationController.Transform transform = tmpT;
      getTranslationAtTime(nodeAnim, time, transform.translation);
      getRotationAtTime(nodeAnim, time, transform.rotation);
      getScalingAtTime(nodeAnim, time, transform.scale);
      return transform;
   }

   private static final void applyNodeAnimationDirectly(NodeAnimation nodeAnim, float time) {
      Node node = nodeAnim.node;
      node.isAnimated = true;
      BaseAnimationController.Transform transform = getNodeAnimationTransform(nodeAnim, time);
      transform.toMatrix4(node.localTransform);
   }

   private static final void applyNodeAnimationBlending(
      NodeAnimation nodeAnim, ObjectMap<Node, BaseAnimationController.Transform> out, Pool<BaseAnimationController.Transform> pool, float alpha, float time
   ) {
      Node node = nodeAnim.node;
      node.isAnimated = true;
      BaseAnimationController.Transform transform = getNodeAnimationTransform(nodeAnim, time);
      BaseAnimationController.Transform t = out.get(node, null);
      if (t != null) {
         if (alpha > 0.999999F) {
            t.set(transform);
         } else {
            t.lerp(transform, alpha);
         }
      } else if (alpha > 0.999999F) {
         out.put(node, pool.obtain().set(transform));
      } else {
         out.put(node, pool.obtain().set(node.translation, node.rotation, node.scale).lerp(transform, alpha));
      }
   }

   protected static void applyAnimation(
      ObjectMap<Node, BaseAnimationController.Transform> out, Pool<BaseAnimationController.Transform> pool, float alpha, Animation animation, float time
   ) {
      if (out == null) {
         for (NodeAnimation nodeAnim : animation.nodeAnimations) {
            applyNodeAnimationDirectly(nodeAnim, time);
         }
      } else {
         for (Node node : out.keys()) {
            node.isAnimated = false;
         }

         for (NodeAnimation nodeAnim : animation.nodeAnimations) {
            applyNodeAnimationBlending(nodeAnim, out, pool, alpha, time);
         }

         for (ObjectMap.Entry<Node, BaseAnimationController.Transform> e : out.entries()) {
            if (!e.key.isAnimated) {
               e.key.isAnimated = true;
               e.value.lerp(e.key.translation, e.key.rotation, e.key.scale, alpha);
            }
         }
      }
   }

   protected void removeAnimation(Animation animation) {
      for (NodeAnimation nodeAnim : animation.nodeAnimations) {
         nodeAnim.node.isAnimated = false;
      }
   }

   public static final class Transform implements Pool.Poolable {
      public final Vector3 translation = new Vector3();
      public final Quaternion rotation = new Quaternion();
      public final Vector3 scale = new Vector3(1.0F, 1.0F, 1.0F);

      public BaseAnimationController.Transform idt() {
         this.translation.set(0.0F, 0.0F, 0.0F);
         this.rotation.idt();
         this.scale.set(1.0F, 1.0F, 1.0F);
         return this;
      }

      public BaseAnimationController.Transform set(Vector3 t, Quaternion r, Vector3 s) {
         this.translation.set(t);
         this.rotation.set(r);
         this.scale.set(s);
         return this;
      }

      public BaseAnimationController.Transform set(BaseAnimationController.Transform other) {
         return this.set(other.translation, other.rotation, other.scale);
      }

      public BaseAnimationController.Transform lerp(BaseAnimationController.Transform target, float alpha) {
         return this.lerp(target.translation, target.rotation, target.scale, alpha);
      }

      public BaseAnimationController.Transform lerp(Vector3 targetT, Quaternion targetR, Vector3 targetS, float alpha) {
         this.translation.lerp(targetT, alpha);
         this.rotation.slerp(targetR, alpha);
         this.scale.lerp(targetS, alpha);
         return this;
      }

      public Matrix4 toMatrix4(Matrix4 out) {
         return out.set(this.translation, this.rotation, this.scale);
      }

      @Override
      public void reset() {
         this.idt();
      }

      @Override
      public String toString() {
         return this.translation.toString() + " - " + this.rotation.toString() + " - " + this.scale.toString();
      }
   }
}
