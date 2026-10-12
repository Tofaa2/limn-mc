#!/bin/sh
set -eu
gamescope=$(nix build --no-link --print-out-paths --impure --expr '(builtins.getFlake (toString ./.)).inputs.devshells.inputs.nixpkgs.legacyPackages.x86_64-linux.gamescope')
exec env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11 "$gamescope/bin/gamescope" --backend headless -W 1280 -H 720 -- ./gradlew ${LIMN_GRADLE_ARGS:-} "${@:-runClientGameTest}"
