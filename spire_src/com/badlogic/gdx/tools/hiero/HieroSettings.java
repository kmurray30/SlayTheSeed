package com.badlogic.gdx.tools.hiero;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.tools.hiero.unicodefont.UnicodeFont;
import com.badlogic.gdx.tools.hiero.unicodefont.effects.ConfigurableEffect;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HieroSettings {
   private static final String RENDER_TYPE = "render_type";
   private String fontName = "Arial";
   private int fontSize = 12;
   private boolean bold;
   private boolean italic;
   private boolean mono;
   private float gamma;
   private int paddingTop;
   private int paddingLeft;
   private int paddingBottom;
   private int paddingRight;
   private int paddingAdvanceX;
   private int paddingAdvanceY;
   private int glyphPageWidth = 512;
   private int glyphPageHeight = 512;
   private String glyphText = "";
   private final List effects = new ArrayList();
   private boolean nativeRendering;
   private boolean font2Active = false;
   private String font2File = "";
   private int renderType = UnicodeFont.RenderType.FreeType.ordinal();

   public HieroSettings() {
   }

   public HieroSettings(String hieroFileRef) {
      try {
         BufferedReader reader = new BufferedReader(new InputStreamReader(Gdx.files.absolute(hieroFileRef).read(), "UTF-8"));

         while (true) {
            String line = reader.readLine();
            if (line == null) {
               reader.close();
               return;
            }

            line = line.trim();
            if (line.length() != 0) {
               String[] pieces = line.split("=", 2);
               String name = pieces[0].trim();
               String value = pieces[1];
               if (name.equals("font.name")) {
                  this.fontName = value;
               } else if (name.equals("font.size")) {
                  this.fontSize = Integer.parseInt(value);
               } else if (name.equals("font.gamma")) {
                  this.gamma = Float.parseFloat(value);
               } else if (name.equals("font.mono")) {
                  this.mono = Boolean.parseBoolean(value);
               } else if (name.equals("font.size")) {
                  this.fontSize = Integer.parseInt(value);
               } else if (name.equals("font.bold")) {
                  this.bold = Boolean.parseBoolean(value);
               } else if (name.equals("font.italic")) {
                  this.italic = Boolean.parseBoolean(value);
               } else if (name.equals("font2.file")) {
                  this.font2File = value;
               } else if (name.equals("font2.use")) {
                  this.font2Active = Boolean.parseBoolean(value);
               } else if (name.equals("pad.top")) {
                  this.paddingTop = Integer.parseInt(value);
               } else if (name.equals("pad.right")) {
                  this.paddingRight = Integer.parseInt(value);
               } else if (name.equals("pad.bottom")) {
                  this.paddingBottom = Integer.parseInt(value);
               } else if (name.equals("pad.left")) {
                  this.paddingLeft = Integer.parseInt(value);
               } else if (name.equals("pad.advance.x")) {
                  this.paddingAdvanceX = Integer.parseInt(value);
               } else if (name.equals("pad.advance.y")) {
                  this.paddingAdvanceY = Integer.parseInt(value);
               } else if (name.equals("glyph.page.width")) {
                  this.glyphPageWidth = Integer.parseInt(value);
               } else if (name.equals("glyph.page.height")) {
                  this.glyphPageHeight = Integer.parseInt(value);
               } else if (name.equals("glyph.native.rendering")) {
                  this.nativeRendering = Boolean.parseBoolean(value);
               } else if (name.equals("glyph.text")) {
                  this.glyphText = value;
               } else if (name.equals("render_type")) {
                  this.renderType = Integer.parseInt(value);
               } else if (name.equals("effect.class")) {
                  try {
                     this.effects.add(Class.forName(value).newInstance());
                  } catch (Throwable var11) {
                     throw new GdxRuntimeException("Unable to create effect instance: " + value, var11);
                  }
               } else if (name.startsWith("effect.")) {
                  name = name.substring(7);
                  ConfigurableEffect effect = (ConfigurableEffect)this.effects.get(this.effects.size() - 1);
                  List values = effect.getValues();
                  Iterator iter = values.iterator();

                  while (true) {
                     if (iter.hasNext()) {
                        ConfigurableEffect.Value effectValue = (ConfigurableEffect.Value)iter.next();
                        if (!effectValue.getName().equals(name)) {
                           continue;
                        }

                        effectValue.setString(value);
                     }

                     effect.setValues(values);
                     break;
                  }
               }
            }
         }
      } catch (Throwable var12) {
         throw new GdxRuntimeException("Unable to load Hiero font file: " + hieroFileRef, var12);
      }
   }

   public int getPaddingTop() {
      return this.paddingTop;
   }

   public void setPaddingTop(int paddingTop) {
      this.paddingTop = paddingTop;
   }

   public int getPaddingLeft() {
      return this.paddingLeft;
   }

   public void setPaddingLeft(int paddingLeft) {
      this.paddingLeft = paddingLeft;
   }

   public int getPaddingBottom() {
      return this.paddingBottom;
   }

   public void setPaddingBottom(int paddingBottom) {
      this.paddingBottom = paddingBottom;
   }

   public int getPaddingRight() {
      return this.paddingRight;
   }

   public void setPaddingRight(int paddingRight) {
      this.paddingRight = paddingRight;
   }

   public int getPaddingAdvanceX() {
      return this.paddingAdvanceX;
   }

   public void setPaddingAdvanceX(int paddingAdvanceX) {
      this.paddingAdvanceX = paddingAdvanceX;
   }

   public int getPaddingAdvanceY() {
      return this.paddingAdvanceY;
   }

   public void setPaddingAdvanceY(int paddingAdvanceY) {
      this.paddingAdvanceY = paddingAdvanceY;
   }

   public int getGlyphPageWidth() {
      return this.glyphPageWidth;
   }

   public void setGlyphPageWidth(int glyphPageWidth) {
      this.glyphPageWidth = glyphPageWidth;
   }

   public int getGlyphPageHeight() {
      return this.glyphPageHeight;
   }

   public void setGlyphPageHeight(int glyphPageHeight) {
      this.glyphPageHeight = glyphPageHeight;
   }

   public String getFontName() {
      return this.fontName;
   }

   public void setFontName(String fontName) {
      this.fontName = fontName;
   }

   public int getFontSize() {
      return this.fontSize;
   }

   public void setFontSize(int fontSize) {
      this.fontSize = fontSize;
   }

   public boolean isBold() {
      return this.bold;
   }

   public void setBold(boolean bold) {
      this.bold = bold;
   }

   public boolean isItalic() {
      return this.italic;
   }

   public void setItalic(boolean italic) {
      this.italic = italic;
   }

   public List getEffects() {
      return this.effects;
   }

   public boolean getNativeRendering() {
      return this.nativeRendering;
   }

   public void setNativeRendering(boolean nativeRendering) {
      this.nativeRendering = nativeRendering;
   }

   public String getGlyphText() {
      return this.glyphText.replace("\\n", "\n");
   }

   public void setGlyphText(String text) {
      this.glyphText = text.replace("\n", "\\n");
   }

   public String getFont2File() {
      return this.font2File;
   }

   public void setFont2File(String filename) {
      this.font2File = filename;
   }

   public boolean isFont2Active() {
      return this.font2Active;
   }

   public void setFont2Active(boolean active) {
      this.font2Active = active;
   }

   public boolean isMono() {
      return this.mono;
   }

   public void setMono(boolean mono) {
      this.mono = mono;
   }

   public float getGamma() {
      return this.gamma;
   }

   public void setGamma(float gamma) {
      this.gamma = gamma;
   }

   public void save(File file) throws IOException {
      PrintStream out = new PrintStream(file, "UTF-8");
      out.println("font.name=" + this.fontName);
      out.println("font.size=" + this.fontSize);
      out.println("font.bold=" + this.bold);
      out.println("font.italic=" + this.italic);
      out.println("font.gamma=" + this.gamma);
      out.println("font.mono=" + this.mono);
      out.println();
      out.println("font2.file=" + this.font2File);
      out.println("font2.use=" + this.font2Active);
      out.println();
      out.println("pad.top=" + this.paddingTop);
      out.println("pad.right=" + this.paddingRight);
      out.println("pad.bottom=" + this.paddingBottom);
      out.println("pad.left=" + this.paddingLeft);
      out.println("pad.advance.x=" + this.paddingAdvanceX);
      out.println("pad.advance.y=" + this.paddingAdvanceY);
      out.println();
      out.println("glyph.native.rendering=" + this.nativeRendering);
      out.println("glyph.page.width=" + this.glyphPageWidth);
      out.println("glyph.page.height=" + this.glyphPageHeight);
      out.println("glyph.text=" + this.glyphText);
      out.println();
      out.println("render_type=" + this.renderType);
      out.println();

      for (ConfigurableEffect effect : this.effects) {
         out.println("effect.class=" + effect.getClass().getName());

         for (ConfigurableEffect.Value value : effect.getValues()) {
            out.println("effect." + value.getName() + "=" + value.getString());
         }

         out.println();
      }

      out.close();
   }

   public void setRenderType(int renderType) {
      this.renderType = renderType;
   }

   public int getRenderType() {
      return this.renderType;
   }
}
