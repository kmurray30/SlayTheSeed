package com.esotericsoftware.spine;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.DataInput;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.SerializationException;
import com.esotericsoftware.spine.attachments.AtlasAttachmentLoader;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.AttachmentLoader;
import com.esotericsoftware.spine.attachments.AttachmentType;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.VertexAttachment;
import java.io.EOFException;
import java.io.IOException;

public class SkeletonBinary {
   public static final int BONE_ROTATE = 0;
   public static final int BONE_TRANSLATE = 1;
   public static final int BONE_SCALE = 2;
   public static final int BONE_SHEAR = 3;
   public static final int SLOT_ATTACHMENT = 0;
   public static final int SLOT_COLOR = 1;
   public static final int PATH_POSITION = 0;
   public static final int PATH_SPACING = 1;
   public static final int PATH_MIX = 2;
   public static final int CURVE_LINEAR = 0;
   public static final int CURVE_STEPPED = 1;
   public static final int CURVE_BEZIER = 2;
   private static final Color tempColor = new Color();
   private final AttachmentLoader attachmentLoader;
   private float scale = 1.0F;
   private Array<SkeletonJson.LinkedMesh> linkedMeshes = new Array<>();

   public SkeletonBinary(TextureAtlas atlas) {
      this.attachmentLoader = new AtlasAttachmentLoader(atlas);
   }

   public SkeletonBinary(AttachmentLoader attachmentLoader) {
      if (attachmentLoader == null) {
         throw new IllegalArgumentException("attachmentLoader cannot be null.");
      } else {
         this.attachmentLoader = attachmentLoader;
      }
   }

   public float getScale() {
      return this.scale;
   }

   public void setScale(float scale) {
      this.scale = scale;
   }

