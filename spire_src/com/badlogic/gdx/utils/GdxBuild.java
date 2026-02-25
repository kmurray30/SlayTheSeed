package com.badlogic.gdx.utils;

import com.badlogic.gdx.jnigen.AntScriptGenerator;
import com.badlogic.gdx.jnigen.BuildConfig;
import com.badlogic.gdx.jnigen.BuildTarget;
import com.badlogic.gdx.jnigen.NativeCodeGenerator;
import com.badlogic.gdx.jnigen.BuildTarget.TargetOs;

public class GdxBuild {
   public static void main(String[] args) throws Exception {
      String JNI_DIR = "jni";
      String LIBS_DIR = "libs";
      new NativeCodeGenerator().generate("src", "bin", JNI_DIR, new String[]{"**/*"}, null);
      String[] excludeCpp = new String[]{"android/**", "iosgl/**"};
      BuildTarget win32home = BuildTarget.newDefaultTarget(TargetOs.Windows, false);
      win32home.compilerPrefix = "";
      win32home.buildFileName = "build-windows32home.xml";
      win32home.excludeFromMasterBuildFile = true;
      win32home.cppExcludes = excludeCpp;
      BuildTarget win32 = BuildTarget.newDefaultTarget(TargetOs.Windows, false);
      win32.cppExcludes = excludeCpp;
      BuildTarget win64 = BuildTarget.newDefaultTarget(TargetOs.Windows, true);
      win64.cppExcludes = excludeCpp;
      BuildTarget lin32 = BuildTarget.newDefaultTarget(TargetOs.Linux, false);
      lin32.cppExcludes = excludeCpp;
      BuildTarget lin64 = BuildTarget.newDefaultTarget(TargetOs.Linux, true);
      lin64.cppExcludes = excludeCpp;
      BuildTarget android = BuildTarget.newDefaultTarget(TargetOs.Android, false);
      android.linkerFlags = android.linkerFlags + " -lGLESv2 -llog";
      android.cppExcludes = new String[]{"iosgl/**"};
      BuildTarget mac = BuildTarget.newDefaultTarget(TargetOs.MacOsX, false);
      mac.cppExcludes = excludeCpp;
      BuildTarget mac64 = BuildTarget.newDefaultTarget(TargetOs.MacOsX, true);
      mac64.cppExcludes = excludeCpp;
      BuildTarget ios = BuildTarget.newDefaultTarget(TargetOs.IOS, false);
      ios.cppExcludes = new String[]{"android/**"};
      ios.headerDirs = new String[]{"iosgl"};
      new AntScriptGenerator()
         .generate(
            new BuildConfig("gdx", "../target/native", LIBS_DIR, JNI_DIR), new BuildTarget[]{mac, mac64, win32home, win32, win64, lin32, lin64, android, ios}
         );
   }
}
