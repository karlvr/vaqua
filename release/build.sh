#!/bin/zsh -eu

ant -DRELEASE=$version -Ddebug=true
cp dist/VAqua.jar ~/Development/charles/repo/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
cp dist/VAqua.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/VAqua-$version.jar
(cd ../src && jar cf ../release/VAqua-$version-sources.jar .)
cp VAqua-$version-sources.jar ~/.m2/repository/com/xk72/violetlib/VAqua/$version/
