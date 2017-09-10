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
    sha256 = "18l5fb8hx5pag99rq3v77qfmh0al46lk2diwqmmvjp8i2rgd7603";
    rev="32680";
    ignoreExternals=true;
  };
  josm = import ./nix/josm.nix { fetchsvn = fetchsvnSafe; antBuild = pkgs.releaseTools.antBuild; stdenv = pkgs.stdenv; };
  jmockit = pkgs.fetchMavenArtifact {
    groupId = "org.jmockit";
    artifactId = "jmockit";
    version = "1.34";
    sha256 = "0spabz9n1s8r5jia0nmk0kb5by66f0m0qjxv9w10vi8g4q5kphyy";
  };
in {
  markseenDevEnv = pkgs.stdenv.mkDerivation {
    name = "josm-markseen-dev-env";
    buildInputs = [
      pkgs.ant
      pkgs.openjdk
      pkgs.rlwrap  # just try using jdb without it
      pkgs.man
      jmockit
      pkgs.inkscape  # for building icons
    ];

    JOSM_PLUGINS_SRC_DIR=josmPluginsSrc;
    JOSM_SRC_DIR=josm.src;
    JOSM_TEST_BUILD_DIR=josm.testBuildDir;
    JOSM_JAR="${josm}/share/java/josm-custom.jar";
    INKSCAPE_PATH="${pkgs.inkscape}/bin/inkscape";
  };
}
