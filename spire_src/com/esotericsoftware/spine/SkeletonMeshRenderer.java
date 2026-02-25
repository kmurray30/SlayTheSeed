package com.esotericsoftware.spine;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.SkeletonAttachment;

public class SkeletonMeshRenderer extends SkeletonRenderer<PolygonSpriteBatch> {
   private static final short[] quadTriangles = new short[]{0, 1, 2, 2, 3, 0};

   public void draw(PolygonSpriteBatch batch, Skeleton skeleton) {
      boolean premultipliedAlpha = this.premultipliedAlpha;
      float[] vertices = null;
      short[] triangles = null;
      Array<Slot> drawOrder = skeleton.drawOrder;
      int i = 0;

      for (int n = drawOrder.size; i < n; i++) {
         Slot slot = drawOrder.get(i);
         Attachment attachment = slot.attachment;
         Texture texture = null;
         if (attachment instanceof RegionAttachment) {
            RegionAttachment region = (RegionAttachment)attachment;
            vertices = region.updateWorldVertices(slot, premultipliedAlpha);
            triangles = quadTriangles;
            texture = region.getRegion().getTexture();
         } else if (attachment instanceof MeshAttachment) {
            MeshAttachment mesh = (MeshAttachment)attachment;
            vertices = mesh.updateWorldVertices(slot, premultipliedAlpha);
            triangles = mesh.getTriangles();
            texture = mesh.getRegion().getTexture();
         } else if (attachment instanceof SkeletonAttachment) {
            Skeleton attachmentSkeleton = ((SkeletonAttachment)attachment).getSkeleton();
            if (attachmentSkeleton == null) {
               continue;
            }

            Bone bone = slot.getBone();
            Bone rootBone = attachmentSkeleton.getRootBone();
            float oldScaleX = rootBone.getScaleX();
            float oldScaleY = rootBone.getScaleY();
            float oldRotation = rootBone.getRotation();
            attachmentSkeleton.setPosition(skeleton.getX() + bone.getWorldX(), skeleton.getY() + bone.getWorldY());
            rootBone.setRotation(oldRotation + bone.getWorldRotationX());
            attachmentSkeleton.updateWorldTransform();
            this.draw(batch, attachmentSkeleton);
            attachmentSkeleton.setPosition(0.0F, 0.0F);
            rootBone.setScaleX(oldScaleX);
            rootBone.setScaleY(oldScaleY);
            rootBone.setRotation(oldRotation);
         }

         if (texture != null) {
            BlendMode blendMode = slot.data.getBlendMode();
            batch.setBlendFunction(blendMode.getSource(premultipliedAlpha), blendMode.getDest());
            batch.draw(texture, vertices, 0, vertices.length, triangles, 0, triangles.length);
         }
      }
   }
}