   public SkeletonData readSkeletonData(FileHandle file) {
      if (file == null) {
         throw new IllegalArgumentException("file cannot be null.");
      } else {
         float scale = this.scale;
         SkeletonData skeletonData = new SkeletonData();
         skeletonData.name = file.nameWithoutExtension();
         DataInput input = new DataInput(file.read(512)) {
            private char[] chars = new char[32];

            @Override
            public String readString() throws IOException {
               int byteCount = this.readInt(true);
               switch (byteCount) {
                  case 0:
                     return null;
                  case 1:
                     return "";
                  default:
                     if (this.chars.length < --byteCount) {
                        this.chars = new char[byteCount];
                     }

                     char[] chars = this.chars;
                     int charCount = 0;
                     int i = 0;

                     while (i < byteCount) {
                        int b = this.read();
                        switch (b >> 4) {
                           case -1:
                              throw new EOFException();
                           case 12:
                           case 13:
                              chars[charCount++] = (char)((b & 31) << 6 | this.read() & 63);
                              i += 2;
                              break;
                           case 14:
                              chars[charCount++] = (char)((b & 15) << 12 | (this.read() & 63) << 6 | this.read() & 63);
                              i += 3;
                              break;
                           default:
                              chars[charCount++] = (char)b;
                              i++;
                        }
                     }

                     return new String(chars, 0, charCount);
               }
            }
         };

         try {
            skeletonData.hash = input.readString();
            if (skeletonData.hash.isEmpty()) {
               skeletonData.hash = null;
            }

            skeletonData.version = input.readString();
            if (skeletonData.version.isEmpty()) {
               skeletonData.version = null;
            }

            skeletonData.width = input.readFloat();
            skeletonData.height = input.readFloat();
            boolean nonessential = input.readBoolean();
            if (nonessential) {
               skeletonData.imagesPath = input.readString();
               if (skeletonData.imagesPath.isEmpty()) {
                  skeletonData.imagesPath = null;
               }
            }

            int i = 0;

            for (int n = input.readInt(true); i < n; i++) {
               String name = input.readString();
               BoneData parent = i == 0 ? null : skeletonData.bones.get(input.readInt(true));
               BoneData data = new BoneData(i, name, parent);
               data.rotation = input.readFloat();
               data.x = input.readFloat() * scale;
               data.y = input.readFloat() * scale;
               data.scaleX = input.readFloat();
               data.scaleY = input.readFloat();
               data.shearX = input.readFloat();
               data.shearY = input.readFloat();
               data.length = input.readFloat() * scale;
               data.inheritRotation = input.readBoolean();
               data.inheritScale = input.readBoolean();
               if (nonessential) {
                  Color.rgba8888ToColor(data.color, input.readInt());
               }

               skeletonData.bones.add(data);
            }

            i = 0;

            for (int n = input.readInt(true); i < n; i++) {
               String slotName = input.readString();
               BoneData boneData = skeletonData.bones.get(input.readInt(true));
               SlotData data = new SlotData(i, slotName, boneData);
               Color.rgba8888ToColor(data.color, input.readInt());
               data.attachmentName = input.readString();
               data.blendMode = BlendMode.values[input.readInt(true)];
               skeletonData.slots.add(data);
            }

            i = 0;

            for (int n = input.readInt(true); i < n; i++) {
               IkConstraintData data = new IkConstraintData(input.readString());
               int ii = 0;

               for (int nn = input.readInt(true); ii < nn; ii++) {
                  data.bones.add(skeletonData.bones.get(input.readInt(true)));
               }

               data.target = skeletonData.bones.get(input.readInt(true));
               data.mix = input.readFloat();
               data.bendDirection = input.readByte();
               skeletonData.ikConstraints.add(data);
            }

            i = 0;

            for (int n = input.readInt(true); i < n; i++) {
               TransformConstraintData data = new TransformConstraintData(input.readString());
               int ii = 0;

               for (int nn = input.readInt(true); ii < nn; ii++) {
                  data.bones.add(skeletonData.bones.get(input.readInt(true)));
               }

               data.target = skeletonData.bones.get(input.readInt(true));
               data.offsetRotation = input.readFloat();
               data.offsetX = input.readFloat() * scale;
               data.offsetY = input.readFloat() * scale;
               data.offsetScaleX = input.readFloat();
               data.offsetScaleY = input.readFloat();
               data.offsetShearY = input.readFloat();
               data.rotateMix = input.readFloat();
               data.translateMix = input.readFloat();
               data.scaleMix = input.readFloat();
               data.shearMix = input.readFloat();
               skeletonData.transformConstraints.add(data);
            }

            i = 0;

            for (int n = input.readInt(true); i < n; i++) {
               PathConstraintData data = new PathConstraintData(input.readString());
               int ii = 0;

               for (int nn = input.readInt(true); ii < nn; ii++) {
                  data.bones.add(skeletonData.bones.get(input.readInt(true)));
               }

               data.target = skeletonData.slots.get(input.readInt(true));
               data.positionMode = PathConstraintData.PositionMode.values[input.readInt(true)];
               data.spacingMode = PathConstraintData.SpacingMode.values[input.readInt(true)];
               data.rotateMode = PathConstraintData.RotateMode.values[input.readInt(true)];
               data.offsetRotation = input.readFloat();
               data.position = input.readFloat();
               if (data.positionMode == PathConstraintData.PositionMode.fixed) {
                  data.position *= scale;
               }

               data.spacing = input.readFloat();
               if (data.spacingMode == PathConstraintData.SpacingMode.length || data.spacingMode == PathConstraintData.SpacingMode.fixed) {
                  data.spacing *= scale;
               }

               data.rotateMix = input.readFloat();
               data.translateMix = input.readFloat();
               skeletonData.pathConstraints.add(data);
            }

            Skin defaultSkin = this.readSkin(input, "default", nonessential);
            if (defaultSkin != null) {
               skeletonData.defaultSkin = defaultSkin;
               skeletonData.skins.add(defaultSkin);
            }

            int ix = 0;

            for (int n = input.readInt(true); ix < n; ix++) {
               skeletonData.skins.add(this.readSkin(input, input.readString(), nonessential));
            }

            ix = 0;

            for (int n = this.linkedMeshes.size; ix < n; ix++) {
               SkeletonJson.LinkedMesh linkedMesh = this.linkedMeshes.get(ix);
               Skin skin = linkedMesh.skin == null ? skeletonData.getDefaultSkin() : skeletonData.findSkin(linkedMesh.skin);
               if (skin == null) {
                  throw new SerializationException("Skin not found: " + linkedMesh.skin);
               }

               Attachment parent = skin.getAttachment(linkedMesh.slotIndex, linkedMesh.parent);
               if (parent == null) {
                  throw new SerializationException("Parent mesh not found: " + linkedMesh.parent);
               }

               linkedMesh.mesh.setParentMesh((MeshAttachment)parent);
               linkedMesh.mesh.updateUVs();
            }

            this.linkedMeshes.clear();
            ix = 0;

            for (int n = input.readInt(true); ix < n; ix++) {
               EventData data = new EventData(input.readString());
               data.intValue = input.readInt(false);
               data.floatValue = input.readFloat();
               data.stringValue = input.readString();
               skeletonData.events.add(data);
            }

            ix = 0;

            for (int n = input.readInt(true); ix < n; ix++) {
               this.readAnimation(input.readString(), input, skeletonData);
            }
         } catch (IOException var19) {
            throw new SerializationException("Error reading skeleton file.", var19);
         } finally {
            try {
               input.close();
            } catch (IOException var18) {
            }
         }

         skeletonData.bones.shrink();
         skeletonData.slots.shrink();
         skeletonData.skins.shrink();
         skeletonData.events.shrink();
         skeletonData.animations.shrink();
         skeletonData.ikConstraints.shrink();
         return skeletonData;
      }
   }

