{
  pkgs ? import <nixpkgs> {}
}:
let
  fetchsvnSafe = fetchsvnArgs: pkgs.stdenv.lib.overrideDerivation (pkgs.fetchsvn fetchsvnArgs) (
    self: { buildInputs = self.buildInputs ++ [ pkgs.glibcLocales ]; LC_ALL = "en_US.UTF-8"; }
  );
  josmPluginsSrc = fetchsvnSafe rec {
    name = "josm-plugins-r${rev}";
    url = "https://svn.openstreetmap.org/applications/editors/josm";
    sha256 = "10z8lflkkq8yi9302flh0zzvx2fgp7crcl5rj2p5689zhbl3m7sq";
    rev="34621";
    ignoreExternals=true;
  };
  josm = import ./nix/josm.nix {
    fetchsvn = fetchsvnSafe;
    antBuild = pkgs.releaseTools.antBuild;
    stdenv = pkgs.stdenv;
    version = "15322";
  };
  jmockit = pkgs.fetchMavenArtifact {
    groupId = "org.jmockit";
    artifactId = "jmockit";
    version = "1.34";
    sha256 = "0spabz9n1s8r5jia0nmk0kb5by66f0m0qjxv9w10vi8g4q5kphyy";
  };
  guava = pkgs.fetchMavenArtifact {
    groupId = "com.google.guava";
    artifactId = "guava";
    sha256 = "14ag7fa462vddprizb3jv61qyimmg4j3mf2d58lrv3qfbypddr3k";
    version = "28.0-jre";
  };
in {
  markseenDevEnv = pkgs.stdenv.mkDerivation {
    name = "josm-markseen-dev-env";
    buildInputs = [
      pkgs.glibcLocales
      pkgs.ant
      pkgs.openjdk
      pkgs.rlwrap  # just try using jdb without it
      pkgs.man
      pkgs.inkscape  # for building icons
    ] ++ pkgs.stdenv.lib.optional ((builtins.compareVersions josm.version "14052") == -1) [
      jmockit
    ] ++ pkgs.stdenv.lib.optional ((builtins.compareVersions josm.version "14760") == 1) [
      guava
    ];

    JOSM_PLUGINS_SRC_DIR=josmPluginsSrc;
    JOSM_SRC_DIR=josm.src;
    JOSM_TEST_BUILD_DIR=josm.testBuildDir;
    JOSM_JAR="${josm}/share/java/josm-custom.jar";
    INKSCAPE_PATH="${pkgs.inkscape}/bin/inkscape";
  };
}
