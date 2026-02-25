package com.esotericsoftware.spine;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool;
import com.esotericsoftware.spine.attachments.Attachment;
import java.util.Iterator;

public class Skin {
   private static final Skin.Key lookup = new Skin.Key();
   final String name;
   final ObjectMap<Skin.Key, Attachment> attachments = new ObjectMap<>();
   final Pool<Skin.Key> keyPool = new Pool(64) {
      @Override
      protected Object newObject() {
         return new Skin.Key();
      }
   };

   public Skin(String name) {
      if (name == null) {
         throw new IllegalArgumentException("name cannot be null.");
      } else {
         this.name = name;
      }
   }

   public void addAttachment(int slotIndex, String name, Attachment attachment) {
      if (attachment == null) {
         throw new IllegalArgumentException("attachment cannot be null.");
      } else if (slotIndex < 0) {
         throw new IllegalArgumentException("slotIndex must be >= 0.");
      } else {
         Skin.Key key = this.keyPool.obtain();
         key.set(slotIndex, name);
         this.attachments.put(key, attachment);
      }
   }

   public Attachment getAttachment(int slotIndex, String name) {
      if (slotIndex < 0) {
         throw new IllegalArgumentException("slotIndex must be >= 0.");
      } else {
         lookup.set(slotIndex, name);
         return this.attachments.get(lookup);
      }
   }

   public void findNamesForSlot(int slotIndex, Array<String> names) {
      if (names == null) {
         throw new IllegalArgumentException("names cannot be null.");
      } else if (slotIndex < 0) {
         throw new IllegalArgumentException("slotIndex must be >= 0.");
      } else {
         for (Skin.Key key : this.attachments.keys()) {
            if (key.slotIndex == slotIndex) {
               names.add(key.name);
            }
         }
      }
   }

   public void findAttachmentsForSlot(int slotIndex, Array<Attachment> attachments) {
      if (attachments == null) {
         throw new IllegalArgumentException("attachments cannot be null.");
      } else if (slotIndex < 0) {
         throw new IllegalArgumentException("slotIndex must be >= 0.");
      } else {
         for (ObjectMap.Entry<Skin.Key, Attachment> entry : this.attachments.entries()) {
            if (entry.key.slotIndex == slotIndex) {
               attachments.add(entry.value);
            }
         }
      }
   }

   public void clear() {
      for (Skin.Key key : this.attachments.keys()) {
         this.keyPool.free(key);
      }

      this.attachments.clear();
   }

   public String getName() {
      return this.name;
   }

   public Iterator<Attachment> attachments() {
      return this.attachments.values().iterator();
   }

   @Override
   public String toString() {
      return this.name;
   }

   void attachAll(Skeleton skeleton, Skin oldSkin) {
      for (ObjectMap.Entry<Skin.Key, Attachment> entry : oldSkin.attachments.entries()) {
         int slotIndex = entry.key.slotIndex;
         Slot slot = skeleton.slots.get(slotIndex);
         if (slot.attachment == entry.value) {
            Attachment attachment = this.getAttachment(slotIndex, entry.key.name);
            if (attachment != null) {
               slot.setAttachment(attachment);
            }
         }
      }
   }

   static class Key {
      int slotIndex;
      String name;
      int hashCode;

      public void set(int slotIndex, String name) {
         if (name == null) {
            throw new IllegalArgumentException("name cannot be null.");
         } else {
            this.slotIndex = slotIndex;
            this.name = name;
            this.hashCode = 31 * (31 + name.hashCode()) + slotIndex;
         }
      }

      @Override
      public int hashCode() {
         return this.hashCode;
      }

      @Override
      public boolean equals(Object object) {
         if (object == null) {
            return false;
         } else {
            Skin.Key other = (Skin.Key)object;
            return this.slotIndex != other.slotIndex ? false : this.name.equals(other.name);
         }
      }

      @Override
      public String toString() {
         return this.slotIndex + ":" + this.name;
      }
   }
}