   private Skin readSkin(DataInput input, String skinName, boolean nonessential) throws IOException {
      int slotCount = input.readInt(true);
      if (slotCount == 0) {
         return null;
      } else {
         Skin skin = new Skin(skinName);

         for (int i = 0; i < slotCount; i++) {
            int slotIndex = input.readInt(true);
            int ii = 0;

            for (int nn = input.readInt(true); ii < nn; ii++) {
               String name = input.readString();
               skin.addAttachment(slotIndex, name, this.readAttachment(input, skin, slotIndex, name, nonessential));
            }
         }

         return skin;
      }
   }

   private Attachment readAttachment(DataInput input, Skin skin, int slotIndex, String attachmentName, boolean nonessential) throws IOException {
      float scale = this.scale;
      String name = input.readString();
      if (name == null) {
         name = attachmentName;
      }

      AttachmentType type = AttachmentType.values[input.readByte()];
      switch (type) {
         case region:
            String pathxx = input.readString();
            float rotation = input.readFloat();
            float x = input.readFloat();
            float y = input.readFloat();
            float scaleX = input.readFloat();
            float scaleY = input.readFloat();
            float widthxx = input.readFloat();
            float heightxx = input.readFloat();
            int colorxxx = input.readInt();
            if (pathxx == null) {
               pathxx = name;
            }

            RegionAttachment region = this.attachmentLoader.newRegionAttachment(skin, name, pathxx);
            if (region == null) {
               return null;
            }

            region.setPath(pathxx);
            region.setX(x * scale);
            region.setY(y * scale);
            region.setScaleX(scaleX);
            region.setScaleY(scaleY);
            region.setRotation(rotation);
            region.setWidth(widthxx * scale);
            region.setHeight(heightxx * scale);
            Color.rgba8888ToColor(region.getColor(), colorxxx);
            region.updateOffset();
            return region;
         case boundingbox:
            int vertexCountx = input.readInt(true);
            SkeletonBinary.Vertices verticesx = this.readVertices(input, vertexCountx);
            int colorxx = nonessential ? input.readInt() : 0;
            BoundingBoxAttachment box = this.attachmentLoader.newBoundingBoxAttachment(skin, name);
            if (box == null) {
               return null;
            }

            box.setWorldVerticesLength(vertexCountx << 1);
            box.setVertices(verticesx.vertices);
            box.setBones(verticesx.bones);
            if (nonessential) {
               Color.rgba8888ToColor(box.getColor(), colorxx);
            }

            return box;
         case mesh:
            String pathx = input.readString();
            int colorx = input.readInt();
            int vertexCount = input.readInt(true);
            float[] uvs = this.readFloatArray(input, vertexCount << 1, 1.0F);
            short[] triangles = this.readShortArray(input);
            SkeletonBinary.Vertices vertices = this.readVertices(input, vertexCount);
            int hullLength = input.readInt(true);
            short[] edges = null;
            float widthx = 0.0F;
            float heightx = 0.0F;
            if (nonessential) {
               edges = this.readShortArray(input);
               widthx = input.readFloat();
               heightx = input.readFloat();
            }

            if (pathx == null) {
               pathx = name;
            }

            MeshAttachment meshx = this.attachmentLoader.newMeshAttachment(skin, name, pathx);
            if (meshx == null) {
               return null;
            }

            meshx.setPath(pathx);
            Color.rgba8888ToColor(meshx.getColor(), colorx);
            meshx.setBones(vertices.bones);
            meshx.setVertices(vertices.vertices);
            meshx.setWorldVerticesLength(vertexCount << 1);
            meshx.setTriangles(triangles);
            meshx.setRegionUVs(uvs);
            meshx.updateUVs();
            meshx.setHullLength(hullLength << 1);
            if (nonessential) {
               meshx.setEdges(edges);
               meshx.setWidth(widthx * scale);
               meshx.setHeight(heightx * scale);
            }

            return meshx;
         case linkedmesh:
            String path = input.readString();
            int color = input.readInt();
            String skinName = input.readString();
            String parent = input.readString();
            boolean inheritDeform = input.readBoolean();
            float width = 0.0F;
            float height = 0.0F;
            if (nonessential) {
               width = input.readFloat();
               height = input.readFloat();
            }

            if (path == null) {
               path = name;
            }

            MeshAttachment mesh = this.attachmentLoader.newMeshAttachment(skin, name, path);
            if (mesh == null) {
               return null;
            }

            mesh.setPath(path);
            Color.rgba8888ToColor(mesh.getColor(), color);
            mesh.setInheritDeform(inheritDeform);
            if (nonessential) {
               mesh.setWidth(width * scale);
               mesh.setHeight(height * scale);
            }

            this.linkedMeshes.add(new SkeletonJson.LinkedMesh(mesh, skinName, slotIndex, parent));
            return mesh;
         case path:
            boolean closed = input.readBoolean();
            boolean constantSpeed = input.readBoolean();
            int pathVertexCount = input.readInt(true);
            SkeletonBinary.Vertices pathVertices = this.readVertices(input, pathVertexCount);
            float[] lengths = new float[pathVertexCount / 3];
            int i = 0;

            for (int n = lengths.length; i < n; i++) {
               lengths[i] = input.readFloat() * scale;
            }

            i = nonessential ? input.readInt() : 0;
            PathAttachment pathAttachment = this.attachmentLoader.newPathAttachment(skin, name);
            if (pathAttachment == null) {
               return null;
            } else {
               pathAttachment.setClosed(closed);
               pathAttachment.setConstantSpeed(constantSpeed);
               pathAttachment.setWorldVerticesLength(pathVertexCount << 1);
               pathAttachment.setVertices(pathVertices.vertices);
               pathAttachment.setBones(pathVertices.bones);
               pathAttachment.setLengths(lengths);
               if (nonessential) {
                  Color.rgba8888ToColor(pathAttachment.getColor(), i);
               }

               return pathAttachment;
            }
         default:
            return null;
      }
   }

