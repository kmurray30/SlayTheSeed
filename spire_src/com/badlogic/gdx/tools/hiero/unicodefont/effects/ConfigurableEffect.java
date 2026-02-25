package com.badlogic.gdx.tools.hiero.unicodefont.effects;

import java.util.List;

public interface ConfigurableEffect extends Effect {
   List getValues();

   void setValues(List var1);

   public interface Value {
      String getName();

      void setString(String var1);

      String getString();

      Object getObject();

      void showDialog();
   }
}
