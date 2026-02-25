package com.badlogic.gdx.scenes.scene2d.actions;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

public class Actions {
   public static <T extends Action> T action(Class<T> type) {
      Pool<T> pool = Pools.get(type);
      T action = (T)pool.obtain();
      action.setPool(pool);
      return action;
   }

   public static AddAction addAction(Action action) {
      AddAction addAction = action(AddAction.class);
      addAction.setAction(action);
      return addAction;
   }

   public static AddAction addAction(Action action, Actor targetActor) {
      AddAction addAction = action(AddAction.class);
      addAction.setTarget(targetActor);
      addAction.setAction(action);
      return addAction;
   }

   public static RemoveAction removeAction(Action action) {
      RemoveAction removeAction = action(RemoveAction.class);
      removeAction.setAction(action);
      return removeAction;
   }

   public static RemoveAction removeAction(Action action, Actor targetActor) {
      RemoveAction removeAction = action(RemoveAction.class);
      removeAction.setTarget(targetActor);
      removeAction.setAction(action);
      return removeAction;
   }

   public static MoveToAction moveTo(float x, float y) {
      return moveTo(x, y, 0.0F, null);
   }

   public static MoveToAction moveTo(float x, float y, float duration) {
      return moveTo(x, y, duration, null);
   }

   public static MoveToAction moveTo(float x, float y, float duration, Interpolation interpolation) {
      MoveToAction action = action(MoveToAction.class);
      action.setPosition(x, y);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static MoveToAction moveToAligned(float x, float y, int alignment) {
      return moveToAligned(x, y, alignment, 0.0F, null);
   }

   public static MoveToAction moveToAligned(float x, float y, int alignment, float duration) {
      return moveToAligned(x, y, alignment, duration, null);
   }

   public static MoveToAction moveToAligned(float x, float y, int alignment, float duration, Interpolation interpolation) {
      MoveToAction action = action(MoveToAction.class);
      action.setPosition(x, y, alignment);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static MoveByAction moveBy(float amountX, float amountY) {
      return moveBy(amountX, amountY, 0.0F, null);
   }

   public static MoveByAction moveBy(float amountX, float amountY, float duration) {
      return moveBy(amountX, amountY, duration, null);
   }

   public static MoveByAction moveBy(float amountX, float amountY, float duration, Interpolation interpolation) {
      MoveByAction action = action(MoveByAction.class);
      action.setAmount(amountX, amountY);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static SizeToAction sizeTo(float x, float y) {
      return sizeTo(x, y, 0.0F, null);
   }

   public static SizeToAction sizeTo(float x, float y, float duration) {
      return sizeTo(x, y, duration, null);
   }

   public static SizeToAction sizeTo(float x, float y, float duration, Interpolation interpolation) {
      SizeToAction action = action(SizeToAction.class);
      action.setSize(x, y);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static SizeByAction sizeBy(float amountX, float amountY) {
      return sizeBy(amountX, amountY, 0.0F, null);
   }

   public static SizeByAction sizeBy(float amountX, float amountY, float duration) {
      return sizeBy(amountX, amountY, duration, null);
   }

   public static SizeByAction sizeBy(float amountX, float amountY, float duration, Interpolation interpolation) {
      SizeByAction action = action(SizeByAction.class);
      action.setAmount(amountX, amountY);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static ScaleToAction scaleTo(float x, float y) {
      return scaleTo(x, y, 0.0F, null);
   }

   public static ScaleToAction scaleTo(float x, float y, float duration) {
      return scaleTo(x, y, duration, null);
   }

   public static ScaleToAction scaleTo(float x, float y, float duration, Interpolation interpolation) {
      ScaleToAction action = action(ScaleToAction.class);
      action.setScale(x, y);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static ScaleByAction scaleBy(float amountX, float amountY) {
      return scaleBy(amountX, amountY, 0.0F, null);
   }

   public static ScaleByAction scaleBy(float amountX, float amountY, float duration) {
      return scaleBy(amountX, amountY, duration, null);
   }

   public static ScaleByAction scaleBy(float amountX, float amountY, float duration, Interpolation interpolation) {
      ScaleByAction action = action(ScaleByAction.class);
      action.setAmount(amountX, amountY);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static RotateToAction rotateTo(float rotation) {
      return rotateTo(rotation, 0.0F, null);
   }

   public static RotateToAction rotateTo(float rotation, float duration) {
      return rotateTo(rotation, duration, null);
   }

   public static RotateToAction rotateTo(float rotation, float duration, Interpolation interpolation) {
      RotateToAction action = action(RotateToAction.class);
      action.setRotation(rotation);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static RotateByAction rotateBy(float rotationAmount) {
      return rotateBy(rotationAmount, 0.0F, null);
   }

   public static RotateByAction rotateBy(float rotationAmount, float duration) {
      return rotateBy(rotationAmount, duration, null);
   }

   public static RotateByAction rotateBy(float rotationAmount, float duration, Interpolation interpolation) {
      RotateByAction action = action(RotateByAction.class);
      action.setAmount(rotationAmount);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static ColorAction color(Color color) {
      return color(color, 0.0F, null);
   }

   public static ColorAction color(Color color, float duration) {
      return color(color, duration, null);
   }

   public static ColorAction color(Color color, float duration, Interpolation interpolation) {
      ColorAction action = action(ColorAction.class);
      action.setEndColor(color);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static AlphaAction alpha(float a) {
      return alpha(a, 0.0F, null);
   }

   public static AlphaAction alpha(float a, float duration) {
      return alpha(a, duration, null);
   }

   public static AlphaAction alpha(float a, float duration, Interpolation interpolation) {
      AlphaAction action = action(AlphaAction.class);
      action.setAlpha(a);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static AlphaAction fadeOut(float duration) {
      return alpha(0.0F, duration, null);
   }

   public static AlphaAction fadeOut(float duration, Interpolation interpolation) {
      AlphaAction action = action(AlphaAction.class);
      action.setAlpha(0.0F);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static AlphaAction fadeIn(float duration) {
      return alpha(1.0F, duration, null);
   }

   public static AlphaAction fadeIn(float duration, Interpolation interpolation) {
      AlphaAction action = action(AlphaAction.class);
      action.setAlpha(1.0F);
      action.setDuration(duration);
      action.setInterpolation(interpolation);
      return action;
   }

   public static VisibleAction show() {
      return visible(true);
   }

   public static VisibleAction hide() {
      return visible(false);
   }

   public static VisibleAction visible(boolean visible) {
      VisibleAction action = action(VisibleAction.class);
      action.setVisible(visible);
      return action;
   }

   public static TouchableAction touchable(Touchable touchable) {
      TouchableAction action = action(TouchableAction.class);
      action.setTouchable(touchable);
      return action;
   }

   public static RemoveActorAction removeActor() {
      return action(RemoveActorAction.class);
   }

   public static RemoveActorAction removeActor(Actor removeActor) {
      RemoveActorAction action = action(RemoveActorAction.class);
      action.setTarget(removeActor);
      return action;
   }

   public static DelayAction delay(float duration) {
      DelayAction action = action(DelayAction.class);
      action.setDuration(duration);
      return action;
   }

   public static DelayAction delay(float duration, Action delayedAction) {
      DelayAction action = action(DelayAction.class);
      action.setDuration(duration);
      action.setAction(delayedAction);
      return action;
   }

   public static TimeScaleAction timeScale(float scale, Action scaledAction) {
      TimeScaleAction action = action(TimeScaleAction.class);
      action.setScale(scale);
      action.setAction(scaledAction);
      return action;
   }

   public static SequenceAction sequence(Action action1) {
      SequenceAction action = action(SequenceAction.class);
      action.addAction(action1);
      return action;
   }

   public static SequenceAction sequence(Action action1, Action action2) {
      SequenceAction action = action(SequenceAction.class);
      action.addAction(action1);
      action.addAction(action2);
      return action;
   }

   public static SequenceAction sequence(Action action1, Action action2, Action action3) {
      SequenceAction action = action(SequenceAction.class);
      action.addAction(action1);
      action.addAction(action2);
      action.addAction(action3);
      return action;
   }

   public static SequenceAction sequence(Action action1, Action action2, Action action3, Action action4) {
      SequenceAction action = action(SequenceAction.class);
      action.addAction(action1);
      action.addAction(action2);
      action.addAction(action3);
      action.addAction(action4);
      return action;
   }

   public static SequenceAction sequence(Action action1, Action action2, Action action3, Action action4, Action action5) {
      SequenceAction action = action(SequenceAction.class);
      action.addAction(action1);
      action.addAction(action2);
      action.addAction(action3);
      action.addAction(action4);
      action.addAction(action5);
      return action;
   }

   public static SequenceAction sequence(Action... actions) {
      SequenceAction action = action(SequenceAction.class);
      int i = 0;

      for (int n = actions.length; i < n; i++) {
         action.addAction(actions[i]);
      }

      return action;
   }

   public static SequenceAction sequence() {
      return action(SequenceAction.class);
   }

   public static ParallelAction parallel(Action action1) {
      ParallelAction action = action(ParallelAction.class);
      action.addAction(action1);
      return action;
   }

   public static ParallelAction parallel(Action action1, Action action2) {
      ParallelAction action = action(ParallelAction.class);
      action.addAction(action1);
      action.addAction(action2);
      return action;
   }

   public static ParallelAction parallel(Action action1, Action action2, Action action3) {
      ParallelAction action = action(ParallelAction.class);
      action.addAction(action1);
      action.addAction(action2);
      action.addAction(action3);
      return action;
   }

   public static ParallelAction parallel(Action action1, Action action2, Action action3, Action action4) {
      ParallelAction action = action(ParallelAction.class);
      action.addAction(action1);
      action.addAction(action2);
      action.addAction(action3);
      action.addAction(action4);
      return action;
   }

   public static ParallelAction parallel(Action action1, Action action2, Action action3, Action action4, Action action5) {
      ParallelAction action = action(ParallelAction.class);
      action.addAction(action1);
      action.addAction(action2);
      action.addAction(action3);
      action.addAction(action4);
      action.addAction(action5);
      return action;
   }

   public static ParallelAction parallel(Action... actions) {
      ParallelAction action = action(ParallelAction.class);
      int i = 0;

      for (int n = actions.length; i < n; i++) {
         action.addAction(actions[i]);
      }

      return action;
   }

   public static ParallelAction parallel() {
      return action(ParallelAction.class);
   }

   public static RepeatAction repeat(int count, Action repeatedAction) {
      RepeatAction action = action(RepeatAction.class);
      action.setCount(count);
      action.setAction(repeatedAction);
      return action;
   }

   public static RepeatAction forever(Action repeatedAction) {
      RepeatAction action = action(RepeatAction.class);
      action.setCount(-1);
      action.setAction(repeatedAction);
      return action;
   }

   public static RunnableAction run(Runnable runnable) {
      RunnableAction action = action(RunnableAction.class);
      action.setRunnable(runnable);
      return action;
   }

   public static LayoutAction layout(boolean enabled) {
      LayoutAction action = action(LayoutAction.class);
      action.setLayoutEnabled(enabled);
      return action;
   }

   public static AfterAction after(Action action) {
      AfterAction afterAction = action(AfterAction.class);
      afterAction.setAction(action);
      return afterAction;
   }

   public static AddListenerAction addListener(EventListener listener, boolean capture) {
      AddListenerAction addAction = action(AddListenerAction.class);
      addAction.setListener(listener);
      addAction.setCapture(capture);
      return addAction;
   }

   public static AddListenerAction addListener(EventListener listener, boolean capture, Actor targetActor) {
      AddListenerAction addAction = action(AddListenerAction.class);
      addAction.setTarget(targetActor);
      addAction.setListener(listener);
      addAction.setCapture(capture);
      return addAction;
   }

   public static RemoveListenerAction removeListener(EventListener listener, boolean capture) {
      RemoveListenerAction addAction = action(RemoveListenerAction.class);
      addAction.setListener(listener);
      addAction.setCapture(capture);
      return addAction;
   }

   public static RemoveListenerAction removeListener(EventListener listener, boolean capture, Actor targetActor) {
      RemoveListenerAction addAction = action(RemoveListenerAction.class);
      addAction.setTarget(targetActor);
      addAction.setListener(listener);
      addAction.setCapture(capture);
      return addAction;
   }
}
