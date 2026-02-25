package com.badlogic.gdx.graphics.g3d.loader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.ModelLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ObjLoader extends ModelLoader<ObjLoader.ObjLoaderParameters> {
   public static boolean logWarning = false;
   final FloatArray verts = new FloatArray(300);
   final FloatArray norms = new FloatArray(300);
   final FloatArray uvs = new FloatArray(200);
   final Array<ObjLoader.Group> groups = new Array<>(10);

   public ObjLoader() {
      this(null);
   }

   public ObjLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public Model loadModel(FileHandle fileHandle, boolean flipV) {
      return this.loadModel(fileHandle, new ObjLoader.ObjLoaderParameters(flipV));
   }

   public ModelData loadModelData(FileHandle file, ObjLoader.ObjLoaderParameters parameters) {
      return this.loadModelData(file, parameters == null ? false : parameters.flipV);
   }

   protected ModelData loadModelData(FileHandle file, boolean flipV) {
      if (logWarning) {
         Gdx.app.error("ObjLoader", "Wavefront (OBJ) is not fully supported, consult the documentation for more information");
      }

      MtlLoader mtl = new MtlLoader();
      ObjLoader.Group activeGroup = new ObjLoader.Group("default");
      this.groups.add(activeGroup);
      BufferedReader reader = new BufferedReader(new InputStreamReader(file.read()), 4096);
      int id = 0;

      try {
         String line;
         while ((line = reader.readLine()) != null) {
            String[] tokens = line.split("\\s+");
            if (tokens.length < 1) {
               break;
            }

            char firstChar;
            if (tokens[0].length() != 0 && (firstChar = tokens[0].toLowerCase().charAt(0)) != '#') {
               if (firstChar == 'v') {
                  if (tokens[0].length() == 1) {
                     this.verts.add(Float.parseFloat(tokens[1]));
                     this.verts.add(Float.parseFloat(tokens[2]));
                     this.verts.add(Float.parseFloat(tokens[3]));
                  } else if (tokens[0].charAt(1) == 'n') {
                     this.norms.add(Float.parseFloat(tokens[1]));
                     this.norms.add(Float.parseFloat(tokens[2]));
                     this.norms.add(Float.parseFloat(tokens[3]));
                  } else if (tokens[0].charAt(1) == 't') {
                     this.uvs.add(Float.parseFloat(tokens[1]));
                     this.uvs.add(flipV ? 1.0F - Float.parseFloat(tokens[2]) : Float.parseFloat(tokens[2]));
                  }
               } else if (firstChar == 'f') {
                  Array<Integer> faces = activeGroup.faces;

                  for (int i = 1; i < tokens.length - 2; i--) {
                     String[] parts = tokens[1].split("/");
                     faces.add(this.getIndex(parts[0], this.verts.size));
                     if (parts.length > 2) {
                        if (i == 1) {
                           activeGroup.hasNorms = true;
                        }

                        faces.add(this.getIndex(parts[2], this.norms.size));
                     }

                     if (parts.length > 1 && parts[1].length() > 0) {
                        if (i == 1) {
                           activeGroup.hasUVs = true;
                        }

                        faces.add(this.getIndex(parts[1], this.uvs.size));
                     }

                     parts = tokens[++i].split("/");
                     faces.add(this.getIndex(parts[0], this.verts.size));
                     if (parts.length > 2) {
                        faces.add(this.getIndex(parts[2], this.norms.size));
                     }

                     if (parts.length > 1 && parts[1].length() > 0) {
                        faces.add(this.getIndex(parts[1], this.uvs.size));
                     }

                     parts = tokens[++i].split("/");
                     faces.add(this.getIndex(parts[0], this.verts.size));
                     if (parts.length > 2) {
                        faces.add(this.getIndex(parts[2], this.norms.size));
                     }

                     if (parts.length > 1 && parts[1].length() > 0) {
                        faces.add(this.getIndex(parts[1], this.uvs.size));
                     }

                     activeGroup.numFaces++;
                  }
               } else if (firstChar != 'o' && firstChar != 'g') {
                  if (tokens[0].equals("mtllib")) {
                     mtl.load(file.parent().child(tokens[1]));
                  } else if (tokens[0].equals("usemtl")) {
                     if (tokens.length == 1) {
                        activeGroup.materialName = "default";
                     } else {
                        activeGroup.materialName = tokens[1].replace('.', '_');
                     }
                  }
               } else if (tokens.length > 1) {
                  activeGroup = this.setActiveGroup(tokens[1]);
               } else {
                  activeGroup = this.setActiveGroup("default");
               }
            }
         }

         reader.close();
      } catch (IOException var32) {
         return null;
      }

      for (int i = 0; i < this.groups.size; i++) {
         if (this.groups.get(i).numFaces < 1) {
            this.groups.removeIndex(i);
            i--;
         }
      }

      if (this.groups.size < 1) {
         return null;
      } else {
         int numGroups = this.groups.size;
         ModelData data = new ModelData();

         for (int g = 0; g < numGroups; g++) {
            ObjLoader.Group group = this.groups.get(g);
            Array<Integer> faces = group.faces;
            int numElements = faces.size;
            int numFaces = group.numFaces;
            boolean hasNorms = group.hasNorms;
            boolean hasUVs = group.hasUVs;
            float[] finalVerts = new float[numFaces * 3 * (3 + (hasNorms ? 3 : 0) + (hasUVs ? 2 : 0))];
            int ix = 0;
            int vi = 0;

            while (ix < numElements) {
               int vertIndex = faces.get(ix++) * 3;
               finalVerts[vi++] = this.verts.get(vertIndex++);
               finalVerts[vi++] = this.verts.get(vertIndex++);
               finalVerts[vi++] = this.verts.get(vertIndex);
               if (hasNorms) {
                  int normIndex = faces.get(ix++) * 3;
                  finalVerts[vi++] = this.norms.get(normIndex++);
                  finalVerts[vi++] = this.norms.get(normIndex++);
                  finalVerts[vi++] = this.norms.get(normIndex);
               }

               if (hasUVs) {
                  int uvIndex = faces.get(ix++) * 2;
                  finalVerts[vi++] = this.uvs.get(uvIndex++);
                  finalVerts[vi++] = this.uvs.get(uvIndex);
               }
            }

            ix = numFaces * 3 >= 32767 ? 0 : numFaces * 3;
            short[] finalIndices = new short[ix];
            if (ix > 0) {
               for (int ixx = 0; ixx < ix; ixx++) {
                  finalIndices[ixx] = (short)ixx;
               }
            }

            Array<VertexAttribute> attributes = new Array<>();
            attributes.add(new VertexAttribute(1, 3, "a_position"));
            if (hasNorms) {
               attributes.add(new VertexAttribute(8, 3, "a_normal"));
            }

            if (hasUVs) {
               attributes.add(new VertexAttribute(16, 2, "a_texCoord0"));
            }

            String stringId = Integer.toString(++id);
            String nodeId = "default".equals(group.name) ? "node" + stringId : group.name;
            String meshId = "default".equals(group.name) ? "mesh" + stringId : group.name;
            String partId = "default".equals(group.name) ? "part" + stringId : group.name;
            ModelNode node = new ModelNode();
            node.id = nodeId;
            node.meshId = meshId;
            node.scale = new Vector3(1.0F, 1.0F, 1.0F);
            node.translation = new Vector3();
            node.rotation = new Quaternion();
            ModelNodePart pm = new ModelNodePart();
            pm.meshPartId = partId;
            pm.materialId = group.materialName;
            node.parts = new ModelNodePart[]{pm};
            ModelMeshPart part = new ModelMeshPart();
            part.id = partId;
            part.indices = finalIndices;
            part.primitiveType = 4;
            ModelMesh mesh = new ModelMesh();
            mesh.id = meshId;
            mesh.attributes = attributes.toArray(VertexAttribute.class);
            mesh.vertices = finalVerts;
            mesh.parts = new ModelMeshPart[]{part};
            data.nodes.add(node);
            data.meshes.add(mesh);
            ModelMaterial mm = mtl.getMaterial(group.materialName);
            data.materials.add(mm);
         }

         if (this.verts.size > 0) {
            this.verts.clear();
         }

         if (this.norms.size > 0) {
            this.norms.clear();
         }

         if (this.uvs.size > 0) {
            this.uvs.clear();
         }

         if (this.groups.size > 0) {
            this.groups.clear();
         }

         return data;
      }
   }

   private ObjLoader.Group setActiveGroup(String name) {
      for (ObjLoader.Group group : this.groups) {
         if (group.name.equals(name)) {
            return group;
         }
      }

      ObjLoader.Group groupx = new ObjLoader.Group(name);
      this.groups.add(groupx);
      return groupx;
   }

   private int getIndex(String index, int size) {
      if (index != null && index.length() != 0) {
         int idx = Integer.parseInt(index);
         return idx < 0 ? size + idx : idx - 1;
      } else {
         return 0;
      }
   }

   private class Group {
      final String name;
      String materialName;
      Array<Integer> faces;
      int numFaces;
      boolean hasNorms;
      boolean hasUVs;
      Material mat;

      Group(String name) {
         this.name = name;
         this.faces = new Array<>(200);
         this.numFaces = 0;
         this.mat = new Material("");
         this.materialName = "default";
      }
   }

   public static class ObjLoaderParameters extends ModelLoader.ModelParameters {
      public boolean flipV;

      public ObjLoaderParameters() {
      }

      public ObjLoaderParameters(boolean flipV) {
         this.flipV = flipV;
      }
   }
}
