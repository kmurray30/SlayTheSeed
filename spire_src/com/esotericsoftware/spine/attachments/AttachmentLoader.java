package com.esotericsoftware.spine.attachments;

import com.esotericsoftware.spine.Skin;

public interface AttachmentLoader {
   RegionAttachment newRegionAttachment(Skin var1, String var2, String var3);

   MeshAttachment newMeshAttachment(Skin var1, String var2, String var3);

   BoundingBoxAttachment newBoundingBoxAttachment(Skin var1, String var2);

   PathAttachment newPathAttachment(Skin var1, String var2);
}
