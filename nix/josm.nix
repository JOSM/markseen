{ fetchsvn, stdenv, antBuild, version }:

antBuild rec {
  inherit version;
  name = "josm-${version}";

  src = fetchsvn rec {
    name = "josm-r${rev}";
    url = "https://josm.openstreetmap.de/svn/trunk";
    sha256 = builtins.getAttr rev {
      "13053" = "12b1ai0gb9akm2jb95dlnmasgm20zlq4782y0skpsp05bgr02lf1";  # 2017-10-29 release
      "13170" = "0xyyxzf0l3izfx8hj51m5gc4xmp3f4yq3k5n2c9w7dli6fvggk8p";  # 2017-11-26 release
      "13576" = "0xihpx2nrbcf32yyvscs8rzn1wwhg248p4vcsaw3lnsybjnx9mz6";  # 2018-03-26 release
      "14178" = "16y7s064ksncvp0xk1bgiqs7cbzpd3bbvpyn66pm65rw3sqsrm35";  # 2018-08-22 release
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
