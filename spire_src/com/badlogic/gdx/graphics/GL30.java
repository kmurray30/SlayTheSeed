package com.badlogic.gdx.graphics;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

public interface GL30 extends GL20 {
   int GL_READ_BUFFER = 3074;
   int GL_UNPACK_ROW_LENGTH = 3314;
   int GL_UNPACK_SKIP_ROWS = 3315;
   int GL_UNPACK_SKIP_PIXELS = 3316;
   int GL_PACK_ROW_LENGTH = 3330;
   int GL_PACK_SKIP_ROWS = 3331;
   int GL_PACK_SKIP_PIXELS = 3332;
   int GL_COLOR = 6144;
   int GL_DEPTH = 6145;
   int GL_STENCIL = 6146;
   int GL_RED = 6403;
   int GL_RGB8 = 32849;
   int GL_RGBA8 = 32856;
   int GL_RGB10_A2 = 32857;
   int GL_TEXTURE_BINDING_3D = 32874;
   int GL_UNPACK_SKIP_IMAGES = 32877;
   int GL_UNPACK_IMAGE_HEIGHT = 32878;
   int GL_TEXTURE_3D = 32879;
   int GL_TEXTURE_WRAP_R = 32882;
   int GL_MAX_3D_TEXTURE_SIZE = 32883;
   int GL_UNSIGNED_INT_2_10_10_10_REV = 33640;
   int GL_MAX_ELEMENTS_VERTICES = 33000;
   int GL_MAX_ELEMENTS_INDICES = 33001;
   int GL_TEXTURE_MIN_LOD = 33082;
   int GL_TEXTURE_MAX_LOD = 33083;
   int GL_TEXTURE_BASE_LEVEL = 33084;
   int GL_TEXTURE_MAX_LEVEL = 33085;
   int GL_MIN = 32775;
   int GL_MAX = 32776;
   int GL_DEPTH_COMPONENT24 = 33190;
   int GL_MAX_TEXTURE_LOD_BIAS = 34045;
   int GL_TEXTURE_COMPARE_MODE = 34892;
   int GL_TEXTURE_COMPARE_FUNC = 34893;
   int GL_CURRENT_QUERY = 34917;
   int GL_QUERY_RESULT = 34918;
   int GL_QUERY_RESULT_AVAILABLE = 34919;
   int GL_BUFFER_MAPPED = 35004;
   int GL_BUFFER_MAP_POINTER = 35005;
   int GL_STREAM_READ = 35041;
   int GL_STREAM_COPY = 35042;
   int GL_STATIC_READ = 35045;
   int GL_STATIC_COPY = 35046;
   int GL_DYNAMIC_READ = 35049;
   int GL_DYNAMIC_COPY = 35050;
   int GL_MAX_DRAW_BUFFERS = 34852;
   int GL_DRAW_BUFFER0 = 34853;
   int GL_DRAW_BUFFER1 = 34854;
   int GL_DRAW_BUFFER2 = 34855;
   int GL_DRAW_BUFFER3 = 34856;
   int GL_DRAW_BUFFER4 = 34857;
   int GL_DRAW_BUFFER5 = 34858;
   int GL_DRAW_BUFFER6 = 34859;
   int GL_DRAW_BUFFER7 = 34860;
   int GL_DRAW_BUFFER8 = 34861;
   int GL_DRAW_BUFFER9 = 34862;
   int GL_DRAW_BUFFER10 = 34863;
   int GL_DRAW_BUFFER11 = 34864;
   int GL_DRAW_BUFFER12 = 34865;
   int GL_DRAW_BUFFER13 = 34866;
   int GL_DRAW_BUFFER14 = 34867;
   int GL_DRAW_BUFFER15 = 34868;
   int GL_MAX_FRAGMENT_UNIFORM_COMPONENTS = 35657;
   int GL_MAX_VERTEX_UNIFORM_COMPONENTS = 35658;
   int GL_SAMPLER_3D = 35679;
   int GL_SAMPLER_2D_SHADOW = 35682;
   int GL_FRAGMENT_SHADER_DERIVATIVE_HINT = 35723;
   int GL_PIXEL_PACK_BUFFER = 35051;
   int GL_PIXEL_UNPACK_BUFFER = 35052;
   int GL_PIXEL_PACK_BUFFER_BINDING = 35053;
   int GL_PIXEL_UNPACK_BUFFER_BINDING = 35055;
   int GL_FLOAT_MAT2x3 = 35685;
   int GL_FLOAT_MAT2x4 = 35686;
   int GL_FLOAT_MAT3x2 = 35687;
   int GL_FLOAT_MAT3x4 = 35688;
   int GL_FLOAT_MAT4x2 = 35689;
   int GL_FLOAT_MAT4x3 = 35690;
   int GL_SRGB = 35904;
   int GL_SRGB8 = 35905;
   int GL_SRGB8_ALPHA8 = 35907;
   int GL_COMPARE_REF_TO_TEXTURE = 34894;
   int GL_MAJOR_VERSION = 33307;
   int GL_MINOR_VERSION = 33308;
   int GL_NUM_EXTENSIONS = 33309;
   int GL_RGBA32F = 34836;
   int GL_RGB32F = 34837;
   int GL_RGBA16F = 34842;
   int GL_RGB16F = 34843;
   int GL_VERTEX_ATTRIB_ARRAY_INTEGER = 35069;
   int GL_MAX_ARRAY_TEXTURE_LAYERS = 35071;
   int GL_MIN_PROGRAM_TEXEL_OFFSET = 35076;
   int GL_MAX_PROGRAM_TEXEL_OFFSET = 35077;
   int GL_MAX_VARYING_COMPONENTS = 35659;
   int GL_TEXTURE_2D_ARRAY = 35866;
   int GL_TEXTURE_BINDING_2D_ARRAY = 35869;
   int GL_R11F_G11F_B10F = 35898;
   int GL_UNSIGNED_INT_10F_11F_11F_REV = 35899;
   int GL_RGB9_E5 = 35901;
   int GL_UNSIGNED_INT_5_9_9_9_REV = 35902;
   int GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH = 35958;
   int GL_TRANSFORM_FEEDBACK_BUFFER_MODE = 35967;
   int GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_COMPONENTS = 35968;
   int GL_TRANSFORM_FEEDBACK_VARYINGS = 35971;
   int GL_TRANSFORM_FEEDBACK_BUFFER_START = 35972;
   int GL_TRANSFORM_FEEDBACK_BUFFER_SIZE = 35973;
   int GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN = 35976;
   int GL_RASTERIZER_DISCARD = 35977;
   int GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS = 35978;
   int GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS = 35979;
   int GL_INTERLEAVED_ATTRIBS = 35980;
   int GL_SEPARATE_ATTRIBS = 35981;
   int GL_TRANSFORM_FEEDBACK_BUFFER = 35982;
   int GL_TRANSFORM_FEEDBACK_BUFFER_BINDING = 35983;
   int GL_RGBA32UI = 36208;
   int GL_RGB32UI = 36209;
   int GL_RGBA16UI = 36214;
   int GL_RGB16UI = 36215;
   int GL_RGBA8UI = 36220;
   int GL_RGB8UI = 36221;
   int GL_RGBA32I = 36226;
   int GL_RGB32I = 36227;
   int GL_RGBA16I = 36232;
   int GL_RGB16I = 36233;
   int GL_RGBA8I = 36238;
   int GL_RGB8I = 36239;
   int GL_RED_INTEGER = 36244;
   int GL_RGB_INTEGER = 36248;
   int GL_RGBA_INTEGER = 36249;
   int GL_SAMPLER_2D_ARRAY = 36289;
   int GL_SAMPLER_2D_ARRAY_SHADOW = 36292;
   int GL_SAMPLER_CUBE_SHADOW = 36293;
   int GL_UNSIGNED_INT_VEC2 = 36294;
   int GL_UNSIGNED_INT_VEC3 = 36295;
   int GL_UNSIGNED_INT_VEC4 = 36296;
   int GL_INT_SAMPLER_2D = 36298;
   int GL_INT_SAMPLER_3D = 36299;
   int GL_INT_SAMPLER_CUBE = 36300;
   int GL_INT_SAMPLER_2D_ARRAY = 36303;
   int GL_UNSIGNED_INT_SAMPLER_2D = 36306;
   int GL_UNSIGNED_INT_SAMPLER_3D = 36307;
   int GL_UNSIGNED_INT_SAMPLER_CUBE = 36308;
   int GL_UNSIGNED_INT_SAMPLER_2D_ARRAY = 36311;
   int GL_BUFFER_ACCESS_FLAGS = 37151;
   int GL_BUFFER_MAP_LENGTH = 37152;
   int GL_BUFFER_MAP_OFFSET = 37153;
   int GL_DEPTH_COMPONENT32F = 36012;
   int GL_DEPTH32F_STENCIL8 = 36013;
   int GL_FLOAT_32_UNSIGNED_INT_24_8_REV = 36269;
   int GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING = 33296;
   int GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE = 33297;
   int GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE = 33298;
   int GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE = 33299;
   int GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE = 33300;
   int GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE = 33301;
   int GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE = 33302;
   int GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE = 33303;
   int GL_FRAMEBUFFER_DEFAULT = 33304;
   int GL_FRAMEBUFFER_UNDEFINED = 33305;
   int GL_DEPTH_STENCIL_ATTACHMENT = 33306;
   int GL_DEPTH_STENCIL = 34041;
   int GL_UNSIGNED_INT_24_8 = 34042;
   int GL_DEPTH24_STENCIL8 = 35056;
   int GL_UNSIGNED_NORMALIZED = 35863;
   int GL_DRAW_FRAMEBUFFER_BINDING = 36006;
   int GL_READ_FRAMEBUFFER = 36008;
   int GL_DRAW_FRAMEBUFFER = 36009;
   int GL_READ_FRAMEBUFFER_BINDING = 36010;
   int GL_RENDERBUFFER_SAMPLES = 36011;
   int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER = 36052;
   int GL_MAX_COLOR_ATTACHMENTS = 36063;
   int GL_COLOR_ATTACHMENT1 = 36065;
   int GL_COLOR_ATTACHMENT2 = 36066;
   int GL_COLOR_ATTACHMENT3 = 36067;
   int GL_COLOR_ATTACHMENT4 = 36068;
   int GL_COLOR_ATTACHMENT5 = 36069;
   int GL_COLOR_ATTACHMENT6 = 36070;
   int GL_COLOR_ATTACHMENT7 = 36071;
   int GL_COLOR_ATTACHMENT8 = 36072;
   int GL_COLOR_ATTACHMENT9 = 36073;
   int GL_COLOR_ATTACHMENT10 = 36074;
   int GL_COLOR_ATTACHMENT11 = 36075;
   int GL_COLOR_ATTACHMENT12 = 36076;
   int GL_COLOR_ATTACHMENT13 = 36077;
   int GL_COLOR_ATTACHMENT14 = 36078;
   int GL_COLOR_ATTACHMENT15 = 36079;
   int GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE = 36182;
   int GL_MAX_SAMPLES = 36183;
   int GL_HALF_FLOAT = 5131;
   int GL_MAP_READ_BIT = 1;
   int GL_MAP_WRITE_BIT = 2;
   int GL_MAP_INVALIDATE_RANGE_BIT = 4;
   int GL_MAP_INVALIDATE_BUFFER_BIT = 8;
   int GL_MAP_FLUSH_EXPLICIT_BIT = 16;
   int GL_MAP_UNSYNCHRONIZED_BIT = 32;
   int GL_RG = 33319;
   int GL_RG_INTEGER = 33320;
   int GL_R8 = 33321;
   int GL_RG8 = 33323;
   int GL_R16F = 33325;
   int GL_R32F = 33326;
   int GL_RG16F = 33327;
   int GL_RG32F = 33328;
   int GL_R8I = 33329;
   int GL_R8UI = 33330;
   int GL_R16I = 33331;
   int GL_R16UI = 33332;
   int GL_R32I = 33333;
   int GL_R32UI = 33334;
   int GL_RG8I = 33335;
   int GL_RG8UI = 33336;
   int GL_RG16I = 33337;
   int GL_RG16UI = 33338;
   int GL_RG32I = 33339;
   int GL_RG32UI = 33340;
   int GL_VERTEX_ARRAY_BINDING = 34229;
   int GL_R8_SNORM = 36756;
   int GL_RG8_SNORM = 36757;
   int GL_RGB8_SNORM = 36758;
   int GL_RGBA8_SNORM = 36759;
   int GL_SIGNED_NORMALIZED = 36764;
   int GL_PRIMITIVE_RESTART_FIXED_INDEX = 36201;
   int GL_COPY_READ_BUFFER = 36662;
   int GL_COPY_WRITE_BUFFER = 36663;
   int GL_COPY_READ_BUFFER_BINDING = 36662;
   int GL_COPY_WRITE_BUFFER_BINDING = 36663;
   int GL_UNIFORM_BUFFER = 35345;
   int GL_UNIFORM_BUFFER_BINDING = 35368;
   int GL_UNIFORM_BUFFER_START = 35369;
   int GL_UNIFORM_BUFFER_SIZE = 35370;
   int GL_MAX_VERTEX_UNIFORM_BLOCKS = 35371;
   int GL_MAX_FRAGMENT_UNIFORM_BLOCKS = 35373;
   int GL_MAX_COMBINED_UNIFORM_BLOCKS = 35374;
   int GL_MAX_UNIFORM_BUFFER_BINDINGS = 35375;
   int GL_MAX_UNIFORM_BLOCK_SIZE = 35376;
   int GL_MAX_COMBINED_VERTEX_UNIFORM_COMPONENTS = 35377;
   int GL_MAX_COMBINED_FRAGMENT_UNIFORM_COMPONENTS = 35379;
   int GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT = 35380;
   int GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH = 35381;
   int GL_ACTIVE_UNIFORM_BLOCKS = 35382;
   int GL_UNIFORM_TYPE = 35383;
   int GL_UNIFORM_SIZE = 35384;
   int GL_UNIFORM_NAME_LENGTH = 35385;
   int GL_UNIFORM_BLOCK_INDEX = 35386;
   int GL_UNIFORM_OFFSET = 35387;
   int GL_UNIFORM_ARRAY_STRIDE = 35388;
   int GL_UNIFORM_MATRIX_STRIDE = 35389;
   int GL_UNIFORM_IS_ROW_MAJOR = 35390;
   int GL_UNIFORM_BLOCK_BINDING = 35391;
   int GL_UNIFORM_BLOCK_DATA_SIZE = 35392;
   int GL_UNIFORM_BLOCK_NAME_LENGTH = 35393;
   int GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS = 35394;
   int GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES = 35395;
   int GL_UNIFORM_BLOCK_REFERENCED_BY_VERTEX_SHADER = 35396;
   int GL_UNIFORM_BLOCK_REFERENCED_BY_FRAGMENT_SHADER = 35398;
   int GL_INVALID_INDEX = -1;
   int GL_MAX_VERTEX_OUTPUT_COMPONENTS = 37154;
   int GL_MAX_FRAGMENT_INPUT_COMPONENTS = 37157;
   int GL_MAX_SERVER_WAIT_TIMEOUT = 37137;
   int GL_OBJECT_TYPE = 37138;
   int GL_SYNC_CONDITION = 37139;
   int GL_SYNC_STATUS = 37140;
   int GL_SYNC_FLAGS = 37141;
   int GL_SYNC_FENCE = 37142;
   int GL_SYNC_GPU_COMMANDS_COMPLETE = 37143;
   int GL_UNSIGNALED = 37144;
   int GL_SIGNALED = 37145;
   int GL_ALREADY_SIGNALED = 37146;
   int GL_TIMEOUT_EXPIRED = 37147;
   int GL_CONDITION_SATISFIED = 37148;
   int GL_WAIT_FAILED = 37149;
   int GL_SYNC_FLUSH_COMMANDS_BIT = 1;
   long GL_TIMEOUT_IGNORED = -1L;
   int GL_VERTEX_ATTRIB_ARRAY_DIVISOR = 35070;
   int GL_ANY_SAMPLES_PASSED = 35887;
   int GL_ANY_SAMPLES_PASSED_CONSERVATIVE = 36202;
   int GL_SAMPLER_BINDING = 35097;
   int GL_RGB10_A2UI = 36975;
   int GL_TEXTURE_SWIZZLE_R = 36418;
   int GL_TEXTURE_SWIZZLE_G = 36419;
   int GL_TEXTURE_SWIZZLE_B = 36420;
   int GL_TEXTURE_SWIZZLE_A = 36421;
   int GL_GREEN = 6404;
   int GL_BLUE = 6405;
   int GL_INT_2_10_10_10_REV = 36255;
   int GL_TRANSFORM_FEEDBACK = 36386;
   int GL_TRANSFORM_FEEDBACK_PAUSED = 36387;
   int GL_TRANSFORM_FEEDBACK_ACTIVE = 36388;
   int GL_TRANSFORM_FEEDBACK_BINDING = 36389;
   int GL_PROGRAM_BINARY_RETRIEVABLE_HINT = 33367;
   int GL_PROGRAM_BINARY_LENGTH = 34625;
   int GL_NUM_PROGRAM_BINARY_FORMATS = 34814;
   int GL_PROGRAM_BINARY_FORMATS = 34815;
   int GL_COMPRESSED_R11_EAC = 37488;
   int GL_COMPRESSED_SIGNED_R11_EAC = 37489;
   int GL_COMPRESSED_RG11_EAC = 37490;
   int GL_COMPRESSED_SIGNED_RG11_EAC = 37491;
   int GL_COMPRESSED_RGB8_ETC2 = 37492;
   int GL_COMPRESSED_SRGB8_ETC2 = 37493;
   int GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2 = 37494;
   int GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2 = 37495;
   int GL_COMPRESSED_RGBA8_ETC2_EAC = 37496;
   int GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC = 37497;
   int GL_TEXTURE_IMMUTABLE_FORMAT = 37167;
   int GL_MAX_ELEMENT_INDEX = 36203;
   int GL_NUM_SAMPLE_COUNTS = 37760;
   int GL_TEXTURE_IMMUTABLE_LEVELS = 33503;

