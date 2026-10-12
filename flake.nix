{
  inputs.devshells.url = "github:Tofaa2/devshells";

  outputs = {
    self,
    devshells,
    ...
  }: {
    devShells.x86_64-linux.default = let
      system = "x86_64-linux";
      pkgs = devshells.inputs.nixpkgs.legacyPackages.${system};
      base = devshells.devShells.${system}.zig;
      jdk = pkgs.jdk25;
      runtimeLibs = with pkgs; [
        vulkan-loader
        mesa
        libGL
        libclang
        libxkbcommon
        wayland
        libdecor
        libx11
        libxrandr
        libxcursor
        libxext
        libxi
        libxinerama
        libxscrnsaver
        libxtst
        libpulseaudio
        pipewire
        alsa-lib
        openal
        udev
        flite
        stdenv.cc.cc.lib
      ];
    in
      pkgs.mkShell {
        inputsFrom = [base];
        packages = with pkgs; [
          jdk
          (gradle_9.override {java = jdk;})
          shaderc
          vulkan-volk
          vulkan-memory-allocator
          vulkan-headers
          vulkan-loader
          vulkan-validation-layers
          vulkan-tools
          vulkan-tools-lunarg
          shader-slang
          directx-shader-compiler
          ktx-tools
          git-lfs
          gcc
          cmake
          pkg-config
          llvm
          libclang
          renderdoc
        ];
        JAVA_HOME = "${jdk}";
        VK_LAYER_PATH = "${pkgs.vulkan-validation-layers}/share/vulkan/explicit_layer.d";
        LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath runtimeLibs + ":/run/opengl-driver/lib";
      };
  };
}