   private SkeletonBinary.Vertices readVertices(DataInput input, int vertexCount) throws IOException {
      int verticesLength = vertexCount << 1;
      SkeletonBinary.Vertices vertices = new SkeletonBinary.Vertices();
      if (!input.readBoolean()) {
         vertices.vertices = this.readFloatArray(input, verticesLength, this.scale);
         return vertices;
      } else {
         FloatArray weights = new FloatArray(verticesLength * 3 * 3);
         IntArray bonesArray = new IntArray(verticesLength * 3);

         for (int i = 0; i < vertexCount; i++) {
            int boneCount = input.readInt(true);
            bonesArray.add(boneCount);

            for (int ii = 0; ii < boneCount; ii++) {
               bonesArray.add(input.readInt(true));
               weights.add(input.readFloat() * this.scale);
               weights.add(input.readFloat() * this.scale);
               weights.add(input.readFloat());
            }
         }

         vertices.vertices = weights.toArray();
         vertices.bones = bonesArray.toArray();
         return vertices;
      }
   }

   private float[] readFloatArray(DataInput input, int n, float scale) throws IOException {
      float[] array = new float[n];
      if (scale == 1.0F) {
         for (int i = 0; i < n; i++) {
            array[i] = input.readFloat();
         }
      } else {
         for (int i = 0; i < n; i++) {
            array[i] = input.readFloat() * scale;
         }
      }

      return array;
   }

