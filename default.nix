{
  pkgs ? import <nixpkgs> {}
}:
{
  markseenDevEnv = pkgs.stdenv.mkDerivation {
    name = "josm-markseen-dev-env";
    buildInputs = [
      pkgs.ant
      pkgs.openjdk
    ];

    shellHook = let
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
    in ''
      export JOSM_PLUGINS_SRC_DIR="${josmPluginsSrc}"
      export JOSM_SRC_DIR="${josmSrc}"
    '';
  };
}
