#!/bin/sh
# Recompiles the Icon Composer file (Liquid Glass) into Assets.car + AppIcon.icns.
# Needs Xcode 26+. Outputs are committed so CI runners with older Xcode still get the icon.
set -e
cd "$(dirname "$0")"
out=$(mktemp -d)
xcrun actool AppIcon.icon --compile "$out" --platform macosx --minimum-deployment-target 12.0 \
  --app-icon AppIcon --output-partial-info-plist "$out/partial.plist" >/dev/null
cp "$out/Assets.car" "$out/AppIcon.icns" .
echo "Updated Assets.car and AppIcon.icns"
