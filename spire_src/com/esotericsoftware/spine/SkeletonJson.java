package com.esotericsoftware.spine;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
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

public class SkeletonJson {
   private final AttachmentLoader attachmentLoader;
   private float scale = 1.0F;
   private Array<SkeletonJson.LinkedMesh> linkedMeshes = new Array<>();

   public SkeletonJson(TextureAtlas atlas) {
      this.attachmentLoader = new AtlasAttachmentLoader(atlas);
   }

   public SkeletonJson(AttachmentLoader attachmentLoader) {
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
         JsonValue root = new JsonReader().parse(file);
         JsonValue skeletonMap = root.get("skeleton");
         if (skeletonMap != null) {
            skeletonData.hash = skeletonMap.getString("hash", null);
            skeletonData.version = skeletonMap.getString("spine", null);
            skeletonData.width = skeletonMap.getFloat("width", 0.0F);
            skeletonData.height = skeletonMap.getFloat("height", 0.0F);
            skeletonData.imagesPath = skeletonMap.getString("images", null);
         }

         for (JsonValue boneMap = root.getChild("bones"); boneMap != null; boneMap = boneMap.next) {
            BoneData parent = null;
            String parentName = boneMap.getString("parent", null);
            if (parentName != null) {
               parent = skeletonData.findBone(parentName);
               if (parent == null) {
                  throw new SerializationException("Parent bone not found: " + parentName);
               }
            }

            BoneData data = new BoneData(skeletonData.bones.size, boneMap.getString("name"), parent);
            data.length = boneMap.getFloat("length", 0.0F) * scale;
            data.x = boneMap.getFloat("x", 0.0F) * scale;
            data.y = boneMap.getFloat("y", 0.0F) * scale;
            data.rotation = boneMap.getFloat("rotation", 0.0F);
            data.scaleX = boneMap.getFloat("scaleX", 1.0F);
            data.scaleY = boneMap.getFloat("scaleY", 1.0F);
            data.shearX = boneMap.getFloat("shearX", 0.0F);
            data.shearY = boneMap.getFloat("shearY", 0.0F);
            data.inheritRotation = boneMap.getBoolean("inheritRotation", true);
            data.inheritScale = boneMap.getBoolean("inheritScale", true);
            String color = boneMap.getString("color", null);
            if (color != null) {
               data.getColor().set(Color.valueOf(color));
            }

            skeletonData.bones.add(data);
         }

         for (JsonValue slotMap = root.getChild("slots"); slotMap != null; slotMap = slotMap.next) {
            String slotName = slotMap.getString("name");
            String boneName = slotMap.getString("bone");
            BoneData boneData = skeletonData.findBone(boneName);
            if (boneData == null) {
               throw new SerializationException("Slot bone not found: " + boneName);
            }

            SlotData data = new SlotData(skeletonData.slots.size, slotName, boneData);
            String color = slotMap.getString("color", null);
            if (color != null) {
               data.getColor().set(Color.valueOf(color));
            }

            data.attachmentName = slotMap.getString("attachment", null);
            data.blendMode = BlendMode.valueOf(slotMap.getString("blend", BlendMode.normal.name()));
            skeletonData.slots.add(data);
         }

         for (JsonValue constraintMap = root.getChild("ik"); constraintMap != null; constraintMap = constraintMap.next) {
            IkConstraintData data = new IkConstraintData(constraintMap.getString("name"));

            for (JsonValue boneMap = constraintMap.getChild("bones"); boneMap != null; boneMap = boneMap.next) {
               String boneNamex = boneMap.asString();
               BoneData bone = skeletonData.findBone(boneNamex);
               if (bone == null) {
                  throw new SerializationException("IK bone not found: " + boneNamex);
               }

               data.bones.add(bone);
            }

            String targetName = constraintMap.getString("target");
            data.target = skeletonData.findBone(targetName);
            if (data.target == null) {
               throw new SerializationException("IK target bone not found: " + targetName);
            }

            data.bendDirection = constraintMap.getBoolean("bendPositive", true) ? 1 : -1;
            data.mix = constraintMap.getFloat("mix", 1.0F);
            skeletonData.ikConstraints.add(data);
         }

         for (JsonValue constraintMap = root.getChild("transform"); constraintMap != null; constraintMap = constraintMap.next) {
            TransformConstraintData data = new TransformConstraintData(constraintMap.getString("name"));

            for (JsonValue boneMap = constraintMap.getChild("bones"); boneMap != null; boneMap = boneMap.next) {
               String boneNamex = boneMap.asString();
               BoneData bone = skeletonData.findBone(boneNamex);
               if (bone == null) {
                  throw new SerializationException("Transform constraint bone not found: " + boneNamex);
               }

               data.bones.add(bone);
            }

            String targetName = constraintMap.getString("target");
            data.target = skeletonData.findBone(targetName);
            if (data.target == null) {
               throw new SerializationException("Transform constraint target bone not found: " + targetName);
            }

            data.offsetRotation = constraintMap.getFloat("rotation", 0.0F);
            data.offsetX = constraintMap.getFloat("x", 0.0F) * scale;
            data.offsetY = constraintMap.getFloat("y", 0.0F) * scale;
            data.offsetScaleX = constraintMap.getFloat("scaleX", 0.0F);
            data.offsetScaleY = constraintMap.getFloat("scaleY", 0.0F);
            data.offsetShearY = constraintMap.getFloat("shearY", 0.0F);
            data.rotateMix = constraintMap.getFloat("rotateMix", 1.0F);
            data.translateMix = constraintMap.getFloat("translateMix", 1.0F);
            data.scaleMix = constraintMap.getFloat("scaleMix", 1.0F);
            data.shearMix = constraintMap.getFloat("shearMix", 1.0F);
            skeletonData.transformConstraints.add(data);
         }

         for (JsonValue constraintMap = root.getChild("path"); constraintMap != null; constraintMap = constraintMap.next) {
            PathConstraintData data = new PathConstraintData(constraintMap.getString("name"));

            for (JsonValue boneMap = constraintMap.getChild("bones"); boneMap != null; boneMap = boneMap.next) {
               String boneNamex = boneMap.asString();
               BoneData bone = skeletonData.findBone(boneNamex);
               if (bone == null) {
                  throw new SerializationException("Path bone not found: " + boneNamex);
               }

               data.bones.add(bone);
            }

            String targetName = constraintMap.getString("target");
            data.target = skeletonData.findSlot(targetName);
            if (data.target == null) {
               throw new SerializationException("Path target slot not found: " + targetName);
            }

            data.positionMode = PathConstraintData.PositionMode.valueOf(constraintMap.getString("positionMode", "percent"));
            data.spacingMode = PathConstraintData.SpacingMode.valueOf(constraintMap.getString("spacingMode", "length"));
            data.rotateMode = PathConstraintData.RotateMode.valueOf(constraintMap.getString("rotateMode", "tangent"));
            data.offsetRotation = constraintMap.getFloat("rotation", 0.0F);
            data.position = constraintMap.getFloat("position", 0.0F);
            if (data.positionMode == PathConstraintData.PositionMode.fixed) {
               data.position *= scale;
            }

            data.spacing = constraintMap.getFloat("spacing", 0.0F);
            if (data.spacingMode == PathConstraintData.SpacingMode.length || data.spacingMode == PathConstraintData.SpacingMode.fixed) {
               data.spacing *= scale;
            }

            data.rotateMix = constraintMap.getFloat("rotateMix", 1.0F);
            data.translateMix = constraintMap.getFloat("translateMix", 1.0F);
            skeletonData.pathConstraints.add(data);
         }

         for (JsonValue skinMap = root.getChild("skins"); skinMap != null; skinMap = skinMap.next) {
            Skin skin = new Skin(skinMap.name);

            for (JsonValue slotEntry = skinMap.child; slotEntry != null; slotEntry = slotEntry.next) {
               int slotIndex = skeletonData.findSlotIndex(slotEntry.name);
               if (slotIndex == -1) {
                  throw new SerializationException("Slot not found: " + slotEntry.name);
               }

               for (JsonValue entry = slotEntry.child; entry != null; entry = entry.next) {
                  try {
                     Attachment attachment = this.readAttachment(entry, skin, slotIndex, entry.name);
                     if (attachment != null) {
                        skin.addAttachment(slotIndex, entry.name, attachment);
                     }
                  } catch (Exception var13) {
                     throw new SerializationException("Error reading attachment: " + entry.name + ", skin: " + skin, var13);
                  }
               }
            }

            skeletonData.skins.add(skin);
            if (skin.name.equals("default")) {
               skeletonData.defaultSkin = skin;
            }
         }

         int i = 0;

         for (int n = this.linkedMeshes.size; i < n; i++) {
            SkeletonJson.LinkedMesh linkedMesh = this.linkedMeshes.get(i);
            Skin skin = linkedMesh.skin == null ? skeletonData.getDefaultSkin() : skeletonData.findSkin(linkedMesh.skin);
            if (skin == null) {
               throw new SerializationException("Skin not found: " + linkedMesh.skin);
            }

            Attachment parentx = skin.getAttachment(linkedMesh.slotIndex, linkedMesh.parent);
            if (parentx == null) {
               throw new SerializationException("Parent mesh not found: " + linkedMesh.parent);
            }

            linkedMesh.mesh.setParentMesh((MeshAttachment)parentx);
            linkedMesh.mesh.updateUVs();
         }

         this.linkedMeshes.clear();

         for (JsonValue eventMap = root.getChild("events"); eventMap != null; eventMap = eventMap.next) {
            EventData data = new EventData(eventMap.name);
            data.intValue = eventMap.getInt("int", 0);
            data.floatValue = eventMap.getFloat("float", 0.0F);
            data.stringValue = eventMap.getString("string", null);
            skeletonData.events.add(data);
         }

         for (JsonValue animationMap = root.getChild("animations"); animationMap != null; animationMap = animationMap.next) {
            try {
               this.readAnimation(animationMap, animationMap.name, skeletonData);
            } catch (Exception var12) {
               throw new SerializationException("Error reading animation: " + animationMap.name, var12);
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

   private Attachment readAttachment(JsonValue map, Skin skin, int slotIndex, String name) {
      float scale = this.scale;
      name = map.getString("name", name);
      String type = map.getString("type", AttachmentType.region.name());
      switch (AttachmentType.valueOf(type)) {
         case region:
            String pathxx = map.getString("path", name);
            RegionAttachment region = this.attachmentLoader.newRegionAttachment(skin, name, pathxx);
            if (region == null) {
               return null;
            }

            region.setPath(pathxx);
            region.setX(map.getFloat("x", 0.0F) * scale);
            region.setY(map.getFloat("y", 0.0F) * scale);
            region.setScaleX(map.getFloat("scaleX", 1.0F));
            region.setScaleY(map.getFloat("scaleY", 1.0F));
            region.setRotation(map.getFloat("rotation", 0.0F));
            region.setWidth(map.getFloat("width") * scale);
            region.setHeight(map.getFloat("height") * scale);
            String colorx = map.getString("color", null);
            if (colorx != null) {
               region.getColor().set(Color.valueOf(colorx));
            }

            region.updateOffset();
            return region;
         case boundingbox:
            BoundingBoxAttachment box = this.attachmentLoader.newBoundingBoxAttachment(skin, name);
            if (box == null) {
               return null;
            }

            this.readVertices(map, box, map.getInt("vertexCount") << 1);
            String colorx = map.getString("color", null);
            if (colorx != null) {
               box.getColor().set(Color.valueOf(colorx));
            }

            return box;
         case mesh:
         case linkedmesh:
            String pathx = map.getString("path", name);
            MeshAttachment mesh = this.attachmentLoader.newMeshAttachment(skin, name, pathx);
            if (mesh == null) {
               return null;
            } else {
               mesh.setPath(pathx);
               String color = map.getString("color", null);
               if (color != null) {
                  mesh.getColor().set(Color.valueOf(color));
               }

               mesh.setWidth(map.getFloat("width", 0.0F) * scale);
               mesh.setHeight(map.getFloat("height", 0.0F) * scale);
               String parent = map.getString("parent", null);
               if (parent != null) {
                  mesh.setInheritDeform(map.getBoolean("deform", true));
                  this.linkedMeshes.add(new SkeletonJson.LinkedMesh(mesh, map.getString("skin", null), slotIndex, parent));
                  return mesh;
               }

               float[] uvs = map.require("uvs").asFloatArray();
               this.readVertices(map, mesh, uvs.length);
               mesh.setTriangles(map.require("triangles").asShortArray());
               mesh.setRegionUVs(uvs);
               mesh.updateUVs();
               if (map.has("hull")) {
                  mesh.setHullLength(map.require("hull").asInt() * 2);
               }

               if (map.has("edges")) {
                  mesh.setEdges(map.require("edges").asShortArray());
               }

               return mesh;
            }
         case path:
            PathAttachment path = this.attachmentLoader.newPathAttachment(skin, name);
            if (path == null) {
               return null;
            }

            path.setClosed(map.getBoolean("closed", false));
            path.setConstantSpeed(map.getBoolean("constantSpeed", true));
            int vertexCount = map.getInt("vertexCount");
            this.readVertices(map, path, vertexCount << 1);
            float[] lengths = new float[vertexCount / 3];
            int i = 0;

            for (JsonValue curves = map.require("lengths").child; curves != null; curves = curves.next) {
               lengths[i++] = curves.asFloat() * scale;
            }

            path.setLengths(lengths);
            String color = map.getString("color", null);
            if (color != null) {
               path.getColor().set(Color.valueOf(color));
            }

            return path;
         default:
            return null;
      }
   }

   private void readVertices(JsonValue map, VertexAttachment attachment, int verticesLength) {
      attachment.setWorldVerticesLength(verticesLength);
      float[] vertices = map.require("vertices").asFloatArray();
      if (verticesLength == vertices.length) {
         if (this.scale != 1.0F) {
            int i = 0;

            for (int n = vertices.length; i < n; i++) {
               vertices[i] *= this.scale;
            }
         }

         attachment.setVertices(vertices);
      } else {
         FloatArray weights = new FloatArray(verticesLength * 3 * 3);
         IntArray bones = new IntArray(verticesLength * 3);
         int i = 0;
         int n = vertices.length;

         while (i < n) {
            int boneCount = (int)vertices[i++];
            bones.add(boneCount);

            for (int nn = i + boneCount * 4; i < nn; i += 4) {
               bones.add((int)vertices[i]);
               weights.add(vertices[i + 1] * this.scale);
               weights.add(vertices[i + 2] * this.scale);
               weights.add(vertices[i + 3]);
            }
         }

         attachment.setBones(bones.toArray());
         attachment.setVertices(weights.toArray());
      }
   }

   private void readAnimation(JsonValue map, String name, SkeletonData skeletonData) {
      float scale = this.scale;
      Array<Animation.Timeline> timelines = new Array<>();
      float duration = 0.0F;

      for (JsonValue slotMap = map.getChild("slots"); slotMap != null; slotMap = slotMap.next) {
         int slotIndex = skeletonData.findSlotIndex(slotMap.name);
         if (slotIndex == -1) {
            throw new SerializationException("Slot not found: " + slotMap.name);
         }

         for (JsonValue timelineMap = slotMap.child; timelineMap != null; timelineMap = timelineMap.next) {
            String timelineName = timelineMap.name;
            if (timelineName.equals("color")) {
               Animation.ColorTimeline timeline = new Animation.ColorTimeline(timelineMap.size);
               timeline.slotIndex = slotIndex;
               int frameIndex = 0;

               for (JsonValue valueMap = timelineMap.child; valueMap != null; valueMap = valueMap.next) {
                  Color color = Color.valueOf(valueMap.getString("color"));
                  timeline.setFrame(frameIndex, valueMap.getFloat("time"), color.r, color.g, color.b, color.a);
                  this.readCurve(valueMap, timeline, frameIndex);
                  frameIndex++;
               }

               timelines.add(timeline);
               duration = Math.max(duration, timeline.getFrames()[(timeline.getFrameCount() - 1) * 5]);
            } else {
               if (!timelineName.equals("attachment")) {
                  throw new RuntimeException("Invalid timeline type for a slot: " + timelineName + " (" + slotMap.name + ")");
               }

               Animation.AttachmentTimeline timeline = new Animation.AttachmentTimeline(timelineMap.size);
               timeline.slotIndex = slotIndex;
               int frameIndex = 0;

               for (JsonValue valueMap = timelineMap.child; valueMap != null; valueMap = valueMap.next) {
                  timeline.setFrame(frameIndex++, valueMap.getFloat("time"), valueMap.getString("name"));
               }

               timelines.add(timeline);
               duration = Math.max(duration, timeline.getFrames()[timeline.getFrameCount() - 1]);
            }
         }
      }

      for (JsonValue boneMap = map.getChild("bones"); boneMap != null; boneMap = boneMap.next) {
         int boneIndex = skeletonData.findBoneIndex(boneMap.name);
         if (boneIndex == -1) {
            throw new SerializationException("Bone not found: " + boneMap.name);
         }

         for (JsonValue timelineMapx = boneMap.child; timelineMapx != null; timelineMapx = timelineMapx.next) {
            String timelineName = timelineMapx.name;
            if (timelineName.equals("rotate")) {
               Animation.RotateTimeline timeline = new Animation.RotateTimeline(timelineMapx.size);
               timeline.boneIndex = boneIndex;
               int frameIndex = 0;

               for (JsonValue valueMap = timelineMapx.child; valueMap != null; valueMap = valueMap.next) {
                  timeline.setFrame(frameIndex, valueMap.getFloat("time"), valueMap.getFloat("angle"));
                  this.readCurve(valueMap, timeline, frameIndex);
                  frameIndex++;
               }

               timelines.add(timeline);
               duration = Math.max(duration, timeline.getFrames()[(timeline.getFrameCount() - 1) * 2]);
            } else {
               if (!timelineName.equals("translate") && !timelineName.equals("scale") && !timelineName.equals("shear")) {
                  throw new RuntimeException("Invalid timeline type for a bone: " + timelineName + " (" + boneMap.name + ")");
               }

               float timelineScale = 1.0F;
               Animation.TranslateTimeline timeline;
               if (timelineName.equals("scale")) {
                  timeline = new Animation.ScaleTimeline(timelineMapx.size);
               } else if (timelineName.equals("shear")) {
                  timeline = new Animation.ShearTimeline(timelineMapx.size);
               } else {
                  timeline = new Animation.TranslateTimeline(timelineMapx.size);
                  timelineScale = scale;
               }

               timeline.boneIndex = boneIndex;
               int frameIndex = 0;

               for (JsonValue valueMap = timelineMapx.child; valueMap != null; valueMap = valueMap.next) {
                  float x = valueMap.getFloat("x", 0.0F);
                  float y = valueMap.getFloat("y", 0.0F);
                  timeline.setFrame(frameIndex, valueMap.getFloat("time"), x * timelineScale, y * timelineScale);
                  this.readCurve(valueMap, timeline, frameIndex);
                  frameIndex++;
               }

               timelines.add(timeline);
               duration = Math.max(duration, timeline.getFrames()[(timeline.getFrameCount() - 1) * 3]);
            }
         }
      }

      for (JsonValue constraintMap = map.getChild("ik"); constraintMap != null; constraintMap = constraintMap.next) {
         IkConstraintData constraint = skeletonData.findIkConstraint(constraintMap.name);
         Animation.IkConstraintTimeline timeline = new Animation.IkConstraintTimeline(constraintMap.size);
         timeline.ikConstraintIndex = skeletonData.getIkConstraints().indexOf(constraint, true);
         int frameIndex = 0;

         for (JsonValue valueMap = constraintMap.child; valueMap != null; valueMap = valueMap.next) {
            timeline.setFrame(frameIndex, valueMap.getFloat("time"), valueMap.getFloat("mix", 1.0F), valueMap.getBoolean("bendPositive", true) ? 1 : -1);
            this.readCurve(valueMap, timeline, frameIndex);
            frameIndex++;
         }

         timelines.add(timeline);
         duration = Math.max(duration, timeline.getFrames()[(timeline.getFrameCount() - 1) * 3]);
      }

      for (JsonValue constraintMap = map.getChild("transform"); constraintMap != null; constraintMap = constraintMap.next) {
         TransformConstraintData constraint = skeletonData.findTransformConstraint(constraintMap.name);
         Animation.TransformConstraintTimeline timeline = new Animation.TransformConstraintTimeline(constraintMap.size);
         timeline.transformConstraintIndex = skeletonData.getTransformConstraints().indexOf(constraint, true);
         int frameIndex = 0;

         for (JsonValue valueMap = constraintMap.child; valueMap != null; valueMap = valueMap.next) {
            timeline.setFrame(
               frameIndex,
               valueMap.getFloat("time"),
               valueMap.getFloat("rotateMix", 1.0F),
               valueMap.getFloat("translateMix", 1.0F),
               valueMap.getFloat("scaleMix", 1.0F),
               valueMap.getFloat("shearMix", 1.0F)
            );
            this.readCurve(valueMap, timeline, frameIndex);
            frameIndex++;
         }

         timelines.add(timeline);
         duration = Math.max(duration, timeline.getFrames()[(timeline.getFrameCount() - 1) * 5]);
      }

      for (JsonValue constraintMap = map.getChild("paths"); constraintMap != null; constraintMap = constraintMap.next) {
         int index = skeletonData.findPathConstraintIndex(constraintMap.name);
         if (index == -1) {
            throw new SerializationException("Path constraint not found: " + constraintMap.name);
         }

         PathConstraintData data = skeletonData.getPathConstraints().get(index);

         for (JsonValue timelineMapxx = constraintMap.child; timelineMapxx != null; timelineMapxx = timelineMapxx.next) {
            String timelineName = timelineMapxx.name;
            if (!timelineName.equals("position") && !timelineName.equals("spacing")) {
               if (timelineName.equals("mix")) {
                  Animation.PathConstraintMixTimeline timeline = new Animation.PathConstraintMixTimeline(timelineMapxx.size);
                  timeline.pathConstraintIndex = index;
                  int frameIndex = 0;

                  for (JsonValue valueMap = timelineMapxx.child; valueMap != null; valueMap = valueMap.next) {
                     timeline.setFrame(frameIndex, valueMap.getFloat("time"), valueMap.getFloat("rotateMix", 1.0F), valueMap.getFloat("translateMix", 1.0F));
                     this.readCurve(valueMap, timeline, frameIndex);
                     frameIndex++;
                  }

                  timelines.add(timeline);
                  duration = Math.max(duration, timeline.getFrames()[(timeline.getFrameCount() - 1) * 3]);
               }
            } else {
               float timelineScale = 1.0F;
               Animation.PathConstraintPositionTimeline timeline;
               if (timelineName.equals("spacing")) {
                  timeline = new Animation.PathConstraintSpacingTimeline(timelineMapxx.size);
                  if (data.spacingMode == PathConstraintData.SpacingMode.length || data.spacingMode == PathConstraintData.SpacingMode.fixed) {
                     timelineScale = scale;
                  }
               } else {
                  timeline = new Animation.PathConstraintPositionTimeline(timelineMapxx.size);
                  if (data.positionMode == PathConstraintData.PositionMode.fixed) {
                     timelineScale = scale;
                  }
               }

               timeline.pathConstraintIndex = index;
               int frameIndex = 0;

               for (JsonValue valueMap = timelineMapxx.child; valueMap != null; valueMap = valueMap.next) {
                  timeline.setFrame(frameIndex, valueMap.getFloat("time"), valueMap.getFloat(timelineName, 0.0F) * timelineScale);
                  this.readCurve(valueMap, timeline, frameIndex);
                  frameIndex++;
               }

               timelines.add(timeline);
               duration = Math.max(duration, timeline.getFrames()[(timeline.getFrameCount() - 1) * 2]);
            }
         }
      }

      for (JsonValue deformMap = map.getChild("deform"); deformMap != null; deformMap = deformMap.next) {
         Skin skin = skeletonData.findSkin(deformMap.name);
         if (skin == null) {
            throw new SerializationException("Skin not found: " + deformMap.name);
         }

         for (JsonValue slotMap = deformMap.child; slotMap != null; slotMap = slotMap.next) {
            int slotIndex = skeletonData.findSlotIndex(slotMap.name);
            if (slotIndex == -1) {
               throw new SerializationException("Slot not found: " + slotMap.name);
            }

            for (JsonValue timelineMapxxx = slotMap.child; timelineMapxxx != null; timelineMapxxx = timelineMapxxx.next) {
               VertexAttachment attachment = (VertexAttachment)skin.getAttachment(slotIndex, timelineMapxxx.name);
               if (attachment == null) {
                  throw new SerializationException("Deform attachment not found: " + timelineMapxxx.name);
               }

               boolean weighted = attachment.getBones() != null;
               float[] vertices = attachment.getVertices();
               int deformLength = weighted ? vertices.length / 3 * 2 : vertices.length;
               Animation.DeformTimeline timeline = new Animation.DeformTimeline(timelineMapxxx.size);
               timeline.slotIndex = slotIndex;
               timeline.attachment = attachment;
               int frameIndex = 0;

               for (JsonValue valueMap = timelineMapxxx.child; valueMap != null; valueMap = valueMap.next) {
                  JsonValue verticesValue = valueMap.get("vertices");
                  float[] deform;
                  if (verticesValue == null) {
                     deform = weighted ? new float[deformLength] : vertices;
                  } else {
                     deform = new float[deformLength];
                     int start = valueMap.getInt("offset", 0);
                     System.arraycopy(verticesValue.asFloatArray(), 0, deform, start, verticesValue.size);
                     if (scale != 1.0F) {
                        int i = start;

                        for (int n = start + verticesValue.size; i < n; i++) {
                           deform[i] *= scale;
                        }
                     }

                     if (!weighted) {
                        for (int i = 0; i < deformLength; i++) {
                           deform[i] += vertices[i];
                        }
                     }
                  }

                  timeline.setFrame(frameIndex, valueMap.getFloat("time"), deform);
                  this.readCurve(valueMap, timeline, frameIndex);
                  frameIndex++;
               }

               timelines.add(timeline);
               duration = Math.max(duration, timeline.getFrames()[timeline.getFrameCount() - 1]);
            }
         }
      }

      JsonValue drawOrdersMap = map.get("drawOrder");
      if (drawOrdersMap == null) {
         drawOrdersMap = map.get("draworder");
      }

      if (drawOrdersMap != null) {
         Animation.DrawOrderTimeline timeline = new Animation.DrawOrderTimeline(drawOrdersMap.size);
         int slotCount = skeletonData.slots.size;
         int frameIndex = 0;

         for (JsonValue drawOrderMap = drawOrdersMap.child; drawOrderMap != null; drawOrderMap = drawOrderMap.next) {
            int[] drawOrder = null;
            JsonValue offsets = drawOrderMap.get("offsets");
            if (offsets != null) {
               drawOrder = new int[slotCount];

               for (int i = slotCount - 1; i >= 0; i--) {
                  drawOrder[i] = -1;
               }

               int[] unchanged = new int[slotCount - offsets.size];
               int originalIndex = 0;
               int unchangedIndex = 0;

               for (JsonValue offsetMap = offsets.child; offsetMap != null; offsetMap = offsetMap.next) {
                  int slotIndex = skeletonData.findSlotIndex(offsetMap.getString("slot"));
                  if (slotIndex == -1) {
                     throw new SerializationException("Slot not found: " + offsetMap.getString("slot"));
                  }

                  while (originalIndex != slotIndex) {
                     unchanged[unchangedIndex++] = originalIndex++;
                  }

                  drawOrder[originalIndex + offsetMap.getInt("offset")] = originalIndex++;
               }

               while (originalIndex < slotCount) {
                  unchanged[unchangedIndex++] = originalIndex++;
               }

               for (int i = slotCount - 1; i >= 0; i--) {
                  if (drawOrder[i] == -1) {
                     drawOrder[i] = unchanged[--unchangedIndex];
                  }
               }
            }

            timeline.setFrame(frameIndex++, drawOrderMap.getFloat("time"), drawOrder);
         }

         timelines.add(timeline);
         duration = Math.max(duration, timeline.getFrames()[timeline.getFrameCount() - 1]);
      }

      JsonValue eventsMap = map.get("events");
      if (eventsMap != null) {
         Animation.EventTimeline timeline = new Animation.EventTimeline(eventsMap.size);
         int frameIndex = 0;

         for (JsonValue eventMap = eventsMap.child; eventMap != null; eventMap = eventMap.next) {
            EventData eventData = skeletonData.findEvent(eventMap.getString("name"));
            if (eventData == null) {
               throw new SerializationException("Event not found: " + eventMap.getString("name"));
            }

            Event event = new Event(eventMap.getFloat("time"), eventData);
            event.intValue = eventMap.getInt("int", eventData.getInt());
            event.floatValue = eventMap.getFloat("float", eventData.getFloat());
            event.stringValue = eventMap.getString("string", eventData.getString());
            timeline.setFrame(frameIndex++, event);
         }

         timelines.add(timeline);
         duration = Math.max(duration, timeline.getFrames()[timeline.getFrameCount() - 1]);
      }

      timelines.shrink();
      skeletonData.animations.add(new Animation(name, timelines, duration));
   }

   void readCurve(JsonValue map, Animation.CurveTimeline timeline, int frameIndex) {
      JsonValue curve = map.get("curve");
      if (curve != null) {
         if (curve.isString() && curve.asString().equals("stepped")) {
            timeline.setStepped(frameIndex);
         } else if (curve.isArray()) {
            timeline.setCurve(frameIndex, curve.getFloat(0), curve.getFloat(1), curve.getFloat(2), curve.getFloat(3));
         }
      }
   }

   static class LinkedMesh {
      String parent;
      String skin;
      int slotIndex;
      MeshAttachment mesh;

      public LinkedMesh(MeshAttachment mesh, String skin, int slotIndex, String parent) {
         this.mesh = mesh;
         this.skin = skin;
         this.slotIndex = slotIndex;
         this.parent = parent;
      }
   }
}
