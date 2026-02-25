package com.badlogic.gdx.tools.flame;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

public class CustomCardLayout extends CardLayout {
   @Override
   public Dimension preferredLayoutSize(Container parent) {
      Component component = this.getCurrentCard(parent);
      return component != null ? component.getPreferredSize() : super.preferredLayoutSize(parent);
   }

   public <K> K getCurrentCard(Container container) {
      Component[] c = container.getComponents();
      int i = 0;

      for (int j = c.length; i < j; i++) {
         if (c[i].isVisible()) {
            return (K)c[i];
         }
      }

      return null;
   }
}