   void glReadBuffer(int var1);

   void glDrawRangeElements(int var1, int var2, int var3, int var4, int var5, Buffer var6);

   void glDrawRangeElements(int var1, int var2, int var3, int var4, int var5, int var6);

   void glTexImage3D(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, Buffer var10);

   void glTexImage3D(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10);

   void glTexSubImage3D(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10, Buffer var11);

   void glTexSubImage3D(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10, int var11);

   void glCopyTexSubImage3D(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9);

   void glGenQueries(int var1, int[] var2, int var3);

   void glGenQueries(int var1, IntBuffer var2);

   void glDeleteQueries(int var1, int[] var2, int var3);

   void glDeleteQueries(int var1, IntBuffer var2);

   boolean glIsQuery(int var1);

   void glBeginQuery(int var1, int var2);

   void glEndQuery(int var1);

   void glGetQueryiv(int var1, int var2, IntBuffer var3);

   void glGetQueryObjectuiv(int var1, int var2, IntBuffer var3);

   boolean glUnmapBuffer(int var1);

   Buffer glGetBufferPointerv(int var1, int var2);

   void glDrawBuffers(int var1, IntBuffer var2);

   void glUniformMatrix2x3fv(int var1, int var2, boolean var3, FloatBuffer var4);

   void glUniformMatrix3x2fv(int var1, int var2, boolean var3, FloatBuffer var4);

