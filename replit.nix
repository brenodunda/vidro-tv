{ pkgs }: {
  deps = [
    pkgs.python311
    pkgs.jdk17_headless   # javac, jar, keytool, jarsigner
    pkgs.aapt             # aapt2 (Android Asset Packaging Tool 2)
  ];
}
