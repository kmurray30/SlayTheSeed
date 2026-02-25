package com.badlogic.gdx.graphics.glutils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class VertexBufferObject implements VertexData {
   private VertexAttributes attributes;
   private FloatBuffer buffer;
   private ByteBuffer byteBuffer;
   private boolean ownsBuffer;
   private int bufferHandle;
   private int usage;
   boolean isDirty = false;
   boolean isBound = false;

   public VertexBufferObject(boolean isStatic, int numVertices, VertexAttribute... attributes) {
      this(isStatic, numVertices, new VertexAttributes(attributes));
   }

   public VertexBufferObject(boolean isStatic, int numVertices, VertexAttributes attributes) {
      this.bufferHandle = Gdx.gl20.glGenBuffer();
      ByteBuffer data = BufferUtils.newUnsafeByteBuffer(attributes.vertexSize * numVertices);
      ((Buffer)data).limit(0);
      this.setBuffer(data, true, attributes);
      this.setUsage(isStatic ? '裤' : '裨');
   }

   protected VertexBufferObject(int usage, ByteBuffer data, boolean ownsBuffer, VertexAttributes attributes) {
      this.bufferHandle = Gdx.gl20.glGenBuffer();
      this.setBuffer(data, ownsBuffer, attributes);
      this.setUsage(usage);
   }

   @Override
   public VertexAttributes getAttributes() {
      return this.attributes;
   }

   @Override
   public int getNumVertices() {
      return this.buffer.limit() * 4 / this.attributes.vertexSize;
   }

   @Override
   public int getNumMaxVertices() {
      return this.byteBuffer.capacity() / this.attributes.vertexSize;
   }

   @Override
   public FloatBuffer getBuffer() {
      this.isDirty = true;
      return this.buffer;
   }

   protected void setBuffer(Buffer data, boolean ownsBuffer, VertexAttributes value) {
      if (this.isBound) {
         throw new GdxRuntimeException("Cannot change attributes while VBO is bound");
      } else {
         if (this.ownsBuffer && this.byteBuffer != null) {
            BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer);
         }

         this.attributes = value;
         if (data instanceof ByteBuffer) {
            this.byteBuffer = (ByteBuffer)data;
            this.ownsBuffer = ownsBuffer;
            int l = this.byteBuffer.limit();
            ((Buffer)this.byteBuffer).limit(this.byteBuffer.capacity());
            this.buffer = this.byteBuffer.asFloatBuffer();
            ((Buffer)this.byteBuffer).limit(l);
            ((Buffer)this.buffer).limit(l / 4);
         } else {
            throw new GdxRuntimeException("Only ByteBuffer is currently supported");
         }
      }
   }

   private void bufferChanged() {
      if (this.isBound) {
         Gdx.gl20.glBufferData(34962, this.byteBuffer.limit(), this.byteBuffer, this.usage);
         this.isDirty = false;
      }
   }

   @Override
   public void setVertices(float[] vertices, int offset, int count) {
      this.isDirty = true;
      BufferUtils.copy(vertices, this.byteBuffer, count, offset);
      ((Buffer)this.buffer).position(0);
      ((Buffer)this.buffer).limit(count);
      this.bufferChanged();
   }

   @Override
   public void updateVertices(int targetOffset, float[] vertices, int sourceOffset, int count) {
      this.isDirty = true;
      int pos = this.byteBuffer.position();
      ((Buffer)this.byteBuffer).position(targetOffset * 4);
      BufferUtils.copy(vertices, sourceOffset, count, this.byteBuffer);
      ((Buffer)this.byteBuffer).position(pos);
      ((Buffer)this.buffer).position(0);
      this.bufferChanged();
   }

   protected int getUsage() {
      return this.usage;
   }

   protected void setUsage(int value) {
      if (this.isBound) {
         throw new GdxRuntimeException("Cannot change usage while VBO is bound");
      } else {
         this.usage = value;
      }
   }

   @Override
   public void bind(ShaderProgram shader) {
      this.bind(shader, null);
   }

   @Override
   public void bind(ShaderProgram shader, int[] locations) {
      GL20 gl = Gdx.gl20;
      gl.glBindBuffer(34962, this.bufferHandle);
      if (this.isDirty) {
         ((Buffer)this.byteBuffer).limit(this.buffer.limit() * 4);
         gl.glBufferData(34962, this.byteBuffer.limit(), this.byteBuffer, this.usage);
         this.isDirty = false;
      }

      int numAttributes = this.attributes.size();
      if (locations == null) {
         for (int i = 0; i < numAttributes; i++) {
            VertexAttribute attribute = this.attributes.get(i);
            int location = shader.getAttributeLocation(attribute.alias);
            if (location >= 0) {
               shader.enableVertexAttribute(location);
               shader.setVertexAttribute(location, attribute.numComponents, attribute.type, attribute.normalized, this.attributes.vertexSize, attribute.offset);
            }
         }
      } else {
         for (int ix = 0; ix < numAttributes; ix++) {
            VertexAttribute attribute = this.attributes.get(ix);
            int location = locations[ix];
            if (location >= 0) {
               shader.enableVertexAttribute(location);
               shader.setVertexAttribute(location, attribute.numComponents, attribute.type, attribute.normalized, this.attributes.vertexSize, attribute.offset);
            }
         }
      }

      this.isBound = true;
   }

   @Override
   public void unbind(ShaderProgram shader) {
      this.unbind(shader, null);
   }

   @Override
   public void unbind(ShaderProgram shader, int[] locations) {
      GL20 gl = Gdx.gl20;
      int numAttributes = this.attributes.size();
      if (locations == null) {
         for (int i = 0; i < numAttributes; i++) {
            shader.disableVertexAttribute(this.attributes.get(i).alias);
         }
      } else {
         for (int i = 0; i < numAttributes; i++) {
            int location = locations[i];
            if (location >= 0) {
               shader.disableVertexAttribute(location);
            }
         }
      }

      gl.glBindBuffer(34962, 0);
      this.isBound = false;
   }

   @Override
   public void invalidate() {
      this.bufferHandle = Gdx.gl20.glGenBuffer();
      this.isDirty = true;
   }

   @Override
   public void dispose() {
      GL20 gl = Gdx.gl20;
      gl.glBindBuffer(34962, 0);
      gl.glDeleteBuffer(this.bufferHandle);
      this.bufferHandle = 0;
      if (this.ownsBuffer) {
         BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer);
      }
   }
}