   void glUniformMatrix2x4fv(int var1, int var2, boolean var3, FloatBuffer var4);

   void glUniformMatrix4x2fv(int var1, int var2, boolean var3, FloatBuffer var4);

   void glUniformMatrix3x4fv(int var1, int var2, boolean var3, FloatBuffer var4);

   void glUniformMatrix4x3fv(int var1, int var2, boolean var3, FloatBuffer var4);

   void glBlitFramebuffer(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10);

   void glRenderbufferStorageMultisample(int var1, int var2, int var3, int var4, int var5);

   void glFramebufferTextureLayer(int var1, int var2, int var3, int var4, int var5);

   void glFlushMappedBufferRange(int var1, int var2, int var3);

   void glBindVertexArray(int var1);

   void glDeleteVertexArrays(int var1, int[] var2, int var3);

   void glDeleteVertexArrays(int var1, IntBuffer var2);

   void glGenVertexArrays(int var1, int[] var2, int var3);

   void glGenVertexArrays(int var1, IntBuffer var2);

   boolean glIsVertexArray(int var1);

   void glBeginTransformFeedback(int var1);

   void glEndTransformFeedback();

   void glBindBufferRange(int var1, int var2, int var3, int var4, int var5);

   void glBindBufferBase(int var1, int var2, int var3);

