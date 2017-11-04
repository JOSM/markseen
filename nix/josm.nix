{ fetchsvn, stdenv, antBuild }:

antBuild rec {
  name = "josm-${version}";
  version = "13053";

  src = fetchsvn rec {
    name = "josm-r${rev}";
    url = "https://josm.openstreetmap.de/svn/trunk";
    sha256 = builtins.getAttr rev {
      "13053" = "12b1ai0gb9akm2jb95dlnmasgm20zlq4782y0skpsp05bgr02lf1";  # 2017-10-29 release
      "13170" = "0xyyxzf0l3izfx8hj51m5gc4xmp3f4yq3k5n2c9w7dli6fvggk8p";  # 2017-11-26 release
    };
    rev=version;
  };

  patches = [] ++ stdenv.lib.optional (
    (builtins.compareVersions version "12715") == -1 && (builtins.compareVersions version "12328") != -1
  ) (
    if
      (builtins.compareVersions version "12620") == -1
    then
      ./josm-build-without-javafx-pre-r12620.patch
    else
      ./josm-build-without-javafx-r12620-onwards.patch
  );

  antTargets = [
    "dist"
    "test-compile"
  ];
  antProperties = [
    { name = "version"; value = version; }
  ] ++ stdenv.lib.optional stdenv.isLinux { name = "noJavaFX"; value = "true"; };
  jars = [
    "dist/josm-custom.jar"
  ];

  outputs = [ "out" "testBuildDir" ];

  postInstall = ''
    cp -r test/build $testBuildDir
  '';

  meta = with stdenv.lib; {
    description = "An extensible editor for ​OpenStreetMap";
    homepage = https://josm.openstreetmap.de/;
    license = licenses.gpl2Plus;
    platforms = platforms.all;
  };
}
