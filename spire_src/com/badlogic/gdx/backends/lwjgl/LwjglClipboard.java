package com.badlogic.gdx.backends.lwjgl;

import com.badlogic.gdx.utils.Clipboard;
import java.awt.Toolkit;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class LwjglClipboard implements Clipboard, ClipboardOwner {
   @Override
   public String getContents() {
      String result = "";

      try {
         java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
         Transferable contents = clipboard.getContents(null);
         boolean hasTransferableText = contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor);
         if (hasTransferableText) {
            try {
               result = (String)contents.getTransferData(DataFlavor.stringFlavor);
            } catch (Exception var6) {
            }
         }
      } catch (Exception var7) {
      }

      return result;
   }

   @Override
   public void setContents(String content) {
      try {
         StringSelection stringSelection = new StringSelection(content);
         java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
         clipboard.setContents(stringSelection, this);
      } catch (Exception var4) {
      }
   }

   @Override
   public void lostOwnership(java.awt.datatransfer.Clipboard arg0, Transferable arg1) {
   }
}