   void glTransformFeedbackVaryings(int var1, String[] var2, int var3);

   void glVertexAttribIPointer(int var1, int var2, int var3, int var4, int var5);

   void glGetVertexAttribIiv(int var1, int var2, IntBuffer var3);

   void glGetVertexAttribIuiv(int var1, int var2, IntBuffer var3);

   void glVertexAttribI4i(int var1, int var2, int var3, int var4, int var5);

   void glVertexAttribI4ui(int var1, int var2, int var3, int var4, int var5);

   void glGetUniformuiv(int var1, int var2, IntBuffer var3);

   int glGetFragDataLocation(int var1, String var2);

   void glUniform1uiv(int var1, int var2, IntBuffer var3);

   void glUniform3uiv(int var1, int var2, IntBuffer var3);

   void glUniform4uiv(int var1, int var2, IntBuffer var3);

   void glClearBufferiv(int var1, int var2, IntBuffer var3);

   void glClearBufferuiv(int var1, int var2, IntBuffer var3);

   void glClearBufferfv(int var1, int var2, FloatBuffer var3);

   void glClearBufferfi(int var1, int var2, float var3, int var4);

   String glGetStringi(int var1, int var2);

   void glCopyBufferSubData(int var1, int var2, int var3, int var4, int var5);

   void glGetUniformIndices(int var1, String[] var2, IntBuffer var3);