   private short[] readShortArray(DataInput input) throws IOException {
      int n = input.readInt(true);
      short[] array = new short[n];

      for (int i = 0; i < n; i++) {
         array[i] = input.readShort();
      }

      return array;
   }

   private void readAnimation(String name, DataInput input, SkeletonData skeletonData) {
      Array<Animation.Timeline> timelines = new Array<>();
      float scale = this.scale;
      float duration = 0.0F;

      try {
         int i = 0;

         for (int n = input.readInt(true); i < n; i++) {
            int slotIndex = input.readInt(true);
            int ii = 0;

            for (int nn = input.readInt(true); ii < nn; ii++) {
               int timelineType = input.readByte();
               int frameCount = input.readInt(true);
               switch (timelineType) {
                  case 0:
                     Animation.AttachmentTimeline timeline = new Animation.AttachmentTimeline(frameCount);
                     timeline.slotIndex = slotIndex;

                     for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                        timeline.setFrame(frameIndex, input.readFloat(), input.readString());
                     }

                     timelines.add(timeline);
                     duration = Math.max(duration, timeline.getFrames()[frameCount - 1]);
                     break;
                  case 1:
                     Animation.ColorTimeline colorTimeline = new Animation.ColorTimeline(frameCount);
                     colorTimeline.slotIndex = slotIndex;

                     for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                        float time = input.readFloat();
                        Color.rgba8888ToColor(tempColor, input.readInt());
                        colorTimeline.setFrame(frameIndex, time, tempColor.r, tempColor.g, tempColor.b, tempColor.a);
                        if (frameIndex < frameCount - 1) {
                           this.readCurve(input, frameIndex, colorTimeline);
                        }
                     }

                     timelines.add(colorTimeline);
                     duration = Math.max(duration, colorTimeline.getFrames()[(frameCount - 1) * 5]);
               }
            }
         }

         i = 0;

         for (int n = input.readInt(true); i < n; i++) {
            int boneIndex = input.readInt(true);
            int ii = 0;

            for (int nn = input.readInt(true); ii < nn; ii++) {
               int timelineType = input.readByte();
               int frameCount = input.readInt(true);
               switch (timelineType) {
                  case 0:
                     Animation.RotateTimeline rotateTimeline = new Animation.RotateTimeline(frameCount);
                     rotateTimeline.boneIndex = boneIndex;
                     int rotateFrameIndex = 0;

                     for (; rotateFrameIndex < frameCount; rotateFrameIndex++) {
                        rotateTimeline.setFrame(rotateFrameIndex, input.readFloat(), input.readFloat());
                        if (rotateFrameIndex < frameCount - 1) {
                           this.readCurve(input, rotateFrameIndex, rotateTimeline);
                        }
                     }

                     timelines.add(rotateTimeline);
                     duration = Math.max(duration, rotateTimeline.getFrames()[(frameCount - 1) * 2]);
                     break;
                  case 1:
                  case 2:
                  case 3:
                     float timelineScale = 1.0F;
                     Animation.TranslateTimeline translateTimeline;
                     if (timelineType == 2) {
                        translateTimeline = new Animation.ScaleTimeline(frameCount);
                     } else if (timelineType == 3) {
                        translateTimeline = new Animation.ShearTimeline(frameCount);
                     } else {
                        translateTimeline = new Animation.TranslateTimeline(frameCount);
                        timelineScale = scale;
                     }

                     translateTimeline.boneIndex = boneIndex;

                     for (int translateFrameIndex = 0; translateFrameIndex < frameCount; translateFrameIndex++) {
                        translateTimeline.setFrame(translateFrameIndex, input.readFloat(), input.readFloat() * timelineScale, input.readFloat() * timelineScale);
                        if (translateFrameIndex < frameCount - 1) {
                           this.readCurve(input, translateFrameIndex, translateTimeline);
                        }
                     }

                     timelines.add(translateTimeline);
                     duration = Math.max(duration, translateTimeline.getFrames()[(frameCount - 1) * 3]);
               }
            }
         }

         i = 0;

