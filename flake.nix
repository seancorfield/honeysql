{
  description = "honeysql";

  inputs = {
    devenv.url =
      "github:cachix/devenv/9ba9e3b908a12ddc6c43f88c52f2bf3c1d1e82c1";
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:NixOS/nixpkgs";
  };

  outputs = { self, flake-utils, nixpkgs, devenv }@inputs:
    flake-utils.lib.eachDefaultSystem (system:
      let pkgs = import nixpkgs { inherit system; };
      in {
        devShells.default = devenv.lib.mkShell {
          inherit inputs pkgs;
          modules = [
            ({ pkgs, ... }: {
              packages = with pkgs; [ babashka clj-kondo clojure clojure-lsp ];
            })
          ];
        };
      });
}