   void glGetActiveUniformsiv(int var1, int var2, IntBuffer var3, int var4, IntBuffer var5);

   int glGetUniformBlockIndex(int var1, String var2);

   void glGetActiveUniformBlockiv(int var1, int var2, int var3, IntBuffer var4);

   void glGetActiveUniformBlockName(int var1, int var2, Buffer var3, Buffer var4);

   String glGetActiveUniformBlockName(int var1, int var2);

   void glUniformBlockBinding(int var1, int var2, int var3);

   void glDrawArraysInstanced(int var1, int var2, int var3, int var4);

   void glDrawElementsInstanced(int var1, int var2, int var3, int var4, int var5);

   void glGetInteger64v(int var1, LongBuffer var2);

   void glGetBufferParameteri64v(int var1, int var2, LongBuffer var3);

   void glGenSamplers(int var1, int[] var2, int var3);

   void glGenSamplers(int var1, IntBuffer var2);

   void glDeleteSamplers(int var1, int[] var2, int var3);

   void glDeleteSamplers(int var1, IntBuffer var2);

   boolean glIsSampler(int var1);

   void glBindSampler(int var1, int var2);

   void glSamplerParameteri(int var1, int var2, int var3);

   void glSamplerParameteriv(int var1, int var2, IntBuffer var3);

   void glSamplerParameterf(int var1, int var2, float var3);

   void glSamplerParameterfv(int var1, int var2, FloatBuffer var3);

   void glGetSamplerParameteriv(int var1, int var2, IntBuffer var3);

   void glGetSamplerParameterfv(int var1, int var2, FloatBuffer var3);

   void glVertexAttribDivisor(int var1, int var2);

   void glBindTransformFeedback(int var1, int var2);

   void glDeleteTransformFeedbacks(int var1, int[] var2, int var3);

   void glDeleteTransformFeedbacks(int var1, IntBuffer var2);

   void glGenTransformFeedbacks(int var1, int[] var2, int var3);

   void glGenTransformFeedbacks(int var1, IntBuffer var2);

   boolean glIsTransformFeedback(int var1);

   void glPauseTransformFeedback();

   void glResumeTransformFeedback();

   void glProgramParameteri(int var1, int var2, int var3);

   void glInvalidateFramebuffer(int var1, int var2, IntBuffer var3);

   void glInvalidateSubFramebuffer(int var1, int var2, IntBuffer var3, int var4, int var5, int var6, int var7);

   @Deprecated
   @Override
   void glVertexAttribPointer(int var1, int var2, int var3, boolean var4, int var5, Buffer var6);
}