         for (int n = input.readInt(true); i < n; i++) {
            int index = input.readInt(true);
            int frameCount = input.readInt(true);
            Animation.IkConstraintTimeline timeline = new Animation.IkConstraintTimeline(frameCount);
            timeline.ikConstraintIndex = index;

            for (int frameIndexxx = 0; frameIndexxx < frameCount; frameIndexxx++) {
               timeline.setFrame(frameIndexxx, input.readFloat(), input.readFloat(), input.readByte());
               if (frameIndexxx < frameCount - 1) {
                  this.readCurve(input, frameIndexxx, timeline);
               }
            }

            timelines.add(timeline);
            duration = Math.max(duration, timeline.getFrames()[(frameCount - 1) * 3]);
         }

         i = 0;

         for (int n = input.readInt(true); i < n; i++) {
            int index = input.readInt(true);
            int frameCount = input.readInt(true);
            Animation.TransformConstraintTimeline timeline = new Animation.TransformConstraintTimeline(frameCount);
            timeline.transformConstraintIndex = index;

            for (int frameIndexxxx = 0; frameIndexxxx < frameCount; frameIndexxxx++) {
               timeline.setFrame(frameIndexxxx, input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat());
               if (frameIndexxxx < frameCount - 1) {
                  this.readCurve(input, frameIndexxxx, timeline);
               }
            }

            timelines.add(timeline);
            duration = Math.max(duration, timeline.getFrames()[(frameCount - 1) * 5]);
         }

         i = 0;

         for (int n = input.readInt(true); i < n; i++) {
            int index = input.readInt(true);
            PathConstraintData data = skeletonData.getPathConstraints().get(index);
            int ii = 0;

            for (int nn = input.readInt(true); ii < nn; ii++) {
               int timelineType = input.readByte();
               int frameCount = input.readInt(true);
               switch (timelineType) {
                  case 0:
                  case 1:
                     float timelineScale = 1.0F;
                     Animation.PathConstraintPositionTimeline timeline;
                     if (timelineType == 1) {
                        timeline = new Animation.PathConstraintSpacingTimeline(frameCount);
                        if (data.spacingMode == PathConstraintData.SpacingMode.length || data.spacingMode == PathConstraintData.SpacingMode.fixed) {
                           timelineScale = scale;
                        }
                     } else {
                        timeline = new Animation.PathConstraintPositionTimeline(frameCount);
                        if (data.positionMode == PathConstraintData.PositionMode.fixed) {
                           timelineScale = scale;
                        }
                     }

                     timeline.pathConstraintIndex = index;

                     for (int frameIndexxxxxx = 0; frameIndexxxxxx < frameCount; frameIndexxxxxx++) {
                        timeline.setFrame(frameIndexxxxxx, input.readFloat(), input.readFloat() * timelineScale);
                        if (frameIndexxxxxx < frameCount - 1) {
                           this.readCurve(input, frameIndexxxxxx, timeline);
                        }
                     }

                     timelines.add(timeline);
                     duration = Math.max(duration, timeline.getFrames()[(frameCount - 1) * 2]);
                     break;
                  case 2:
                     Animation.PathConstraintMixTimeline pathMixTimeline = new Animation.PathConstraintMixTimeline(frameCount);
                     pathMixTimeline.pathConstraintIndex = index;

                     for (int frameIndexxxxx = 0; frameIndexxxxx < frameCount; frameIndexxxxx++) {
                        pathMixTimeline.setFrame(frameIndexxxxx, input.readFloat(), input.readFloat(), input.readFloat());
                        if (frameIndexxxxx < frameCount - 1) {
                           this.readCurve(input, frameIndexxxxx, pathMixTimeline);
                        }
                     }

                     timelines.add(pathMixTimeline);
                     duration = Math.max(duration, pathMixTimeline.getFrames()[(frameCount - 1) * 3]);
               }
            }
         }

         i = 0;

