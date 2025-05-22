#!/bin/zsh -eu

version="${1:-}"

if [ -z "$version" ]; then
  echo "usage: $0 <version>" >&2
  exit 1
fi

ant clean
ant -DRELEASE=$version -Ddebug=true

# Remove the dylibs from the jar, as I want them separately
(
  rm -rf dist/extract
  mkdir dist/extract
  cd dist/extract
  jar xf ../VAqua.jar
  rm -rf *.dylib.dSYM
  mv *.dylib ..
  jar -cf ../VAqua.jar .
)

mkdir -p ~/Development/charles/repo/com/xk72/violetlib/VAqua/$version
mkdir -p ~/.m2/repository/com/xk72/violetlib/VAqua/$version
cp dist/VAqua.jar ~/Development/charles/repo/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
cp dist/VAqua.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
if [ -d ~/Development/charles/app/build/macos/assembly/openjdk/Charles.app/Contents/Java ]; then
  cp dist/VAqua.jar ~/Development/charles/app/build/macos/assembly/openjdk/Charles.app/Contents/Java/VAqua-$version.jar
fi
(cd ../src && jar cf ../release/VAqua-$version-sources.jar .)
mv VAqua-$version-sources.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/

# Sign and copy across the .dylibs
codesign dist/*.dylib -s "Developer ID Application: XK72 Limited"
rsync -a dist/*.dylib ~/Development/charles/app/assembly/src/main/assembly/macos/lib
