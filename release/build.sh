#!/bin/zsh -eu

ant -DRELEASE=$version -Ddebug=true
cp dist/VAqua.jar ~/Development/charles/repo/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
cp dist/VAqua.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
(cd ../src && jar cf ../release/VAqua-$version-sources.jar .)
cp VAqua-$version-sources.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/
codesign out/jni/*.dylib out/uber-classes/*.dylib -s "Developer ID Application: XK72 Limited"
rsync -a out/jni/*.dylib out/uber-classes/*.dylib ~/Development/charles/app/assembly/src/main/assembly/macos/lib
