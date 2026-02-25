package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.NumberUtils;

public class PolygonSpriteBatch implements Batch {
   private Mesh mesh;
   private final float[] vertices;
   private final short[] triangles;
   private int vertexIndex;
   private int triangleIndex;
   private Texture lastTexture;
   private float invTexWidth = 0.0F;
   private float invTexHeight = 0.0F;
   private boolean drawing;
   private final Matrix4 transformMatrix = new Matrix4();
   private final Matrix4 projectionMatrix = new Matrix4();
   private final Matrix4 combinedMatrix = new Matrix4();
   private boolean blendingDisabled;
   private int blendSrcFunc = 770;
   private int blendDstFunc = 771;
   private final ShaderProgram shader;
   private ShaderProgram customShader;
   private boolean ownsShader;
   float color = Color.WHITE.toFloatBits();
   private Color tempColor = new Color(1.0F, 1.0F, 1.0F, 1.0F);
   public int renderCalls = 0;
   public int totalRenderCalls = 0;
   public int maxTrianglesInBatch = 0;

   public PolygonSpriteBatch() {
      this(2000, null);
   }

   public PolygonSpriteBatch(int size) {
      this(size, size * 2, null);
   }

   public PolygonSpriteBatch(int size, ShaderProgram defaultShader) {
      this(size, size * 2, defaultShader);
   }

   public PolygonSpriteBatch(int maxVertices, int maxTriangles, ShaderProgram defaultShader) {
      if (maxVertices > 32767) {
         throw new IllegalArgumentException("Can't have more than 32767 vertices per batch: " + maxVertices);
      } else {
         Mesh.VertexDataType vertexDataType = Mesh.VertexDataType.VertexArray;
         if (Gdx.gl30 != null) {
            vertexDataType = Mesh.VertexDataType.VertexBufferObjectWithVAO;
         }

         this.mesh = new Mesh(
            vertexDataType,
            false,
            maxVertices,
            maxTriangles * 3,
            new VertexAttribute(1, 2, "a_position"),
            new VertexAttribute(4, 4, "a_color"),
            new VertexAttribute(16, 2, "a_texCoord0")
         );
         this.vertices = new float[maxVertices * 5];
         this.triangles = new short[maxTriangles * 3];
         if (defaultShader == null) {
            this.shader = SpriteBatch.createDefaultShader();
            this.ownsShader = true;
         } else {
            this.shader = defaultShader;
         }

         this.projectionMatrix.setToOrtho2D(0.0F, 0.0F, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
      }
   }

   @Override
   public void begin() {
      if (this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.end must be called before begin.");
      } else {
         this.renderCalls = 0;
         Gdx.gl.glDepthMask(false);
         if (this.customShader != null) {
            this.customShader.begin();
         } else {
            this.shader.begin();
         }

         this.setupMatrices();
         this.drawing = true;
      }
   }

   @Override
   public void end() {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before end.");
      } else {
         if (this.vertexIndex > 0) {
            this.flush();
         }

         this.lastTexture = null;
         this.drawing = false;
         GL20 gl = Gdx.gl;
         gl.glDepthMask(true);
         if (this.isBlendingEnabled()) {
            gl.glDisable(3042);
         }

         if (this.customShader != null) {
            this.customShader.end();
         } else {
            this.shader.end();
         }
      }
   }

   @Override
   public void setColor(Color tint) {
      this.color = tint.toFloatBits();
   }

   @Override
   public void setColor(float r, float g, float b, float a) {
      int intBits = (int)(255.0F * a) << 24 | (int)(255.0F * b) << 16 | (int)(255.0F * g) << 8 | (int)(255.0F * r);
      this.color = NumberUtils.intToFloatColor(intBits);
   }

   @Override
   public void setColor(float color) {
      this.color = color;
   }

   @Override
   public Color getColor() {
      int intBits = NumberUtils.floatToIntColor(this.color);
      Color color = this.tempColor;
      color.r = (intBits & 0xFF) / 255.0F;
      color.g = (intBits >>> 8 & 0xFF) / 255.0F;
      color.b = (intBits >>> 16 & 0xFF) / 255.0F;
      color.a = (intBits >>> 24 & 0xFF) / 255.0F;
      return color;
   }

