{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        testScript = pkgs.writeShellScriptBin "test" ''
          ${pkgs.gradle}/bin/gradle test
        '';
        specScript = pkgs.writeShellScriptBin "spec" ''
          ${pkgs.quint}/bin/quint run ./spec/raft.qnt --max-steps=50 --mbt --invariants foobar
        '';

        # kotlinLspPatch = pkgs.writeShellScriptBin "kotlin-language-server" ''
        #   JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication" \
        #   exec ${pkgs.kotlin-language-server}/bin/kotlin-language-server
        # '';
      in
      {
        apps.test = {
          type = "app";
          buildInputs = with pkgs;[
            pkgs.gradle
            pkgs.openjdk21
            pkgs.kotlin
          ];
          program = "${testScript}/bin/test";
        };

        apps.spec = {
          type = "app";
          buildInputs = with pkgs;[
            pkgs.quint
            pkgs.nodejs
          ];
          program = "${specScript}/bin/spec";
        };

        devShells.default = pkgs.mkShell {
          buildInputs = [
            # Quint Spec Deps
            pkgs.quint
            pkgs.nodejs

            # Dev Deps
            pkgs.gradle
            pkgs.openjdk21
            pkgs.kotlin
            pkgs.ktlint
            # kotlinLspPatch
          ];

          shellHook = ''
            export GRADLE_USER_HOME="$PWD/.gradle"
            export PATH="$PATH:$(pwd)/node_modules/.bin"
            npm install @informalsystems/quint-language-server -D
          '';
        };
      });
}

