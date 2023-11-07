#!/bin/zsh -eu

version="${1:-}"

if [ -z "$version" ]; then
  echo "usage: $0 <version>" >&2
  exit 1
fi

ant -DRELEASE=$version -Ddebug=true
mkdir -p ~/Development/charles/repo/com/xk72/violetlib/VAqua/$version
mkdir -p ~/.m2/repository/com/xk72/violetlib/VAqua/$version
cp dist/VAqua.jar ~/Development/charles/repo/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
cp dist/VAqua.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
if [ -d ~/Development/charles/app/build/macos/assembly/openjdk/Charles.app/Contents/Java ]; then
  cp dist/VAqua.jar ~/Development/charles/app/build/macos/assembly/openjdk/Charles.app/Contents/Java/VAqua-$version.jar
fi
(cd ../src && jar cf ../release/VAqua-$version-sources.jar .)
mv VAqua-$version-sources.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/
codesign out/jni/*.dylib out/uber-classes/*.dylib -s "Developer ID Application: XK72 Limited"
rsync -a out/jni/*.dylib out/uber-classes/*.dylib ~/Development/charles/app/assembly/src/main/assembly/macos/lib