   @Override
   public float getPackedColor() {
      return this.color;
   }

   public void draw(PolygonRegion region, float x, float y) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         short[] regionTriangles = region.triangles;
         int regionTrianglesLength = regionTriangles.length;
         float[] regionVertices = region.vertices;
         int regionVerticesLength = regionVertices.length;
         Texture texture = region.region.texture;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + regionTrianglesLength > triangles.length || this.vertexIndex + regionVerticesLength * 5 / 2 > this.vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int vertexIndex = this.vertexIndex;
         int startVertex = vertexIndex / 5;

         for (int i = 0; i < regionTrianglesLength; i++) {
            triangles[triangleIndex++] = (short)(regionTriangles[i] + startVertex);
         }

         this.triangleIndex = triangleIndex;
         float[] vertices = this.vertices;
         float color = this.color;
         float[] textureCoords = region.textureCoords;

         for (int i = 0; i < regionVerticesLength; i += 2) {
            vertices[vertexIndex++] = regionVertices[i] + x;
            vertices[vertexIndex++] = regionVertices[i + 1] + y;
            vertices[vertexIndex++] = color;
            vertices[vertexIndex++] = textureCoords[i];
            vertices[vertexIndex++] = textureCoords[i + 1];
         }

         this.vertexIndex = vertexIndex;
      }
   }

   public void draw(PolygonRegion region, float x, float y, float width, float height) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         short[] regionTriangles = region.triangles;
         int regionTrianglesLength = regionTriangles.length;
         float[] regionVertices = region.vertices;
         int regionVerticesLength = regionVertices.length;
         TextureRegion textureRegion = region.region;
         Texture texture = textureRegion.texture;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + regionTrianglesLength > triangles.length || this.vertexIndex + regionVerticesLength * 5 / 2 > this.vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int vertexIndex = this.vertexIndex;
         int startVertex = vertexIndex / 5;
         int i = 0;

         for (int n = regionTriangles.length; i < n; i++) {
            triangles[triangleIndex++] = (short)(regionTriangles[i] + startVertex);
         }

         this.triangleIndex = triangleIndex;
         float[] vertices = this.vertices;
         float color = this.color;
         float[] textureCoords = region.textureCoords;
         float sX = width / textureRegion.regionWidth;
         float sY = height / textureRegion.regionHeight;

         for (int ix = 0; ix < regionVerticesLength; ix += 2) {
            vertices[vertexIndex++] = regionVertices[ix] * sX + x;
            vertices[vertexIndex++] = regionVertices[ix + 1] * sY + y;
            vertices[vertexIndex++] = color;
            vertices[vertexIndex++] = textureCoords[ix];
            vertices[vertexIndex++] = textureCoords[ix + 1];
         }

         this.vertexIndex = vertexIndex;
      }
   }

   public void draw(PolygonRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         short[] regionTriangles = region.triangles;
         int regionTrianglesLength = regionTriangles.length;
         float[] regionVertices = region.vertices;
         int regionVerticesLength = regionVertices.length;
         TextureRegion textureRegion = region.region;
         Texture texture = textureRegion.texture;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + regionTrianglesLength > triangles.length || this.vertexIndex + regionVerticesLength * 5 / 2 > this.vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int vertexIndex = this.vertexIndex;
         int startVertex = vertexIndex / 5;

         for (int i = 0; i < regionTrianglesLength; i++) {
            triangles[triangleIndex++] = (short)(regionTriangles[i] + startVertex);
         }

         this.triangleIndex = triangleIndex;
         float[] vertices = this.vertices;
         float color = this.color;
         float[] textureCoords = region.textureCoords;
         float worldOriginX = x + originX;
         float worldOriginY = y + originY;
         float sX = width / textureRegion.regionWidth;
         float sY = height / textureRegion.regionHeight;
         float cos = MathUtils.cosDeg(rotation);
         float sin = MathUtils.sinDeg(rotation);

         for (int i = 0; i < regionVerticesLength; i += 2) {
            float fx = (regionVertices[i] * sX - originX) * scaleX;
            float fy = (regionVertices[i + 1] * sY - originY) * scaleY;
            vertices[vertexIndex++] = cos * fx - sin * fy + worldOriginX;
            vertices[vertexIndex++] = sin * fx + cos * fy + worldOriginY;
            vertices[vertexIndex++] = color;
            vertices[vertexIndex++] = textureCoords[i];
            vertices[vertexIndex++] = textureCoords[i + 1];
         }

         this.vertexIndex = vertexIndex;
      }
   }

   public void draw(
      Texture texture, float[] polygonVertices, int verticesOffset, int verticesCount, short[] polygonTriangles, int trianglesOffset, int trianglesCount
   ) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + trianglesCount > triangles.length || this.vertexIndex + verticesCount > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int vertexIndex = this.vertexIndex;
         int startVertex = vertexIndex / 5;
         int i = trianglesOffset;

         for (int n = trianglesOffset + trianglesCount; i < n; i++) {
            triangles[triangleIndex++] = (short)(polygonTriangles[i] + startVertex);
         }

         this.triangleIndex = triangleIndex;
         System.arraycopy(polygonVertices, verticesOffset, vertices, vertexIndex, verticesCount);
         this.vertexIndex += verticesCount;
      }
   }

   @Override
   public void draw(
      Texture texture,
      float x,
      float y,
      float originX,
      float originY,
      float width,
      float height,
      float scaleX,
      float scaleY,
      float rotation,
      int srcX,
      int srcY,
      int srcWidth,
      int srcHeight,
      boolean flipX,
      boolean flipY
   ) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float worldOriginX = x + originX;
         float worldOriginY = y + originY;
         float fx = -originX;
         float fy = -originY;
         float fx2 = width - originX;
         float fy2 = height - originY;
         if (scaleX != 1.0F || scaleY != 1.0F) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
         }

         float x1;
         float y1;
         float x2;
         float y2;
         float x3;
         float y3;
         float x4;
         float y4;
         if (rotation != 0.0F) {
            float cos = MathUtils.cosDeg(rotation);
            float sin = MathUtils.sinDeg(rotation);
            x1 = cos * fx - sin * fy;
            y1 = sin * fx + cos * fy;
            x2 = cos * fx - sin * fy2;
            y2 = sin * fx + cos * fy2;
            x3 = cos * fx2 - sin * fy2;
            y3 = sin * fx2 + cos * fy2;
            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
         } else {
            x1 = fx;
            y1 = fy;
            x2 = fx;
            y2 = fy2;
            x3 = fx2;
            y3 = fy2;
            x4 = fx2;
            y4 = fy;
         }

         x1 += worldOriginX;
         y1 += worldOriginY;
         x2 += worldOriginX;
         y2 += worldOriginY;
         x3 += worldOriginX;
         y3 += worldOriginY;
         x4 += worldOriginX;
         y4 += worldOriginY;
         float u = srcX * this.invTexWidth;
         float v = (srcY + srcHeight) * this.invTexHeight;
         float u2 = (srcX + srcWidth) * this.invTexWidth;
         float v2 = srcY * this.invTexHeight;
         if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
         }

         if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
         }

         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x1;
         vertices[idx++] = y1;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v;
         vertices[idx++] = x2;
         vertices[idx++] = y2;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v2;
         vertices[idx++] = x3;
         vertices[idx++] = y3;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = x4;
         vertices[idx++] = y4;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float u = srcX * this.invTexWidth;
         float v = (srcY + srcHeight) * this.invTexHeight;
         float u2 = (srcX + srcWidth) * this.invTexWidth;
         float v2 = srcY * this.invTexHeight;
         float fx2 = x + width;
         float fy2 = y + height;
         if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
         }

         if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
         }

         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v;
         vertices[idx++] = x;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float u = srcX * this.invTexWidth;
         float v = (srcY + srcHeight) * this.invTexHeight;
         float u2 = (srcX + srcWidth) * this.invTexWidth;
         float v2 = srcY * this.invTexHeight;
         float fx2 = x + srcWidth;
         float fy2 = y + srcHeight;
         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v;
         vertices[idx++] = x;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float fx2 = x + width;
         float fy2 = y + height;
         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v;
         vertices[idx++] = x;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(Texture texture, float x, float y) {
      this.draw(texture, x, y, (float)texture.getWidth(), (float)texture.getHeight());
   }

   @Override
   public void draw(Texture texture, float x, float y, float width, float height) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float fx2 = x + width;
         float fy2 = y + height;
         float u = 0.0F;
         float v = 1.0F;
         float u2 = 1.0F;
         float v2 = 0.0F;
         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = 0.0F;
         vertices[idx++] = 1.0F;
         vertices[idx++] = x;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = 0.0F;
         vertices[idx++] = 0.0F;
         vertices[idx++] = fx2;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = 1.0F;
         vertices[idx++] = 0.0F;
         vertices[idx++] = fx2;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = 1.0F;
         vertices[idx++] = 1.0F;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(Texture texture, float[] spriteVertices, int offset, int count) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         int triangleCount = count / 20 * 6;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + triangleCount > triangles.length || this.vertexIndex + count > vertices.length) {
            this.flush();
         }

         int vertexIndex = this.vertexIndex;
         int triangleIndex = this.triangleIndex;
         short vertex = (short)(vertexIndex / 5);

         for (int n = triangleIndex + triangleCount; triangleIndex < n; vertex = (short)(vertex + 4)) {
            triangles[triangleIndex] = vertex;
            triangles[triangleIndex + 1] = (short)(vertex + 1);
            triangles[triangleIndex + 2] = (short)(vertex + 2);
            triangles[triangleIndex + 3] = (short)(vertex + 2);
            triangles[triangleIndex + 4] = (short)(vertex + 3);
            triangles[triangleIndex + 5] = vertex;
            triangleIndex += 6;
         }

         this.triangleIndex = triangleIndex;
         System.arraycopy(spriteVertices, offset, vertices, vertexIndex, count);
         this.vertexIndex += count;
      }
   }

   @Override
   public void draw(TextureRegion region, float x, float y) {
      this.draw(region, x, y, (float)region.getRegionWidth(), (float)region.getRegionHeight());
   }

   @Override
   public void draw(TextureRegion region, float x, float y, float width, float height) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         Texture texture = region.texture;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float fx2 = x + width;
         float fy2 = y + height;
         float u = region.u;
         float v = region.v2;
         float u2 = region.u2;
         float v2 = region.v;
         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v;
         vertices[idx++] = x;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = fy2;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = fx2;
         vertices[idx++] = y;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         Texture texture = region.texture;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float worldOriginX = x + originX;
         float worldOriginY = y + originY;
         float fx = -originX;
         float fy = -originY;
         float fx2 = width - originX;
         float fy2 = height - originY;
         if (scaleX != 1.0F || scaleY != 1.0F) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
         }

         float x1;
         float y1;
         float x2;
         float y2;
         float x3;
         float y3;
         float x4;
         float y4;
         if (rotation != 0.0F) {
            float cos = MathUtils.cosDeg(rotation);
            float sin = MathUtils.sinDeg(rotation);
            x1 = cos * fx - sin * fy;
            y1 = sin * fx + cos * fy;
            x2 = cos * fx - sin * fy2;
            y2 = sin * fx + cos * fy2;
            x3 = cos * fx2 - sin * fy2;
            y3 = sin * fx2 + cos * fy2;
            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
         } else {
            x1 = fx;
            y1 = fy;
            x2 = fx;
            y2 = fy2;
            x3 = fx2;
            y3 = fy2;
            x4 = fx2;
            y4 = fy;
         }

         x1 += worldOriginX;
         y1 += worldOriginY;
         x2 += worldOriginX;
         y2 += worldOriginY;
         x3 += worldOriginX;
         y3 += worldOriginY;
         x4 += worldOriginX;
         y4 += worldOriginY;
         float u = region.u;
         float v = region.v2;
         float u2 = region.u2;
         float v2 = region.v;
         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x1;
         vertices[idx++] = y1;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v;
         vertices[idx++] = x2;
         vertices[idx++] = y2;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v2;
         vertices[idx++] = x3;
         vertices[idx++] = y3;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = x4;
         vertices[idx++] = y4;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(
      TextureRegion region,
      float x,
      float y,
      float originX,
      float originY,
      float width,
      float height,
      float scaleX,
      float scaleY,
      float rotation,
      boolean clockwise
   ) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         Texture texture = region.texture;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float worldOriginX = x + originX;
         float worldOriginY = y + originY;
         float fx = -originX;
         float fy = -originY;
         float fx2 = width - originX;
         float fy2 = height - originY;
         if (scaleX != 1.0F || scaleY != 1.0F) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
         }

         float x1;
         float y1;
         float x2;
         float y2;
         float x3;
         float y3;
         float x4;
         float y4;
         if (rotation != 0.0F) {
            float cos = MathUtils.cosDeg(rotation);
            float sin = MathUtils.sinDeg(rotation);
            x1 = cos * fx - sin * fy;
            y1 = sin * fx + cos * fy;
            x2 = cos * fx - sin * fy2;
            y2 = sin * fx + cos * fy2;
            x3 = cos * fx2 - sin * fy2;
            y3 = sin * fx2 + cos * fy2;
            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
         } else {
            x1 = fx;
            y1 = fy;
            x2 = fx;
            y2 = fy2;
            x3 = fx2;
            y3 = fy2;
            x4 = fx2;
            y4 = fy;
         }

         x1 += worldOriginX;
         y1 += worldOriginY;
         x2 += worldOriginX;
         y2 += worldOriginY;
         x3 += worldOriginX;
         y3 += worldOriginY;
         x4 += worldOriginX;
         y4 += worldOriginY;
         float u2;
         float v2;
         float u3;
         float v3;
         float u4;
         float v4;
         float u1;
         float v1;
         if (clockwise) {
            u1 = region.u2;
            v1 = region.v2;
            u2 = region.u;
            v2 = region.v2;
            u3 = region.u;
            v3 = region.v;
            u4 = region.u2;
            v4 = region.v;
         } else {
            u1 = region.u;
            v1 = region.v;
            u2 = region.u2;
            v2 = region.v;
            u3 = region.u2;
            v3 = region.v2;
            u4 = region.u;
            v4 = region.v2;
         }

         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x1;
         vertices[idx++] = y1;
         vertices[idx++] = color;
         vertices[idx++] = u1;
         vertices[idx++] = v1;
         vertices[idx++] = x2;
         vertices[idx++] = y2;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = x3;
         vertices[idx++] = y3;
         vertices[idx++] = color;
         vertices[idx++] = u3;
         vertices[idx++] = v3;
         vertices[idx++] = x4;
         vertices[idx++] = y4;
         vertices[idx++] = color;
         vertices[idx++] = u4;
         vertices[idx++] = v4;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void draw(TextureRegion region, float width, float height, Affine2 transform) {
      if (!this.drawing) {
         throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
      } else {
         short[] triangles = this.triangles;
         float[] vertices = this.vertices;
         Texture texture = region.texture;
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
            this.flush();
         }

         int triangleIndex = this.triangleIndex;
         int startVertex = this.vertexIndex / 5;
         triangles[triangleIndex++] = (short)startVertex;
         triangles[triangleIndex++] = (short)(startVertex + 1);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 2);
         triangles[triangleIndex++] = (short)(startVertex + 3);
         triangles[triangleIndex++] = (short)startVertex;
         this.triangleIndex = triangleIndex;
         float x1 = transform.m02;
         float y1 = transform.m12;
         float x2 = transform.m01 * height + transform.m02;
         float y2 = transform.m11 * height + transform.m12;
         float x3 = transform.m00 * width + transform.m01 * height + transform.m02;
         float y3 = transform.m10 * width + transform.m11 * height + transform.m12;
         float x4 = transform.m00 * width + transform.m02;
         float y4 = transform.m10 * width + transform.m12;
         float u = region.u;
         float v = region.v2;
         float u2 = region.u2;
         float v2 = region.v;
         float color = this.color;
         int idx = this.vertexIndex;
         vertices[idx++] = x1;
         vertices[idx++] = y1;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v;
         vertices[idx++] = x2;
         vertices[idx++] = y2;
         vertices[idx++] = color;
         vertices[idx++] = u;
         vertices[idx++] = v2;
         vertices[idx++] = x3;
         vertices[idx++] = y3;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v2;
         vertices[idx++] = x4;
         vertices[idx++] = y4;
         vertices[idx++] = color;
         vertices[idx++] = u2;
         vertices[idx++] = v;
         this.vertexIndex = idx;
      }
   }

   @Override
   public void flush() {
      if (this.vertexIndex != 0) {
         this.renderCalls++;
         this.totalRenderCalls++;
         int trianglesInBatch = this.triangleIndex;
         if (trianglesInBatch > this.maxTrianglesInBatch) {
            this.maxTrianglesInBatch = trianglesInBatch;
         }

         this.lastTexture.bind();
         Mesh mesh = this.mesh;
         mesh.setVertices(this.vertices, 0, this.vertexIndex);
         mesh.setIndices(this.triangles, 0, this.triangleIndex);
         if (this.blendingDisabled) {
            Gdx.gl.glDisable(3042);
         } else {
            Gdx.gl.glEnable(3042);
            if (this.blendSrcFunc != -1) {
               Gdx.gl.glBlendFunc(this.blendSrcFunc, this.blendDstFunc);
            }
         }

         mesh.render(this.customShader != null ? this.customShader : this.shader, 4, 0, trianglesInBatch);
         this.vertexIndex = 0;
         this.triangleIndex = 0;
      }
   }

   @Override
   public void disableBlending() {
      this.flush();
      this.blendingDisabled = true;
   }

   @Override
   public void enableBlending() {
      this.flush();
      this.blendingDisabled = false;
   }

   @Override
   public void setBlendFunction(int srcFunc, int dstFunc) {
      if (this.blendSrcFunc != srcFunc || this.blendDstFunc != dstFunc) {
         this.flush();
         this.blendSrcFunc = srcFunc;
         this.blendDstFunc = dstFunc;
      }
   }

   @Override
   public int getBlendSrcFunc() {
      return this.blendSrcFunc;
   }

   @Override
   public int getBlendDstFunc() {
      return this.blendDstFunc;
   }

   @Override
   public void dispose() {
      this.mesh.dispose();
      if (this.ownsShader && this.shader != null) {
         this.shader.dispose();
      }
   }

   @Override
   public Matrix4 getProjectionMatrix() {
      return this.projectionMatrix;
   }

   @Override
   public Matrix4 getTransformMatrix() {
      return this.transformMatrix;
   }

   @Override
   public void setProjectionMatrix(Matrix4 projection) {
      if (this.drawing) {
         this.flush();
      }

      this.projectionMatrix.set(projection);
      if (this.drawing) {
         this.setupMatrices();
      }
   }

   @Override
   public void setTransformMatrix(Matrix4 transform) {
      if (this.drawing) {
         this.flush();
      }

      this.transformMatrix.set(transform);
      if (this.drawing) {
         this.setupMatrices();
      }
   }

   private void setupMatrices() {
      this.combinedMatrix.set(this.projectionMatrix).mul(this.transformMatrix);
      if (this.customShader != null) {
         this.customShader.setUniformMatrix("u_projTrans", this.combinedMatrix);
         this.customShader.setUniformi("u_texture", 0);
      } else {
         this.shader.setUniformMatrix("u_projTrans", this.combinedMatrix);
         this.shader.setUniformi("u_texture", 0);
      }
   }

   private void switchTexture(Texture texture) {
      this.flush();
      this.lastTexture = texture;
      this.invTexWidth = 1.0F / texture.getWidth();
      this.invTexHeight = 1.0F / texture.getHeight();
   }

   @Override
   public void setShader(ShaderProgram shader) {
      if (this.drawing) {
         this.flush();
         if (this.customShader != null) {
            this.customShader.end();
         } else {
            this.shader.end();
         }
      }

      this.customShader = shader;
      if (this.drawing) {
         if (this.customShader != null) {
            this.customShader.begin();
         } else {
            this.shader.begin();
         }

         this.setupMatrices();
      }
   }

   @Override
   public ShaderProgram getShader() {
      return this.customShader == null ? this.shader : this.customShader;
   }

   @Override
   public boolean isBlendingEnabled() {
      return !this.blendingDisabled;
   }

   @Override
   public boolean isDrawing() {
      return this.drawing;
   }
}
