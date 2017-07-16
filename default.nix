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
  josmSrc = fetchsvnSafe rec {
    name = "josm-r${rev}";
    url = "https://josm.openstreetmap.de/svn/trunk";
    sha256 = "0xnql92j09wdgvdbbalhlwhcbdi958j12yq699p903ri13ckpa9y";
    rev="12275";
    ignoreExternals=true;
  };
  byteBuddy = pkgs.fetchMavenArtifact {
    groupId = "net.bytebuddy";
    artifactId = "byte-buddy";
    version = "1.6.14";
    sha256 = "1hb1yij0qxf5h55smqhz1s5jv05z5paa36q2bahpiqjiqsrmhxwi";
  };
  byteBuddyAgent = (pkgs.fetchMavenArtifact {
    groupId = "net.bytebuddy";
    artifactId = "byte-buddy-agent";
    version = "1.6.14";
    sha256 = "0crswy5fhipklpkz6fkr9gfypk5b4ql9j99xlksfyglwh3ba4hf1";
  }).overrideAttrs (oldAttrs: oldAttrs // { propagatedBuildInputs = [byteBuddy]; });
  objenesis = pkgs.fetchMavenArtifact {
    groupId = "org.objenesis";
    artifactId = "objenesis";
    version = "2.5";
    sha256 = "0z3bvp231mlqkpmn43mvi44w1yjjdhmm9jlzp05x67nkn3hjhcr9";
  };
  mockitoCore = (pkgs.fetchMavenArtifact {
    groupId = "org.mockito";
    artifactId = "mockito-core";
    version = "2.8.47";
    sha256 = "1833yn6v91g1ih8bq02r0fmr31bmz1i50zzkgilhfp65j0vzx5n4";
  }).overrideAttrs (oldAttrs: oldAttrs // { propagatedBuildInputs = [ byteBuddyAgent objenesis ]; });
in {
  markseenDevEnv = pkgs.stdenv.mkDerivation {
    name = "josm-markseen-dev-env";
    buildInputs = [
      pkgs.ant
      pkgs.openjdk
      pkgs.rlwrap  # just try using jdb without it
      pkgs.man
      mockitoCore
    ];

    JOSM_PLUGINS_SRC_DIR=josmPluginsSrc;
    JOSM_SRC_DIR=josmSrc;
  };
}
