{
  description = "lac";

  inputs = {
    nixpkgs = { url = "nixpkgs/nixos-unstable"; };
    examples = {
      url = "github:lorenzleutgeb/lac-examples";
      flake = false;
    };
    gradle2nix = {
      url = "github:tadfisher/gradle2nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, gradle2nix, examples }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        #config.allowUnfree = true;
      };
      jdk = pkgs.graalvm11-ce;
      z3 = pkgs.z3.override {
        inherit jdk;
        javaBindings = true;
      };
      #g2n = import gradle2nix {};
      gradleGen = pkgs.gradleGen.override { java = jdk; };
      gradle = gradleGen.gradle_latest;
      solvers = with pkgs; [
        alt-ergo
        cvc4
        yices
        opensmt
      ];
      lacEnv = pkgs.buildEnv {
        name = "lac-env";
        paths = [
          gradle
          jdk
          z3
          pkgs.dot2tex
          pkgs.graphviz
          gradle2nix.packages."${system}".gradle2nix
        ];
      };
    in rec {
      nixosConfigurations.lac = nixpkgs.lib.nixosSystem {
        inherit system;
        modules = [
          #(nixpkgs + "/nixos/modules/installer/scan/not-detected.nix")
          "${nixpkgs}/nixos/modules/virtualisation/virtualbox-image.nix"
          "${nixpkgs}/nixos/modules/virtualisation/virtualbox-guest.nix"
          ({ pkgs, ... }: {
            virtualbox = {
              vmName = "lac";
              params = { usb = "off"; };
            };
            environment.systemPackages = [ self.defaultPackage."${system}" ];
            networking.hostName = "lac";
            users.users.lac = {
              password = "lac";
              isNormalUser = true;
              description = "autogenerated user";
              extraGroups = [ "wheel" ];
              uid = 1000;
              shell = pkgs.bash;
            };
            security.sudo.wheelNeedsPassword = false;
            services = {
              openssh.enable = true;
              xserver = {
                enable = true;
                autorun = true;
                desktopManager = {
                  xterm.enable = false;
                  xfce.enable = true;
                };
                displayManager.defaultSession = "xfce";
              };
            };
          })
        ];
      };
      devShell."${system}" = with pkgs;
        mkShell {
          buildInputs = [ lacEnv ];
          shellHook = ''
            export Z3_JAVA="${z3.java}"

            rm -f src/main/resources/jni/linux/amd64/libz3java.so
            mkdir -p src/main/resources/jni/linux/amd64
            ln -s ${z3.lib}/lib/libz3java.so src/main/resources/jni/linux/amd64/libz3java.so

            export GRAAL_HOME="${jdk}"
            export JAVA_HOME="$GRAAL_HOME"
            export GRADLE_HOME="${gradle}"

            $JAVA_HOME/bin/java -version
            $GRAAL_HOME/bin/gu --version
            $GRAAL_HOME/bin/gu list
            $GRADLE_HOME/bin/gradle -version

            z3 --version
            dot2tex --version

            if [ "$GITHUB_ACTIONS" = "true" ]
            then
              echo "$PATH" >> $GITHUB_PATH
              env | grep -E "^((GRAAL|GRADLE|JAVA)_HOME|LD_LIBRARY_PATH|Z3_JAVA)=" | tee -a $GITHUB_ENV
            fi
          '';
        };
      defaultPackage."${system}" = packages."${system}".lac;

      packages."${system}" = rec {
        lac-ova = nixosConfigurations.lac.config.system.build.virtualBoxOVA;

        lac = (pkgs.callPackage ./gradle-env.nix { inherit gradleGen; }) {
          envSpec = ./gradle-env.json;

          src =
            /* pkgs.nix-gitignore.gitignoreSourcePure [
                 "*"
                 "!src/"
                 "!*gradle.kts"
                 "!gradle.properties"
                 "!lac.*"
                 "!version.sh"
               ]
            */
            ./.;

          nativeBuildInputs =
            [ pkgs.bash pkgs.git jdk z3 examples pkgs.glibcLocales ];
          Z3_JAVA = "${z3.java}";
          LAC_HOME = "${examples}";
          LANG = "en_US.UTF-8";
          LOCALE_ARCHIVE = "${pkgs.glibcLocales}/lib/locale/locale-archive";
          LD_LIBRARY_PATH = "${z3.lib}";
          gradleFlags = [ "nativeImage" "-x" "test" ];
          configurePhase = ''
            locale
            patchShebangs version.sh
            mkdir -p src/main/resources/jni/linux/amd64
            cp -v ${z3.lib}/lib/libz3java.so src/main/resources/jni/linux/amd64
          '';
          installPhase = ''
            mkdir $out
            mv lac.jsh $out
            mv lac.properties $out
            echo "xyz.leutgeb.lorenz.lac.module.Loader.defaultHome=$out/examples" >> $out/lac.properties
            cp -Rv ${examples} $out/examples
            mv src/test/resources/tactics $out
            mkdir -p $out/bin
            ls -la build/native-image
            mv build/native-image/lac $out/bin/lac
          '';
        };

        lac-shell-oci = pkgs.dockerTools.buildLayeredImage {
          name = "lac-shell";
          tag = "latest";
          contents = [ pkgs.bash pkgs.coreutils packages."${system}".lac ];
          config = {
            Entrypoint = [ "${pkgs.bash}/bin/bash" ];
            #Env = [ "PATH=${lacEnv}/bin" ];
          };
        };

        lac-oci = pkgs.dockerTools.buildLayeredImage {
          name = "lac";
          tag = "latest";
          config.Entrypoint = packages."${system}".lac + "/lac";
        };
      };
    };
}
