package org.apache.logging.log4j.core;

public class Version {
   public static void main(final String[] args) {
      System.out.println(getProductString());
   }

   public static String getProductString() {
      Package pkg = Version.class.getPackage();
      return pkg == null ? "Apache Log4j" : String.format("%s %s", pkg.getSpecificationTitle(), pkg.getSpecificationVersion());
   }
}
