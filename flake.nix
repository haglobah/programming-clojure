{
  description = "A clj-nix flake";

  inputs = {
    nixpkgs.url = "https://flakehub.com/f/DeterminateSystems/nixpkgs-weekly/*.tar.gz";
    devshell.url = "github:numtide/devshell";
    devshell.inputs.nixpkgs.follows = "nixpkgs";
    clj-nix.url = "github:jlesquembre/clj-nix";
    clj-nix.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = inputs@{ flake-parts, clj-nix, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      imports = [
        inputs.devshell.flakeModule
        # To import a flake module
        # 1. Add foo to inputs
        # 2. Add foo as a parameter to the outputs function
        # 3. Add here: foo.flakeModule

      ];
      systems = [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" "x86_64-darwin" ];
      perSystem = { config, self', inputs', pkgs, system, ... }: {
        # Per-system attributes can be defined here. The self' and inputs'
        # module parameters provide easy access to attributes of the same
        # system.

        # Equivalent to  inputs'.nixpkgs.legacyPackages.hello;
        packages = {
          default = clj-nix.lib.mkCljApp {
            inherit pkgs;

            modules = [
              # Option list:
              # https://jlesquembre.github.io/clj-nix/options/
              {
                projectSrc = ./.;
                name = "me.beathagenlocher.com/programming-clojure";
                main-ns = "hello.core";

                nativeImage.enable = true;

                # customJdk.enable = true;
              }
            ];
          };
        };
        
        devshells.default = {
          env = [
              # { name = "MY_ENV_VAR"; value = "SOTRUE"; }
          ];
          packages = [
            pkgs.clojure
            pkgs.zulu
          ];
          commands = [
            { name = "lock"; command = "nix run github:jlesquembre/clj-nix#deps-lock"; help = "Create the deps-lock.json file"; }
          ];
        };
      };
      flake = {
        # The usual flake attributes can be defined here, including system-
        # agnostic ones like nixosModule and system-enumerating ones, although
        # those are more easily expressed in perSystem.

      };
    };
}