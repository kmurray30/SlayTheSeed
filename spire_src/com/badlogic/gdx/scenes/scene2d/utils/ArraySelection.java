package com.badlogic.gdx.scenes.scene2d.utils;

import com.badlogic.gdx.utils.Array;
import java.util.Iterator;

public class ArraySelection<T> extends Selection<T> {
   private Array<T> array;
   private boolean rangeSelect = true;
   private int rangeStart;

   public ArraySelection(Array<T> array) {
      this.array = array;
   }

   @Override
   public void choose(T item) {
      if (item == null) {
         throw new IllegalArgumentException("item cannot be null.");
      } else if (!this.isDisabled) {
         int index = this.array.indexOf(item, false);
         if (this.selected.size > 0 && this.rangeSelect && this.multiple && UIUtils.shift()) {
            int oldRangeState = this.rangeStart;
            this.snapshot();
            int start = this.rangeStart;
            int end = index;
            if (start > index) {
               end = start;
               start = index;
            }

            if (!UIUtils.ctrl()) {
               this.selected.clear();
            }

            for (int i = start; i <= end; i++) {
               this.selected.add(this.array.get(i));
            }

            if (this.fireChangeEvent()) {
               this.rangeStart = oldRangeState;
               this.revert();
            }

            this.cleanup();
         } else {
            this.rangeStart = index;
            super.choose(item);
         }
      }
   }

   public boolean getRangeSelect() {
      return this.rangeSelect;
   }

   public void setRangeSelect(boolean rangeSelect) {
      this.rangeSelect = rangeSelect;
   }

   public void validate() {
      Array<T> array = this.array;
      if (array.size == 0) {
         this.clear();
      } else {
         Iterator<T> iter = this.items().iterator();

         while (iter.hasNext()) {
            T selected = iter.next();
            if (!array.contains(selected, false)) {
               iter.remove();
            }
         }

         if (this.required && this.selected.size == 0) {
            this.set(array.first());
         }
      }
   }
}