         for (int n = input.readInt(true); i < n; i++) {
            Skin skin = skeletonData.skins.get(input.readInt(true));
            int ii = 0;

            for (int nn = input.readInt(true); ii < nn; ii++) {
               int slotIndex = input.readInt(true);
               int iii = 0;

               for (int nnn = input.readInt(true); iii < nnn; iii++) {
                  VertexAttachment attachment = (VertexAttachment)skin.getAttachment(slotIndex, input.readString());
                  boolean weighted = attachment.getBones() != null;
                  float[] vertices = attachment.getVertices();
                  int deformLength = weighted ? vertices.length / 3 * 2 : vertices.length;
                  int frameCount = input.readInt(true);
                  Animation.DeformTimeline timeline = new Animation.DeformTimeline(frameCount);
                  timeline.slotIndex = slotIndex;
                  timeline.attachment = attachment;

                  for (int frameIndexxxxxxx = 0; frameIndexxxxxxx < frameCount; frameIndexxxxxxx++) {
                     float time = input.readFloat();
                     int end = input.readInt(true);
                     float[] deform;
                     if (end == 0) {
                        deform = weighted ? new float[deformLength] : vertices;
                     } else {
                        deform = new float[deformLength];
                        int start = input.readInt(true);
                        end += start;
                        if (scale == 1.0F) {
                           for (int v = start; v < end; v++) {
                              deform[v] = input.readFloat();
                           }
                        } else {
                           for (int v = start; v < end; v++) {
                              deform[v] = input.readFloat() * scale;
                           }
                        }

                        if (!weighted) {
                           int v = 0;

                           for (int vn = deform.length; v < vn; v++) {
                              deform[v] += vertices[v];
                           }
                        }
                     }

                     timeline.setFrame(frameIndexxxxxxx, time, deform);
                     if (frameIndexxxxxxx < frameCount - 1) {
                        this.readCurve(input, frameIndexxxxxxx, timeline);
                     }
                  }

                  timelines.add(timeline);
                  duration = Math.max(duration, timeline.getFrames()[frameCount - 1]);
               }
            }
         }

         i = input.readInt(true);
         if (i > 0) {
            Animation.DrawOrderTimeline timeline = new Animation.DrawOrderTimeline(i);
            int slotCount = skeletonData.slots.size;

            for (int ix = 0; ix < i; ix++) {
               float timex = input.readFloat();
               int offsetCount = input.readInt(true);
               int[] drawOrder = new int[slotCount];

               for (int ii = slotCount - 1; ii >= 0; ii--) {
                  drawOrder[ii] = -1;
               }

               int[] unchanged = new int[slotCount - offsetCount];
               int originalIndex = 0;
               int unchangedIndex = 0;

               for (int ii = 0; ii < offsetCount; ii++) {
                  int slotIndex = input.readInt(true);

                  while (originalIndex != slotIndex) {
                     unchanged[unchangedIndex++] = originalIndex++;
                  }

                  drawOrder[originalIndex + input.readInt(true)] = originalIndex++;
               }

               while (originalIndex < slotCount) {
                  unchanged[unchangedIndex++] = originalIndex++;
               }

               for (int ii = slotCount - 1; ii >= 0; ii--) {
                  if (drawOrder[ii] == -1) {
                     drawOrder[ii] = unchanged[--unchangedIndex];
                  }
               }

               timeline.setFrame(ix, timex, drawOrder);
            }

            timelines.add(timeline);
            duration = Math.max(duration, timeline.getFrames()[i - 1]);
         }

         int eventCount = input.readInt(true);
         if (eventCount > 0) {
            Animation.EventTimeline timeline = new Animation.EventTimeline(eventCount);

            for (int ix = 0; ix < eventCount; ix++) {
               float timex = input.readFloat();
               EventData eventData = skeletonData.events.get(input.readInt(true));
               Event event = new Event(timex, eventData);
               event.intValue = input.readInt(false);
               event.floatValue = input.readFloat();
               event.stringValue = input.readBoolean() ? input.readString() : eventData.stringValue;
               timeline.setFrame(ix, event);
            }

            timelines.add(timeline);
            duration = Math.max(duration, timeline.getFrames()[eventCount - 1]);
         }
      } catch (IOException var28) {
         throw new SerializationException("Error reading skeleton file.", var28);
      }

      timelines.shrink();
      skeletonData.animations.add(new Animation(name, timelines, duration));
   }

   private void readCurve(DataInput input, int frameIndex, Animation.CurveTimeline timeline) throws IOException {
      switch (input.readByte()) {
         case 1:
            timeline.setStepped(frameIndex);
            break;
         case 2:
            this.setCurve(timeline, frameIndex, input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat());
      }
   }

   void setCurve(Animation.CurveTimeline timeline, int frameIndex, float cx1, float cy1, float cx2, float cy2) {
      timeline.setCurve(frameIndex, cx1, cy1, cx2, cy2);
   }

   static class Vertices {
      int[] bones;
      float[] vertices;
   }
}
